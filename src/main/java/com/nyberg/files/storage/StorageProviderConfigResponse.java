package com.nyberg.files.storage;

import java.time.Instant;
import java.util.UUID;

public record StorageProviderConfigResponse(
        UUID id,
        UUID organizationId,
        String provider,
        String endpointHint,
        String bucketHint,
        String keyHint,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
