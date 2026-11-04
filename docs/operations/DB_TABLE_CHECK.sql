-- Supabase SQL Editor에서 실행하는 읽기 전용 확인. 행 데이터·사진·접근정보를 조회하지 않는다.
-- dodu-photos는 Storage 버킷이다. 아래 테이블은 Flyway가 PostgreSQL에 생성한다.
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in (
    'flyway_schema_history', 'participant', 'participant_device',
    'participant_session', 'access_credential', 'promise',
    'promise_schedule_revision', 'promise_date_usage',
    'auth_attempt', 'photo_asset', 'validation_survey'
  )
order by table_name;
