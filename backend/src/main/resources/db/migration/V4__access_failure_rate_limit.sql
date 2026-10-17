-- V4: shared participant-code failure counters for temporary IP blocking.
-- The source IP is operational metadata only; raw access codes are never stored.
create table access_failure_rate_limit (
    ip_address        varchar(64) primary key,
    window_started_at timestamptz not null,
    failed_attempts  integer not null,
    blocked_until    timestamptz,
    updated_at       timestamptz not null,
    constraint ck_access_failure_attempts_nonnegative check (failed_attempts >= 0)
);
