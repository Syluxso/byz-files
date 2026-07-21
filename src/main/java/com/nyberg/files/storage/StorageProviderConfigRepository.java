package com.nyberg.files.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StorageProviderConfigRepository extends JpaRepository<StorageProviderConfig, UUID> {
    Optional<StorageProviderConfig> findByOrganizationId(UUID organizationId);
    Optional<StorageProviderConfig> findByOrganizationIdAndActiveTrue(UUID organizationId);
}
