package com.nyberg.files.file;

import java.time.Instant;
import java.util.UUID;

/** Admin view of a stored file — includes storageKey for debugging MinIO. */
public record AdminFileResponse(
        UUID id,
        UUID organizationId,
        UUID tenantId,
        UUID uploadedBy,
        String name,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String storageKey,
        String status,
        String visibility,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
    public static AdminFileResponse from(StoredFile f) {
        return new AdminFileResponse(
                f.getId(),
                f.getOrganizationId(),
                f.getTenantId(),
                f.getUploadedBy(),
                f.getName(),
                f.getContentType(),
                f.getSizeBytes(),
                f.getChecksumSha256(),
                f.getStorageKey(),
                f.getStatus(),
                f.getVisibility(),
                f.getCreatedAt(),
                f.getUpdatedAt(),
                f.getDeletedAt()
        );
    }
}
