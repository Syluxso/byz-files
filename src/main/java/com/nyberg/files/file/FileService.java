package com.nyberg.files.file;

import com.nyberg.files.events.FileCreatedApplicationEvent;
import com.nyberg.files.events.FileLifecycleEvent;
import com.nyberg.files.storage.StorageObject;
import com.nyberg.files.storage.StorageProviderConfigService;
import com.nyberg.files.storage.StorageProviderConfigService.ResolvedStorage;
import com.nyberg.files.tenant.OrganizationContext;
import com.nyberg.files.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public List<FileResponse> list() {
        UUID orgId = requireOrg();
        return repository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, "active").stream()
                .map(FileResponse::from)
                .toList();
    }

    /** Platform admin: list across orgs. status blank/"all" = any status. */
    @Transactional(readOnly = true)
    public Page<AdminFileResponse> adminList(UUID organizationId, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        String normalized = status == null || status.isBlank() || "all".equalsIgnoreCase(status.trim())
                ? null
                : status.trim().toLowerCase();

        Page<StoredFile> rows;
        if (organizationId != null && normalized != null) {
            rows = repository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, normalized, pageable);
        } else if (organizationId != null) {
            rows = repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
        } else if (normalized != null) {
            rows = repository.findByStatusOrderByCreatedAtDesc(normalized, pageable);
        } else {
            rows = repository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return rows.map(AdminFileResponse::from);
    }

    @Transactional(readOnly = true)
    public FileResponse getMeta(UUID id) {
        return FileResponse.from(requireActiveFile(id, requireOrg()));
    }

    @Transactional(readOnly = true)
    public AdminFileResponse adminGetMeta(UUID id) {
        StoredFile file = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        return AdminFileResponse.from(file);
    }

    @Transactional
    public FileResponse upload(MultipartFile file) {
        return uploadForOrg(requireOrg(), file, TenantContext.get());
    }

    @Transactional
    public AdminFileResponse adminUpload(UUID organizationId, MultipartFile file) {
        if (organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId is required");
        }
        return AdminFileResponse.from(uploadEntity(organizationId, file, null));
    }

    /** Caller must close the returned StorageObject. */
    @Transactional(readOnly = true)
    public OpenFile openContent(UUID id) {
        UUID orgId = requireOrg();
        StoredFile meta = requireActiveFile(id, orgId);
        return openStored(meta);
    }

    /** Caller must close the returned StorageObject. */
    @Transactional(readOnly = true)
    public OpenFile adminOpenContent(UUID id) {
        StoredFile meta = repository.findByIdAndStatus(id, "active")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        return openStored(meta);
    }

    @Transactional
    public void delete(UUID id) {
        deleteStored(requireActiveFile(id, requireOrg()));
    }

    @Transactional
    public void adminDelete(UUID id) {
        StoredFile meta = repository.findByIdAndStatus(id, "active")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        deleteStored(meta);
    }

    @Transactional
    public AdminFileResponse adminRename(UUID id, String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        String cleaned = name.trim().replaceAll("[\\\\/]+", "_");
        if (cleaned.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 255);
        }
        StoredFile meta = repository.findByIdAndStatus(id, "active")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        meta.setName(cleaned);
        return AdminFileResponse.from(repository.save(meta));
    }

    private FileResponse uploadForOrg(UUID orgId, MultipartFile file, UUID tenantId) {
        return FileResponse.from(uploadEntity(orgId, file, tenantId));
    }

    private StoredFile uploadEntity(UUID orgId, MultipartFile file, UUID tenantId) {
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
                    .tenantId(tenantId)
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
            publishFileCreated(saved);
            return saved;
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

    private OpenFile openStored(StoredFile meta) {
        ResolvedStorage storage = storageConfigs.resolveForOrg(meta.getOrganizationId());
        StorageObject object = storage.provider().get(storage.credentials(), meta.getStorageKey());
        return new OpenFile(meta, object);
    }

    private void deleteStored(StoredFile meta) {
        ResolvedStorage storage = storageConfigs.resolveForOrg(meta.getOrganizationId());
        try {
            storage.provider().delete(storage.credentials(), meta.getStorageKey());
        } catch (Exception e) {
            log.warn("storage delete failed for file {}: {}", meta.getId(), e.toString());
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

    /** Published as Spring event; Kafka send runs AFTER_COMMIT so rollbacks do not emit. */
    private void publishFileCreated(StoredFile file) {
        applicationEventPublisher.publishEvent(new FileCreatedApplicationEvent(
                this,
                FileLifecycleEvent.fileCreated(
                        file.getOrganizationId(),
                        file.getTenantId(),
                        file.getId(),
                        file.getUploadedBy(),
                        file.getName(),
                        file.getContentType(),
                        file.getSizeBytes(),
                        file.getChecksumSha256(),
                        file.getStorageKey()
                )
        ));
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
