-- V1: 핵심 체인 + 인증 테이블 초안 (참가자 -> 접근 자격·기기·세션·알림 구독 / 약속 -> 시도 -> 이벤트·설문)
-- 근거: docs/dodu/DATA_MODEL.md (구현 전 기술설계 초안), docs/dodu/OPEN_DECISIONS.md D01~D13 (2026-09-27 확정)
-- 이 파일은 물리 스키마 초안입니다. 확정되지 않은 상태 값(enum)은 CHECK로 고정하지 않습니다 (OPEN).
-- 인증 테이블의 재발급·세션 종료 처리 방식은 SCRUM-12(보안 계약 설계)와 SCRUM-22(구현)에서 확정합니다.
-- 포함하지 않음: PARTICIPANT_PERMISSION, NOTIFICATION_DELIVERY, PHOTO_ASSET, DATA_REQUEST
--   (권한 기록·알림 발송·사진 저장·삭제 처리 계약 확정 후 V2 이후)
-- 타입: UUID는 uuid, 시각은 timestamptz(UTC 순간), KST 일자는 date, JSON은 jsonb. 문자열 길이는 초안값입니다.

create table study_period (
                              id                        uuid primary key default gen_random_uuid(),
                              code                      varchar(64)  not null,
                              enrollment_starts_at      timestamptz,
                              official_ends_at          timestamptz,
                              promise_creation_ends_at  timestamptz,
                              intervention_ends_at      timestamptz,   -- D03: 약속 T+30 <= intervention_ends_at (약속 단위 검증은 애플리케이션)
                              survey_collection_ends_at timestamptz,
                              access_ends_at            timestamptz,
                              photo_delete_after        timestamptz,
                              raw_data_delete_after     timestamptz,
                              constraint uq_study_period_code unique (code)
);

create table policy_version (
                                id                 uuid primary key default gen_random_uuid(),
                                version            varchar(64) not null,
                                effective_from     timestamptz not null,
                                effective_to       timestamptz,
                                settings_snapshot  jsonb       not null default '{}'::jsonb,
                                constraint uq_policy_version_version unique (version)
);

create table participant (
                             id                       uuid primary key default gen_random_uuid(),
                             study_period_id          uuid        not null references study_period (id),
                             public_code              varchar(64) not null,
                             lifecycle_status         varchar(32) not null,   -- 값 미확정(OPEN): 철회와 접근 만료를 구분 (D05)
                             enrolled_at              timestamptz not null,
                             onboarding_completed_at  timestamptz,
                             withdrawn_at             timestamptz,
                             access_disabled_at       timestamptz,
                             created_at               timestamptz not null default now(),
                             updated_at               timestamptz not null default now(),
                             constraint uq_participant_public_code unique (public_code)
);
create index ix_participant_study_period on participant (study_period_id);

create table promise (
                         id                       uuid primary key default gen_random_uuid(),
                         participant_id           uuid         not null references participant (id),
                         policy_version_id        uuid         not null references policy_version (id),
                         task_title               varchar(200) not null,
                         scheduled_at             timestamptz  not null,
                         scheduled_date_kst       date         not null,   -- D06·D09: 최종 예정 시작일(KST)
                         tool_type                varchar(32)  not null,
                         creation_type            varchar(32)  not null,   -- D06·D09: 일반/사용자 재생성/기술복구 구분, 값 미확정(OPEN)
                         source_promise_id        uuid references promise (id),
                         lifecycle_status         varchar(32)  not null,   -- 값 미확정(OPEN)
                         final_outcome            varchar(32),             -- D05: Cancelled/TechFailed/Permission/Withdrawn은 AuthIncomplete와 별도, 값 미확정(OPEN)
                         schedule_version         integer      not null default 1,   -- 일정 세대
                         row_version              bigint       not null default 0,   -- 낙관적 잠금
                         auth_window_ends_at      timestamptz,
                         intervention_reached_at  timestamptz,
                         auth_completed_at        timestamptz,
                         closed_at                timestamptz,
                         created_at               timestamptz  not null default now(),
                         updated_at               timestamptz  not null default now(),
    -- DATA_MODEL_GUIDE: scheduled_date_kst는 scheduled_at의 KST 변환 날짜와 같아야 함
                         constraint ck_promise_scheduled_date_kst
                             check (scheduled_date_kst = (scheduled_at at time zone 'Asia/Seoul')::date)
    );
create index ix_promise_participant on promise (participant_id);
create index ix_promise_source on promise (source_promise_id);
-- [초안, 미적용] 참가자당 활성 약속 최대 1개 (DATA_MODEL 제약 후보 1번).
-- "활성"에 해당하는 lifecycle_status 값이 아직 정해지지 않아 인덱스를 만들지 않습니다.
-- 상태 값이 정해지면 아래 형태의 부분 유니크 인덱스로 구현합니다.
--   create unique index ux_promise_one_active_per_participant on promise (participant_id)
--       where lifecycle_status in (<활성 상태 값>);

