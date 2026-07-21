package com.nyberg.files.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record StorageProviderConfigRequest(
        @NotNull UUID organizationId,
        @NotBlank String provider,
        @NotNull Map<String, String> credentials
) {}
