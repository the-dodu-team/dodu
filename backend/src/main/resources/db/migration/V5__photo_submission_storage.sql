-- V5: photo submission metadata and the attempt-to-asset relationship.
-- Raw photo bytes stay in an external/private object store; PostgreSQL stores metadata only.

create table photo_asset (
    id             uuid primary key default gen_random_uuid(),
    storage_key    varchar(128) not null,
    content_type   varchar(128) not null,
    byte_size      bigint not null,
    sha256_hash    varchar(64) not null,
    received_at    timestamptz not null,
    constraint uq_photo_asset_storage_key unique (storage_key),
    constraint ck_photo_asset_byte_size_positive check (byte_size > 0),
    constraint ck_photo_asset_sha256_hex check (sha256_hash ~ '^[0-9a-f]{64}$')
);

alter table auth_attempt
    add column photo_asset_id uuid references photo_asset (id);

create unique index ux_auth_attempt_photo_asset
    on auth_attempt (photo_asset_id)
    where photo_asset_id is not null;
