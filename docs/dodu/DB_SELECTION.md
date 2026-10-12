# DB 선택과 마이그레이션 기반

- 상태: 기술 결정 초안. 제품 정책이 아니며 DB 제품·도구 선택에 한정합니다.
- 작업: SCRUM-10 (이전 DODU-19)
- 작성: 문은서
- 근거: `docs/dodu/DATA_MODEL.md`(논리 모델, "DB 제품과 물리 타입은 DODU-19에서 결정"), `docs/dodu/OPEN_DECISIONS.md` D01~D13(2026-09-27 확정), `docs/DEVELOPMENT_CONVENTIONS.md`
## 1. 선택

| 항목 | 선택 | 비고 |
|---|---|---|
| DB 제품 | PostgreSQL 16 | |
| 마이그레이션 도구 | Flyway (SQL 파일 방식) | `backend/src/main/resources/db/migration/V<번호>__<설명>.sql` |
| 데이터 접근 | JDBC까지만 도입 | ORM(JPA 등)은 아직 선택하지 않음. 필요해지는 작업에서 따로 결정 |
| 테스트 격리 | Testcontainers(PostgreSQL) | 테스트마다 새 컨테이너. 로컬 DB와 접속 정보를 공유하지 않음 |
| 로컬 실행 | `backend/docker-compose.yml` | 접속 정보는 `backend/.env`(Git 제외)에서 읽음 |

## 2. 후보와 선택 이유

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| PostgreSQL | 부분 유니크 인덱스로 "참가자당 활성 약속 1개", "약속당 Evaluating 사진 1개"를 DB 제약으로 보장(DATA_MODEL 제약 후보 1번, D10). `timestamptz`·`uuid`·`jsonb` 지원 | 로컬에 컨테이너 필요 | **선택** |
| MySQL | 널리 쓰임 | 부분 유니크 인덱스가 없어 같은 제약에 슬롯 테이블이나 잠금이 필요 | 제외 |
| H2 | Docker 없이 테스트 가능 | PostgreSQL 전용 기능(부분 유니크 인덱스 등)을 검증하지 못해 마이그레이션 검증이 약함 | 테스트용으로도 제외 |

## 3. 물리 타입 규칙 (초안)

- 모든 업무 시각은 `timestamptz`(UTC 순간)로 저장하고, KST 날짜가 필요한 레코드는 `date` 열을 따로 둡니다(`promise.scheduled_date_kst`). DATA_MODEL 설계 경계와 시간 계약과 같은 방향입니다.
- `scheduled_date_kst`는 `scheduled_at`의 KST 변환 날짜와 같아야 하며 CHECK 제약으로 보장합니다.
- 식별자는 `uuid`, 반복 가능한 JSON은 `jsonb`. 문자열 길이는 초안 값이며 구현 중 조정할 수 있습니다.
- 상태 값(enum)은 확정되지 않았으므로 CHECK로 고정하지 않고 열에 "값 미확정(OPEN)"으로 표시했습니다.
## 4. V1 범위

포함(13개): 핵심 체인 9개(`study_period`, `policy_version`, `participant`, `promise`, `promise_schedule_revision`, `promise_date_usage`, `auth_attempt`, `validation_survey`, `behavior_event`)와 인증 4개(`access_credential`, `participant_device`, `participant_session`, `notification_subscription`). 인증 테이블은 SCRUM-22 구현 담당자가 바로 쓸 수 있도록 미리 만들되, 재발급·세션 종료 처리 방식은 SCRUM-12·22에서 확정합니다.

제외(4개): `participant_permission`, `notification_delivery`, `photo_asset`, `data_request`. 권한 기록·알림 발송·사진 저장·삭제 처리 계약이 정해진 뒤 별도 마이그레이션으로 추가합니다. V1이 `dev`에 머지된 뒤에는 V1을 고치지 않고 V2 이후로 바꿉니다. 인증 테이블의 구조가 SCRUM-12에서 달라지면 같은 방식으로 새 마이그레이션에서 바꿉니다.

