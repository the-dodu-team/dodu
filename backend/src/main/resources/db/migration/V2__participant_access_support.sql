-- SCRUM-22: 참가자 접근 지원 (docs/security/PARTICIPANT_ACCESS_CONTRACT.md §7.2, §11)
-- V1은 머지되어 수정할 수 없으므로 변경은 V2로 추가합니다.

-- 1) 철회 요청 시각.
--    withdrawn_at(운영자 적용 시각)과 별개로, 참가자가 외부 채널로 철회를 요청한 시각을 기록합니다.
--    요청~적용 사이 데이터를 분석에서 소급 제외할 때 이 값을 기준으로 씁니다(계약 §7.2).
alter table participant
    add column withdrawal_requested_at timestamptz;

alter table participant
    add constraint ck_participant_withdrawal_requested_before_applied
        check (withdrawal_requested_at is null
            or withdrawn_at is null
            or withdrawal_requested_at <= withdrawn_at);

-- 2) 참가자당 활성 접근 자격(입장 코드)은 하나만 허용합니다.
--    재발급 경쟁 상황에서도 활성 자격이 둘이 되지 않게 하는 마지막 방어선입니다(계약 §5).
create unique index ux_access_credential_one_active_per_participant
    on access_credential (participant_id)
    where revoked_at is null;

-- 3) 운영자 작업 감사 기록 (발급·재발급·철회 등, 계약 §8.2, §5).
--    코드·토큰 원문은 어떤 컬럼에도 남기지 않습니다.
--    participant_id는 삭제 요청 완료 시 NULL로 바꿔 참가자와의 연결을 끊습니다(비식별화).
create table operator_audit_log
(
    id             uuid primary key default gen_random_uuid(),
    action_type    varchar(64)  not null,   -- 값 미확정(OPEN): ISSUE_CREDENTIAL / REISSUE_CREDENTIAL / WITHDRAW 등
    participant_id uuid references participant (id),
    operator_ref   varchar(128) not null,   -- 실행한 운영자 식별(계정명 등). 구체 값은 운영 방식 확정 후 결정
    result         varchar(32)  not null,   -- 값 미확정(OPEN): SUCCESS / REJECTED / FAILED 등
    detail         jsonb,                   -- 작업별 부가 정보(예: 입력한 요청 시각). 비밀값 금지
    occurred_at    timestamptz  not null default now()
);

create index ix_operator_audit_log_participant on operator_audit_log (participant_id);
create index ix_operator_audit_log_occurred_at on operator_audit_log (occurred_at);