create table promise_creation_request (
    id                  uuid primary key default gen_random_uuid(),
    participant_id      uuid         not null references participant (id),
    idempotency_key     varchar(128) not null,
    request_fingerprint varchar(64)  not null,
    promise_id          uuid references promise (id),
    created_at          timestamptz  not null default now(),
    constraint uq_promise_creation_request_key unique (participant_id, idempotency_key),
    constraint uq_promise_creation_request_promise unique (promise_id)
);

create index ix_promise_creation_request_participant
    on promise_creation_request (participant_id, created_at);
