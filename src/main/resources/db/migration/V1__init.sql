CREATE TABLE files.storage_provider_configs (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID         NOT NULL UNIQUE,
    provider              VARCHAR(50)  NOT NULL,
    credentials_encrypted TEXT         NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE files.files (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID         NOT NULL,
    tenant_id         UUID,
    uploaded_by       UUID,
    name              TEXT         NOT NULL,
    content_type      VARCHAR(255),
    size_bytes        BIGINT       NOT NULL,
    checksum_sha256   VARCHAR(64),
    storage_key       TEXT         NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'active',
    visibility        VARCHAR(30)  NOT NULL DEFAULT 'org',
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_files_org_created ON files.files (organization_id, created_at DESC);
CREATE INDEX idx_files_org_status ON files.files (organization_id, status);
CREATE UNIQUE INDEX uq_files_org_storage_key ON files.files (organization_id, storage_key);
