package com.nyberg.files.events;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON payload for {@code byz.files.file}. See events-service {@code docs/EVENTS.md}.
 */
public record FileLifecycleEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        UUID organizationId,
        UUID tenantId,
        UUID fileId,
        UUID uploadedBy,
        String name,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String storageKey
) {
    public static final String TYPE_FILE_CREATED = "file.created";

    public static FileLifecycleEvent fileCreated(
            UUID organizationId,
            UUID tenantId,
            UUID fileId,
            UUID uploadedBy,
            String name,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            String storageKey
    ) {
        return new FileLifecycleEvent(
                UUID.randomUUID(),
                TYPE_FILE_CREATED,
                Instant.now(),
                organizationId,
                tenantId,
                fileId,
                uploadedBy,
                name,
                contentType,
                sizeBytes,
                checksumSha256,
                storageKey
        );
    }
}
