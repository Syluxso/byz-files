package com.nyberg.files.file;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        UUID organizationId,
        UUID tenantId,
        UUID uploadedBy,
        String name,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String status,
        String visibility,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileResponse from(StoredFile f) {
        return new FileResponse(
                f.getId(),
                f.getOrganizationId(),
                f.getTenantId(),
                f.getUploadedBy(),
                f.getName(),
                f.getContentType(),
                f.getSizeBytes(),
                f.getChecksumSha256(),
                f.getStatus(),
                f.getVisibility(),
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }
}
