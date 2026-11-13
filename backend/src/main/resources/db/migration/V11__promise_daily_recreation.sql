-- V11: 하루(KST 예정 시작일) 약속 1개 + 인증 미완료·기술 실패 약속의 같은 날 재생성.
-- 재생성되면 이전 실패 약속은 참가자 화면(이력·설문·설문 알림)에서 제외한다.
-- 연구 원자료(사진 attempt·행동 로그)는 지우지 않고, 대체 관계만 기록한다.
alter table promise
    add column superseded_by_promise_id uuid references promise (id),
    add column superseded_at            timestamptz;

create index ix_promise_participant_date_visible
    on promise (participant_id, scheduled_date_kst)
    where superseded_by_promise_id is null;
