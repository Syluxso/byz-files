package com.nyberg.files.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON payload for {@code byz.search.index}. See events-service {@code docs/EVENTS.md}.
 */
public record SearchIndexEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        UUID organizationId,
        UUID tenantId,
        UUID userId,
        String documentId,
        String title,
        String content,
        String source,
        String path,
        List<String> tags
) {
    public static final String TYPE_INDEX = "search.index";
    public static final String TYPE_DELETE = "search.delete";

    public static SearchIndexEvent index(
            UUID organizationId,
            UUID tenantId,
            UUID userId,
            UUID documentId,
            String title,
            String content,
            String path
    ) {
        return new SearchIndexEvent(
                UUID.randomUUID(),
                TYPE_INDEX,
                Instant.now(),
                organizationId,
                tenantId,
                userId,
                documentId.toString(),
                title,
                content,
                "file-service",
                path,
                null
        );
    }

    public static SearchIndexEvent delete(
            UUID organizationId,
            UUID tenantId,
            UUID userId,
            UUID documentId
    ) {
        return new SearchIndexEvent(
                UUID.randomUUID(),
                TYPE_DELETE,
                Instant.now(),
                organizationId,
                tenantId,
                userId,
                documentId.toString(),
                null,
                null,
                "file-service",
                null,
                null
        );
    }
}
