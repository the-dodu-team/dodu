-- Supabase SQL Editor에서 실행. 기술 테스트 연구 1개만 대상으로 한다.
-- 무기한 NULL/infinity 대신 Java/브라우저에서 읽을 수 있는 2099년 종료 시각을 사용한다.
-- 참가자·코드·약속·세션·사진을 삭제하거나 재발급하지 않는다.
begin;

do $$
declare
    target_id uuid;
    test_end timestamptz := '2099-12-31 23:59:00+09';
begin
    select id into target_id from study_period
    where code = 'technical-smoke-20261104' for update;
    if target_id is null then
        raise exception 'TECHNICAL_TEST_PERIOD_NOT_FOUND';
    end if;
    update study_period set
        official_ends_at = test_end,
        promise_creation_ends_at = test_end - interval '30 minutes',
        intervention_ends_at = test_end,
        survey_collection_ends_at = test_end + interval '2 days',
        access_ends_at = test_end + interval '2 days',
        photo_delete_after = test_end + interval '30 days',
        raw_data_delete_after = test_end + interval '90 days'
    where id = target_id;
end $$;

select code,
       intervention_ends_at at time zone 'Asia/Seoul' as intervention_ends_kst,
       access_ends_at at time zone 'Asia/Seoul' as access_ends_kst
from study_period where code = 'technical-smoke-20261104';

commit;
