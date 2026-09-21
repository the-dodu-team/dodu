# Dodu v2.5 논리 데이터 모델과 ERD

상태: **구현 전 기술설계 초안**. 이 문서는 승인된 제품 정책이나 물리 DB 스키마가 아니다.

기준 문서는 `PRD_v2.5.md`, `SERVICE_POLICY_v2.5.md`, `FLOW_LOG_MAPPING_v2.5.md`,
`IMPLEMENTATION_CONTEXT.md`, `OPEN_DECISIONS.md`, `ACCEPTANCE_CHECKLIST.md`이다.
현재 저장소에는 DB 드라이버·ORM·마이그레이션 도구가 없으므로 DB 제품과 물리 타입은 DODU-19에서 결정한다.

## 설계 경계

- 정식 회원가입이나 소셜 로그인 대신 폐쇄형 참가자 식별과 재발급 가능한 접근 자격을 모델링한다.
- 약속의 현재 상태와 변경 이력을 분리하고, 사진 제출과 판정은 `AUTH_ATTEMPT` 단위로 연결한다.
- 사진 파일 자체는 DB 밖의 비공개 객체 저장소를 전제로 하며 DB에는 파일 키와 보관·삭제 상태만 둔다.
- 알림 발송, 사용자 진입, 사진 제출, 판정 결과, 자기보고는 서로 다른 관찰 사실로 기록한다.
- 철회와 과거 데이터 삭제 요청을 구분한다. 삭제 작업은 여러 저장소에 걸쳐 추적할 수 있어야 한다.
- 모든 업무 시각은 UTC 순간으로 저장하고 KST 날짜 계산 결과가 필요한 레코드에는 별도 날짜를 보존한다.
- `D01`~`D13`의 제안은 승인 전까지 상태값, 대상 조건, 시간 제약으로 확정하지 않는다.

## ERD

```mermaid
erDiagram
    STUDY_PERIOD ||--o{ PARTICIPANT : enrolls
    PARTICIPANT ||--o{ ACCESS_CREDENTIAL : authenticates_with
    PARTICIPANT ||--o{ PARTICIPANT_PERMISSION : reports
    PARTICIPANT ||--o{ PROMISE : creates
    PARTICIPANT ||--o{ DATA_REQUEST : requests
    POLICY_VERSION ||--o{ PROMISE : governs
    PROMISE ||--o{ PROMISE_SCHEDULE_REVISION : changes
    PROMISE ||--o{ AUTH_ATTEMPT : receives
    PROMISE ||--o{ NOTIFICATION_DELIVERY : schedules
    PROMISE ||--o| VALIDATION_SURVEY : may_have
    PROMISE ||--o{ BEHAVIOR_EVENT : groups
    AUTH_ATTEMPT ||--|| PHOTO_ASSET : uses
    AUTH_ATTEMPT ||--o{ BEHAVIOR_EVENT : emits
    PARTICIPANT ||--o{ BEHAVIOR_EVENT : owns

    STUDY_PERIOD {
        uuid id PK
        string code UK
        timestamp enrollment_starts_at
        timestamp promise_creation_ends_at
        timestamp intervention_ends_at
        timestamp access_ends_at
        timestamp raw_data_delete_after
    }
    POLICY_VERSION {
        uuid id PK
        string version UK
        timestamp effective_from
        timestamp effective_to
        json settings_snapshot
    }
    PARTICIPANT {
        uuid id PK
        uuid study_period_id FK
        string public_code UK
        string lifecycle_status
        timestamp enrolled_at
        timestamp withdrawn_at
        timestamp access_disabled_at
        timestamp created_at
        timestamp updated_at
    }
    ACCESS_CREDENTIAL {
        uuid id PK
        uuid participant_id FK
        string secret_hash UK
        timestamp issued_at
        timestamp expires_at
        timestamp revoked_at
        uuid replaces_credential_id FK
    }
    PARTICIPANT_PERMISSION {
        uuid id PK
        uuid participant_id FK
        string permission_type
        string status
        timestamp observed_at
    }
    PROMISE {
        uuid id PK
        uuid participant_id FK
        uuid policy_version_id FK
        timestamp scheduled_at
        date scheduled_date_kst
        string tool_type
        string lifecycle_status
        string final_outcome
        int schedule_version
        timestamp auth_window_ends_at
        timestamp closed_at
        timestamp created_at
        timestamp updated_at
    }
    PROMISE_SCHEDULE_REVISION {
        uuid id PK
        uuid promise_id FK
        int version UK
        timestamp previous_scheduled_at
        timestamp new_scheduled_at
        date previous_date_kst
        date new_date_kst
        timestamp changed_at
    }
    AUTH_ATTEMPT {
        uuid id PK
        uuid promise_id FK
        uuid client_request_id UK
        string tool_type
        string processing_status
        string decision_result
        string rejection_reason
        string technical_error_type
        string model_version
        timestamp received_at
        timestamp decided_at
    }
    PHOTO_ASSET {
        uuid id PK
        uuid auth_attempt_id FK
        string storage_key UK
        string content_type
        bigint size_bytes
        string checksum
        timestamp stored_at
        timestamp delete_after
        timestamp deleted_at
    }
    NOTIFICATION_DELIVERY {
        uuid id PK
        uuid promise_id FK
        string notification_type
        int schedule_version
        timestamp scheduled_for
        string delivery_status
        string provider_message_id
        timestamp sent_at
        timestamp cancelled_at
    }
    VALIDATION_SURVEY {
        uuid id PK
        uuid promise_id FK
        string lifecycle_status
        boolean actual_started
        string start_delay_bucket
        string no_start_reason
        timestamp prompted_at
        timestamp reminder_sent_at
        timestamp submitted_at
        timestamp expires_at
    }
    BEHAVIOR_EVENT {
        uuid id PK
        uuid event_id UK
        uuid participant_id FK
        uuid promise_id FK
        uuid auth_attempt_id FK
        string event_type
        timestamp occurred_at
        timestamp received_at
        string policy_version
        json attributes
    }
    DATA_REQUEST {
        uuid id PK
        uuid participant_id FK
        string request_type
        string processing_status
        timestamp requested_at
        timestamp completed_at
        string failure_reason
    }
```