create table promise_schedule_revision (
                                           id                   uuid primary key default gen_random_uuid(),
                                           promise_id           uuid        not null references promise (id),
                                           version              integer     not null,
                                           previous_scheduled_at timestamptz not null,
                                           new_scheduled_at     timestamptz not null,
                                           previous_date_kst    date        not null,
                                           new_date_kst         date        not null,
                                           changed_at           timestamptz not null,
    -- D06 / 제약 후보 3번: (promise_id, version) 유일, 서로 다른 약속의 version=1은 허용
                                           constraint uq_schedule_revision_version unique (promise_id, version)
);

create table promise_date_usage (
                                    id                       uuid primary key default gen_random_uuid(),
                                    participant_id           uuid    not null references participant (id),
                                    scheduled_date_kst       date    not null,
                                    user_recreation_count    integer not null default 0,
                                    technical_recovery_count integer not null default 0,
                                    first_used_at            timestamptz not null,
                                    updated_at               timestamptz not null default now(),
    -- 제약 후보 2번: 참가자·최종 예정일(KST) 복합 유일
                                    constraint uq_promise_date_usage unique (participant_id, scheduled_date_kst),
    -- D06: 동일 날짜 사용자 재생성 최대 1회, 기술복구 최대 1회(서로 별도)
                                    constraint ck_user_recreation_count check (user_recreation_count between 0 and 1),
                                    constraint ck_technical_recovery_count check (technical_recovery_count between 0 and 1)
);

create table auth_attempt (
                              id                   uuid primary key default gen_random_uuid(),
                              promise_id           uuid         not null references promise (id),
                              client_request_id    uuid         not null,
                              request_fingerprint  varchar(128) not null,
                              tool_type            varchar(32)  not null,
                              processing_status    varchar(32)  not null,   -- 값 미확정(OPEN). 다만 'Evaluating'은 D10에서 확정된 이름
                              decision_result      varchar(32),             -- 성공/거절/기술오류, 값 미확정(OPEN)
                              rejection_reason     varchar(64),
                              technical_error_type varchar(64),
                              model_version        varchar(64),
                              request_received_at  timestamptz  not null,
                              valid_received_at    timestamptz,             -- D04·D11: T <= received < T+30 인 유효 접수 시각
                              decided_at           timestamptz,
    -- D08 / 제약 후보 4번: 같은 요청 키는 같은 attempt. 같은 키에 다른 본문은 애플리케이션이 충돌로 처리
                              constraint uq_auth_attempt_request unique (promise_id, client_request_id)
);
-- D10: 약속당 동시에 Evaluating인 사진은 최대 1개
create unique index ux_auth_attempt_one_evaluating_per_promise
    on auth_attempt (promise_id) where processing_status = 'Evaluating';

create table validation_survey (
                                   id                    uuid primary key default gen_random_uuid(),
                                   promise_id            uuid        not null references promise (id),
                                   questionnaire_version varchar(64) not null,
                                   lifecycle_status      varchar(32) not null,   -- 값 미확정(OPEN)
                                   eligibility_status    varchar(32) not null,   -- D12: AuthCompleted/AuthIncomplete만 대상, 값 미확정(OPEN)
                                   actual_started        boolean,                -- D01: 인증 결과와 분리해 자기보고로만 저장
                                   start_order           varchar(32),
                                   start_delay_bucket    varchar(32),
                                   no_start_reason       varchar(64),
                                   prompted_at           timestamptz,            -- D02·D07: 안내 시각과 24시간 수명
                                   reminder_sent_at      timestamptz,
                                   inapp_reprompted_at   timestamptz,
                                   skipped_at            timestamptz,
                                   submitted_at          timestamptz,
                                   available_at          timestamptz,
                                   expires_at            timestamptz,
    -- 제약 후보 8번: 약속당 설문 최대 1개
                                   constraint uq_validation_survey_promise unique (promise_id)
);

