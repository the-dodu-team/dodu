-- Technical test data only. These dates are not the real participant study schedule.
-- Re-running does not extend dates, reset a participant, or reissue credentials.
begin;

insert into study_period (
    code, enrollment_starts_at, official_ends_at, promise_creation_ends_at,
    intervention_ends_at, survey_collection_ends_at, access_ends_at,
    photo_delete_after, raw_data_delete_after
) values (
    'technical-smoke-20261104', now(), now() + interval '7 days',
    now() + interval '7 days' - interval '30 minutes', now() + interval '7 days',
    now() + interval '9 days', now() + interval '9 days',
    now() + interval '37 days', now() + interval '97 days'
) on conflict (code) do nothing;

insert into policy_version (version, effective_from, settings_snapshot)
values ('v2.5.2', now(), '{"purpose":"technical-smoke-test","selfReportHours":24}'::jsonb)
on conflict (version) do nothing;

insert into participant (study_period_id, public_code, lifecycle_status, enrolled_at)
select id, 'TECH-SMOKE-001', 'Active', now()
from study_period where code = 'technical-smoke-20261104'
on conflict (public_code) do nothing;

select id as participant_id, public_code
from participant where public_code = 'TECH-SMOKE-001';

commit;