## 엔티티 책임

| 엔티티 | 책임 | 주요 제약 |
|---|---|---|
| `STUDY_PERIOD` | 행동검증의 예약·개입·접근·보관 경계를 분리 | D03 승인 전 네 끝점을 하나로 합치지 않음 |
| `POLICY_VERSION` | 운영값 변경 전후 데이터 구분 | 약속 생성 시 적용 버전을 고정 |
| `PARTICIPANT` | 식별 가능한 참가자와 참여 생명주기 | 철회와 접근 만료를 구분 |
| `ACCESS_CREDENTIAL` | 재발급·폐기 가능한 접근 자격 | 평문 비밀값 저장 금지, 재발급 시 이전 자격 폐기 |
| `PARTICIPANT_PERMISSION` | 실제 관찰한 알림·카메라 권한 상태 | 권한 거부를 인증 실패로 변환하지 않음 |
| `PROMISE` | 최종 예정 시각과 현재 진행 상태 | 참가자별 활성 1개, KST 예정일별 생성 제한의 기준 |
| `PROMISE_SCHEDULE_REVISION` | 동일 `promise_id`의 일정 변경 이력 | 버전 증가, 수정 전후 시각 보존 |
| `AUTH_ATTEMPT` | 사진 1회 제출의 접수·판정·오류 | 판정 거절과 기술오류 분리, 중복 요청 키 보유 |
| `PHOTO_ASSET` | 비공개 파일 참조와 삭제 증적 | 공식 종료 후 30일 이내 삭제, 요청 삭제 지원 |
| `NOTIFICATION_DELIVERY` | T/T+10/T+20 및 설문 리마인드의 실행 결과 | 발송과 수신·열람을 동일시하지 않음 |
| `VALIDATION_SURVEY` | 약속별 자기보고 상태와 응답 | 미응답을 미시작으로 변환하지 않음 |
| `BEHAVIOR_EVENT` | 분석용 관찰 사실의 append-only 원장 | `event_id` 중복 제거, 가짜 `promise_id` 금지 |
| `DATA_REQUEST` | 철회·과거 데이터 삭제 처리 추적 | 철회 후 개입 중단, 삭제 범위별 완료 확인 |

## 물리 스키마에서 필요한 제약 후보

아래는 확정 정책을 보존하기 위한 후보이며 DB 제품을 고른 뒤 마이그레이션으로 구체화한다.

