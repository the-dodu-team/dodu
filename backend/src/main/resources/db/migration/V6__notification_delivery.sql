-- V6: notification delivery ledger for idempotent in-app and push dispatch.
create table notification_delivery (
    id                   uuid primary key default gen_random_uuid(),
    participant_id       uuid         not null references participant(id),
    promise_id           uuid         not null references promise(id),
    schedule_version     integer      not null,
    notification_type    varchar(32)  not null,
    channel              varchar(16)  not null,
    dispatch_key         varchar(255) not null,
    lifecycle_status     varchar(32)  not null,
    scheduled_at         timestamptz  not null,
    provider_message_id  varchar(255),
    sent_at              timestamptz,
    read_at              timestamptz,
    failure_code         varchar(64),
    created_at           timestamptz  not null default now(),
    constraint uq_notification_delivery_dispatch unique (dispatch_key, channel)
);

create index ix_notification_delivery_participant
    on notification_delivery (participant_id, channel, created_at desc);
create index ix_notification_delivery_due
    on notification_delivery (lifecycle_status, scheduled_at);