create table behavior_event (
                                event_id                uuid primary key,     -- D08: 클라이언트·서버가 만든 event_id로 중복 제거 (기본값 없음)
                                participant_id          uuid        not null references participant (id),
                                promise_id              uuid references promise (id),
                                auth_attempt_id         uuid references auth_attempt (id),
                                event_type              varchar(64) not null,
                                occurred_at             timestamptz not null,
                                received_at             timestamptz not null,
                                policy_version          varchar(64),
                                model_version           varchar(64),
                                analysis_exclusion_reason varchar(64),         -- D05: 분석 제외 사유와 오류 분리
                                attributes              jsonb       not null default '{}'::jsonb,
    -- 제약 후보 9번 중 일부: 시도 ID가 있으면 약속 ID도 있어야 함.
    -- 시도->약속->참가자의 소유자 일치 검증(복합 FK 등)은 이벤트 수집 계약 확정 후 추가합니다.
                                constraint ck_behavior_event_attempt_requires_promise
                                    check (auth_attempt_id is null or promise_id is not null)
);
create index ix_behavior_event_participant on behavior_event (participant_id);
create index ix_behavior_event_promise on behavior_event (promise_id);
create index ix_behavior_event_attempt on behavior_event (auth_attempt_id);

-- ===== 인증 테이블 (DATA_MODEL: ACCESS_CREDENTIAL / PARTICIPANT_DEVICE / PARTICIPANT_SESSION / NOTIFICATION_SUBSCRIPTION) =====
-- 평문 비밀값(참가자 코드, 세션 토큰, 구독 키)은 저장하지 않고 해시만 저장합니다.

create table access_credential (
                                   id                      uuid primary key default gen_random_uuid(),
                                   participant_id          uuid         not null references participant (id),
                                   secret_hash             varchar(128) not null,
                                   issued_at               timestamptz  not null,
                                   expires_at              timestamptz,
                                   revoked_at              timestamptz,
                                   replaces_credential_id  uuid references access_credential (id),
                                   constraint uq_access_credential_secret_hash unique (secret_hash),
    -- 재발급 시 이전 자격은 하나의 새 자격으로만 교체됨 (DATA_MODEL: replaces_credential_id FK, UK)
                                   constraint uq_access_credential_replaces unique (replaces_credential_id)
);
create index ix_access_credential_participant on access_credential (participant_id);

create table participant_device (
                                    id                uuid primary key default gen_random_uuid(),
                                    participant_id    uuid         not null references participant (id),
                                    device_key_hash   varchar(128) not null,
                                    lifecycle_status  varchar(32)  not null,   -- 값 미확정(OPEN)
                                    first_seen_at     timestamptz  not null,
                                    last_seen_at      timestamptz  not null,
                                    deactivated_at    timestamptz,
    -- 제약 후보 11번: 기기 식별 해시는 참가자 범위에서만 유일 (다른 참가자와는 같은 값 허용)
                                    constraint uq_participant_device_key unique (participant_id, device_key_hash)
);

create table participant_session (
                                     id                     uuid primary key default gen_random_uuid(),
                                     participant_id         uuid         not null references participant (id),
                                     participant_device_id  uuid         not null references participant_device (id),
                                     session_token_hash     varchar(128) not null,
                                     lifecycle_status       varchar(32)  not null,   -- 'Active'는 DATA_MODEL 제약 후보 11번에 명시된 값, 나머지 값은 미확정(OPEN)
                                     issued_at              timestamptz  not null,
                                     last_seen_at           timestamptz  not null,
                                     expires_at             timestamptz  not null,
                                     revoked_at             timestamptz,
                                     revoke_reason          varchar(64),             -- 값 미확정(OPEN): 7장 Q7의 SESSION_REPLACED/CREDENTIAL_REVOKED와 연결될 수 있음
                                     constraint uq_participant_session_token unique (session_token_hash)
);
create index ix_participant_session_participant on participant_session (participant_id);
create index ix_participant_session_device on participant_session (participant_device_id);
-- 제약 후보 11번: 참가자당 활성 세션은 최대 1개
create unique index ux_participant_session_one_active_per_participant
    on participant_session (participant_id) where lifecycle_status = 'Active';
-- [미적용] 세션의 participant_id와 기기의 participant_id가 같아야 한다는 검증은 복합 FK 또는 애플리케이션에서 보장 (SCRUM-22)

create table notification_subscription (
                                           id                      uuid primary key default gen_random_uuid(),
                                           participant_id          uuid         not null references participant (id),
                                           participant_session_id  uuid         not null references participant_session (id),
                                           subscription_key_hash   varchar(128) not null,
                                           lifecycle_status        varchar(32)  not null,   -- 값 미확정(OPEN)
                                           activated_at            timestamptz  not null,
                                           last_seen_at            timestamptz,
                                           deactivated_at          timestamptz,
                                           constraint uq_notification_subscription_key unique (subscription_key_hash)
);
create index ix_notification_subscription_participant on notification_subscription (participant_id);
create index ix_notification_subscription_session on notification_subscription (participant_session_id);
-- [미적용] "구독은 활성 세션에만 연결", "새 기기 활성화 시 이전 구독 비활성화"(제약 후보 12번)는
-- 여러 행에 걸친 규칙이라 트랜잭션에서 처리합니다 (SCRUM-22).