package com.nyberg.files.file;

import com.nyberg.files.storage.StorageObject;
import com.nyberg.files.storage.StorageProviderConfigService;
import com.nyberg.files.storage.StorageProviderConfigService.ResolvedStorage;
import com.nyberg.files.tenant.OrganizationContext;
import com.nyberg.files.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final StoredFileRepository repository;
    private final StorageProviderConfigService storageConfigs;

    @Transactional(readOnly = true)
    public List<FileResponse> list() {
        UUID orgId = requireOrg();
        return repository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, "active").stream()
                .map(FileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FileResponse getMeta(UUID id) {
        return FileResponse.from(requireActiveFile(id, requireOrg()));
    }

    @Transactional
    public FileResponse upload(MultipartFile file) {
        UUID orgId = requireOrg();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }

        String originalName = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                ? file.getOriginalFilename()
                : "upload.bin";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        long size = file.getSize();

        UUID fileId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        String storageKey = orgId + "/" + today.getYear() + "/" + today.getMonthValue()
                + "/" + fileId + "/" + sanitizeName(originalName);

        ResolvedStorage storage = storageConfigs.resolveForOrg(orgId);
        MessageDigest digest = sha256();
        boolean objectStored = false;
        try (InputStream in = file.getInputStream();
             DigestInputStream din = new DigestInputStream(in, digest)) {
            storage.provider().put(storage.credentials(), storageKey, din, size, contentType);
            objectStored = true;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read upload stream");
        }

        String checksum = HexFormat.of().formatHex(digest.digest());

        try {
            StoredFile saved = repository.save(StoredFile.builder()
                    .id(fileId)
                    .organizationId(orgId)
                    .tenantId(TenantContext.get())
                    .uploadedBy(currentUserId())
                    .name(originalName)
                    .contentType(contentType)
                    .sizeBytes(size)
                    .checksumSha256(checksum)
                    .storageKey(storageKey)
                    .status("active")
                    .visibility("org")
                    .build());

            log.info("file uploaded id={} org={} key={} size={} sha256={}",
                    saved.getId(), orgId, storageKey, size, checksum);
            return FileResponse.from(saved);
        } catch (RuntimeException e) {
            if (objectStored) {
                try {
                    storage.provider().delete(storage.credentials(), storageKey);
                } catch (Exception cleanup) {
                    log.warn("orphan cleanup failed for key={}: {}", storageKey, cleanup.toString());
                }
            }
            throw e;
        }
    }

    /** Caller must close the returned StorageObject. */
    @Transactional(readOnly = true)
    public OpenFile openContent(UUID id) {
        UUID orgId = requireOrg();
        StoredFile meta = requireActiveFile(id, orgId);
        ResolvedStorage storage = storageConfigs.resolveForOrg(orgId);
        StorageObject object = storage.provider().get(storage.credentials(), meta.getStorageKey());
        return new OpenFile(meta, object);
    }

    @Transactional
    public void delete(UUID id) {
        UUID orgId = requireOrg();
        StoredFile meta = requireActiveFile(id, orgId);
        ResolvedStorage storage = storageConfigs.resolveForOrg(orgId);
        try {
            storage.provider().delete(storage.credentials(), meta.getStorageKey());
        } catch (Exception e) {
            log.warn("storage delete failed for file {}: {}", id, e.toString());
        }
        meta.setStatus("deleted");
        meta.setDeletedAt(Instant.now());
        repository.save(meta);
    }

    public record OpenFile(StoredFile meta, StorageObject object) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            object.close();
        }
    }

    private StoredFile requireActiveFile(UUID id, UUID orgId) {
        return repository.findByIdAndOrganizationIdAndStatus(id, orgId, "active")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    private static UUID requireOrg() {
        UUID orgId = OrganizationContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization ID required in token");
        }
        return orgId;
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID id) {
            return id;
        }
        return null;
    }

    static String sanitizeName(String name) {
        String cleaned = name.replaceAll("[\\\\/]+", "_").replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        if (cleaned.isBlank() || !cleaned.matches(".*[a-zA-Z0-9].*")) {
            return "file";
        }
        return cleaned.length() > 180 ? cleaned.substring(0, 180) : cleaned;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