1. 한 참가자의 활성 약속은 최대 1개다. 부분 유니크 인덱스를 지원하지 않는 DB라면 트랜잭션 잠금과 별도 슬롯 테이블을 검토한다.
2. `participant_id + scheduled_date_kst` 제한은 취소 후에도 복구되지 않아야 한다. `PROMISE`를 삭제하지 않고 보존하거나 별도 날짜 사용 원장을 둔다.
3. 일정 수정은 `schedule_version`을 낙관적 잠금 키로 사용하고, 알림 작업도 같은 버전을 저장해 오래된 작업을 무효화한다.
4. `AUTH_ATTEMPT.client_request_id`, `BEHAVIOR_EVENT.event_id`, 알림의 공급자 키에는 유일 제약을 두어 재전송을 중복 집계하지 않는다.
5. `PHOTO_ASSET.storage_key`는 외부 공개 URL이 아닌 내부 식별자다. 삭제 성공 후 `deleted_at`을 기록하고 파일 존재 여부를 별도로 검증한다.
6. 원시 이벤트의 `promise_id`와 `auth_attempt_id`는 사건에 따라 nullable이다. 참가자 수준 사건에 가짜 FK를 만들지 않는다.
7. 개인 식별 연결을 제거한 집계와 삭제 대상 원시 데이터는 저장 영역과 접근 권한을 분리한다.

## OPEN 결정과 스키마 영향

| OPEN ID | 현재 모델의 유보 방식 |
|---|---|
| D01 | 자기보고 응답값의 최종 enum과 상호배타 조건을 확정하지 않음 |
| D02, D07, D12 | 설문 생성 대상·노출 기준일·만료 기산점은 서비스 규칙으로 남기고 DB 제약으로 고정하지 않음 |
| D03 | 예약·개입·설문·접근 종료 시각을 `STUDY_PERIOD`에서 분리 |
| D04, D10, D11 | 접수·판정·인증 창 종료 시각을 각각 저장하고 우선순위와 끝점 비교는 미확정 |
| D05 | 철회·권한 부재·판정 외 오류를 하나의 실패 enum으로 합치지 않음 |
| D06 | 날짜 수정과 기술 실패 후 날짜 사용 처리용 이력은 보존하되 재사용 규칙은 미확정 |
| D08 | 시도·이벤트 식별자를 분리하고 중복 제거 키를 둠. 최종 분석 계약은 미확정 |
| D09 | 생성일과 예정 시작일을 모두 계산할 수 있게 원시 시각과 KST 날짜를 보존 |
| D13 | 지원 환경·모델 QA·Go 판단 필드는 핵심 트랜잭션 스키마에 넣지 않음 |

## 구현 순서

1. DODU-19에서 DB 제품, 마이그레이션 도구, UUID·시간·JSON 물리 타입을 결정한다.
2. `STUDY_PERIOD`, `POLICY_VERSION`, `PARTICIPANT`, `ACCESS_CREDENTIAL`의 최소 보안 모델을 검토한다.
3. `PROMISE`와 일정 변경 이력을 만들고 동시 생성·수정 경계 테스트를 먼저 작성한다.
4. 인증 시도·사진 메타데이터·알림 실행을 추가하고 idempotency와 늦은 결과를 검증한다.
5. 자기보고·행동 이벤트·철회 및 삭제 작업을 추가하고 보관 삭제 리허설을 수행한다.
6. OPEN 결정이 승인될 때 관련 enum, 제약, 전이와 테스트를 별도 마이그레이션으로 확정한다.

## 검증 질문

- 참가자 A의 접근 자격으로 참가자 B의 약속·사진·설문을 조회할 수 없는가?
- 동시에 두 약속을 만들 때 활성 1개와 KST 예정일 1개 제한이 유지되는가?
- 일정 수정 전 알림 작업과 중복 실행이 새 상태를 바꾸지 않는가?
- 같은 사진 요청·AI 콜백·이벤트가 재전송되어도 한 번만 반영되는가?
- 마감 전 서버가 접수한 시도와 마감 후 접수 시도를 구분할 수 있는가?
- 철회 직후 예약 알림과 신규 이벤트 수집이 멈추고 늦은 판정이 행동 결과를 바꾸지 않는가?
- 삭제 요청으로 사진·원시 이벤트·설문·식별 연결을 찾아 지우고 완료를 증명할 수 있는가?
- 30일·90일 보관 작업이 비식별 집계 결과와 연결 가능한 원시 데이터를 구분하는가?
