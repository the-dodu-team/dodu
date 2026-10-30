-- V8: application-owned retry state for transient Push provider failures.
alter table notification_delivery
    add column attempt_count integer not null default 0,
    add column next_attempt_at timestamptz;

create index ix_notification_delivery_push_retry
    on notification_delivery (channel, lifecycle_status, next_attempt_at)
    where channel = 'PUSH' and lifecycle_status = 'FAILED';