## 5. DB 제약으로 반영한 확정 결정과 미적용 항목

| 규칙 | 근거 | 반영 |
|---|---|---|
| 같은 참가자·최종 예정일(KST)의 사용 원장은 하나 | D06, 제약 후보 2번 | `uq_promise_date_usage` |
| 사용자 재생성·기술복구는 각각 최대 1회 | D06 | `ck_user_recreation_count`, `ck_technical_recovery_count` |
| 일정 변경 이력 `(promise_id, version)` 유일 | 제약 후보 3번 | `uq_schedule_revision_version` |
| 같은 요청 키는 같은 attempt | D08, 제약 후보 4번 | `uq_auth_attempt_request` |
| 약속당 동시 Evaluating 사진 1개 | D10 | `ux_auth_attempt_one_evaluating_per_promise` |
| 약속당 설문 1개 | 제약 후보 8번 | `uq_validation_survey_promise` |
| 시도 ID가 있으면 약속 ID도 있어야 함 | 제약 후보 9번(일부) | `ck_behavior_event_attempt_requires_promise` |
| 접근 자격은 하나의 새 자격으로만 교체, 비밀값 해시 유일 | DATA_MODEL ERD(`replaces_credential_id` UK, `secret_hash` UK) | `uq_access_credential_replaces`, `uq_access_credential_secret_hash` |
| 기기 식별 해시는 참가자 범위에서만 유일 | 제약 후보 11번 | `uq_participant_device_key` |
| 참가자당 활성 세션 최대 1개 | 제약 후보 11번 (`Active`) | `ux_participant_session_one_active_per_participant` |
| 구독은 활성 세션에만 연결, 새 기기 활성화 시 이전 구독 비활성화 | 제약 후보 12번 | **미적용**: 여러 행에 걸친 규칙이라 트랜잭션에서 처리(SCRUM-22) |
| **참가자당 활성 약속 1개** | 제약 후보 1번 | **미적용**: "활성"에 해당하는 상태 값이 정해지지 않아 부분 유니크 인덱스를 주석으로만 남김 |
| 시도→약속→참가자 소유자 일치 | 제약 후보 9번 | **미적용**: 이벤트 수집 계약 확정 후 추가 |
| `T+30 ≤ intervention_ends_at`, 생성·수정 시각 범위 | D03, D11 | **미적용**: 약속 단위 비교는 애플리케이션 검증과 시간 계약 |

## 6. 연결정보

접속 URL·계정·비밀번호는 코드와 Git에 두지 않습니다. `application.yml`은 환경변수 이름만 참조하고 기본값을 두지 않으며, 값은 `backend/.env`(`.gitignore`에 포함)나 실행 환경에서 주입합니다. 저장소에는 값이 빈 `backend/.env.example`만 둡니다.

## 7. 확인 결과

| 확인 | 결과                                                                                                                |
|---|-------------------------------------------------------------------------------------------------------------------|
| V1 SQL을 깨끗한 PostgreSQL 16에 실행 | CoreSchemaMigrationTests 통과. 빈 컨테이너(postgres:16-alpine)에서 테이블 13개 생성 확인                                           |
| Flyway 재실행 시 추가 적용 없음 | 같은 테스트 클래스의 재실행 검증 통과. 추가 적용 0건                                                                                   |
| 제약 위반 케이스(KST 날짜 불일치, 중복 키, Evaluating 2개, 활성 세션 2개 등) | 같은 클래스에서 KST 날짜 불일치, 날짜 중복 사용, 재생성·복구 횟수 초과, Evaluating 2건, 요청 키 중복, 설문 중복, 활성 세션 2개, 크리덴셜 중복 교체 등이 의도한 제약명으로 거부됨 |
| 연결정보가 Git에 없음 | `git status`로 확인                                                                                                  |

## 8. 변경 이력

| 날짜 | 변경 | 작성 |
|---|---|---|
| 2026-10-09 | 초안 작성 | 문은서 |
 