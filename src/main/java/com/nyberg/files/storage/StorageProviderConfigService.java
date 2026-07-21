package com.nyberg.files.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyberg.files.crypto.CredentialEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageProviderConfigService {

    private final StorageProviderConfigRepository repo;
    private final CredentialEncryptionService encryption;
    private final ObjectMapper objectMapper;
    private final List<StorageProvider> providers;

    public List<StorageProviderConfigResponse> listAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public StorageProviderConfigResponse upsert(StorageProviderConfigRequest req) {
        String providerId = req.provider().trim().toLowerCase();
        StorageProvider provider = resolveProvider(providerId);

        StorageProviderConfig existing = repo.findByOrganizationId(req.organizationId()).orElse(null);
        Map<String, String> merged = new HashMap<>(req.credentials() != null ? req.credentials() : Map.of());

        if (existing != null) {
            Map<String, String> stored = decryptCredentials(existing.getCredentialsEncrypted());
            for (String secretKey : List.of("accessKey", "secretKey", "apiKey")) {
                if (blank(merged.get(secretKey)) && !blank(stored.get(secretKey))) {
                    merged.put(secretKey, stored.get(secretKey));
                }
            }
        }

        requireCreds(merged, "endpoint", "bucket", "accessKey", "secretKey");

        try {
            provider.ensureBucket(merged);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach storage: " + e.getMessage());
        }

        String encrypted = encrypt(merged);
        if (existing != null) {
            existing.setProvider(providerId);
            existing.setCredentialsEncrypted(encrypted);
            existing.setActive(true);
            return toResponse(repo.save(existing));
        }

        StorageProviderConfig cfg = StorageProviderConfig.builder()
                .organizationId(req.organizationId())
                .provider(providerId)
                .credentialsEncrypted(encrypted)
                .build();
        return toResponse(repo.save(cfg));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage config not found");
        }
        repo.deleteById(id);
    }

    public ResolvedStorage resolveForOrg(UUID organizationId) {
        StorageProviderConfig cfg = repo.findByOrganizationIdAndActiveTrue(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No active storage config for organization"));
        StorageProvider provider = resolveProvider(cfg.getProvider());
        return new ResolvedStorage(provider, decryptCredentials(cfg.getCredentialsEncrypted()));
    }

    public record ResolvedStorage(StorageProvider provider, Map<String, String> credentials) {}

    private StorageProvider resolveProvider(String providerId) {
        return providers.stream()
                .filter(p -> p.providerId().equalsIgnoreCase(providerId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported storage provider: " + providerId));
    }

    private StorageProviderConfigResponse toResponse(StorageProviderConfig cfg) {
        Map<String, String> creds = decryptCredentials(cfg.getCredentialsEncrypted());
        String accessKey = creds.getOrDefault("accessKey", "");
        String hint = accessKey.length() >= 4 ? "..." + accessKey.substring(accessKey.length() - 4) : "****";
        return new StorageProviderConfigResponse(
                cfg.getId(),
                cfg.getOrganizationId(),
                cfg.getProvider(),
                creds.getOrDefault("endpoint", ""),
                creds.getOrDefault("bucket", ""),
                hint,
                cfg.isActive(),
                cfg.getCreatedAt(),
                cfg.getUpdatedAt()
        );
    }

    private String encrypt(Map<String, String> credentials) {
        try {
            return encryption.encrypt(objectMapper.writeValueAsString(credentials));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt credentials", e);
        }
    }

    private Map<String, String> decryptCredentials(String encrypted) {
        try {
            String json = encryption.decrypt(encrypted);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static void requireCreds(Map<String, String> creds, String... keys) {
        for (String key : keys) {
            if (blank(creds.get(key))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing credential: " + key);
            }
        }
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }
}
