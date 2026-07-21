package com.nyberg.files.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    List<StoredFile> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, String status);
    Optional<StoredFile> findByIdAndOrganizationIdAndStatus(UUID id, UUID organizationId, String status);
    Optional<StoredFile> findByIdAndStatus(UUID id, String status);

    Page<StoredFile> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<StoredFile> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<StoredFile> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
    Page<StoredFile> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, String status, Pageable pageable);
}
