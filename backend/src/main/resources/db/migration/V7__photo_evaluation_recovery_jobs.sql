-- V7: durable retry queue for accepted photo evaluation work.
create table photo_evaluation_job (
    id               uuid primary key default gen_random_uuid(),
    auth_attempt_id  uuid not null references auth_attempt(id),
    promise_id       uuid not null references promise(id),
    lifecycle_status varchar(32) not null,
    attempt_count    integer not null default 0,
    next_attempt_at  timestamptz not null,
    lease_until      timestamptz,
    last_error_code  varchar(64),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint uq_photo_evaluation_job_attempt unique (auth_attempt_id),
    constraint ck_photo_evaluation_job_attempt_count check (attempt_count >= 0)
);

create index ix_photo_evaluation_job_due
    on photo_evaluation_job (lifecycle_status, next_attempt_at);

insert into photo_evaluation_job(auth_attempt_id, promise_id, lifecycle_status, next_attempt_at)
select a.id, a.promise_id, 'READY', a.request_received_at
  from auth_attempt a
 where a.processing_status = 'Evaluating'
on conflict (auth_attempt_id) do nothing;
