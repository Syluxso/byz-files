package com.nyberg.files.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    List<StoredFile> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, String status);
    Optional<StoredFile> findByIdAndOrganizationIdAndStatus(UUID id, UUID organizationId, String status);
}
