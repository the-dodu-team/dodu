# 약속 API 계약 초안 — SCRUM-76 / SCRUM-60

- 담당: 강민 / 작성일: 2026-10-15
- 상태: **구현·통합 검증 완료 범위와 미확정 계약을 함께 기록**. 일반 CRUD·멱등·동시성은 SCRUM-23에서 실행 API로 검증했으며, 공통 envelope와 FE 인수 항목은 별도 합의 전 제안으로 유지한다.
- 범위: 일반 약속 생성, 자기 약속 조회, 예정 시작일시 수정, 시작 전 취소. 재생성·기술복구 생성 계약은 별도 증분으로 남긴다.

후속 증분: [재생성·기술복구·멱등 처리 초안](PROMISE_RECOVERY_CONTRACT.md). 아래 ‘미정’ 표기는 최초 CRUD 증분의 상태이며 후속 문서에서 제안한 부분도 팀 인수 전까지 확정으로 보지 않는다.
- 근거: PRD v2.5 F-01·F-06, 최신 Notion 운영정책 §1~2·§12~13, 승인 D03·D05·D06·D11, Flow·Log §1.2~1.3·§3~4.
- 공통: [응답·오류](COMMON_API_CONTRACT.md), [시간](TIME_CONTRACT.md), [접근](../security/PARTICIPANT_ACCESS_CONTRACT.md), [화면별 인수 지도](SCREEN_API_HANDOFF.md).

## 1. 접근·공통 규칙

모든 요청은 접근 계약의 `Authorization: Bearer` 세션으로 참가자를 결정한다. 토큰 예시는 저장하지 않는다. 요청에서 `participantId`, 상태, 일자, 횟수를 받아 신뢰하지 않는다. 본문은 아래 명시한 필드만 허용하는 방향을 제안한다.

- 조회·수정·취소는 세션 참가자와 약속 ID를 함께 조건으로 조회한다. 타인 자원과 없는 자원은 동일한 `RESOURCE_NOT_FOUND`/404. 공통 계약 §3.5의 타인 자원 403 설명과 충돌하므로 접근 계약 §4를 따르는 보완안으로 리뷰한다.
- 세션 교체·코드 폐기·철회·접근 만료를 조회와 쓰기에서 검사한다. 철회와 쓰기는 같은 참가자 잠금 경계에서 검증하여 이미 끝난 요청의 지연 결과가 철회를 덮어쓰지 않게 한다.
- 응답은 공통 envelope와 `serverTime`을 사용한다. 아래 `+09:00` 직렬화도 공통 시간 계약의 기술 제안 상태를 유지한다.
- 브라우저 버튼과 타이머는 안내용이다. 저장 직전 서버 시간·상태·수정 횟수·날짜 사용 이력을 트랜잭션에서 재검증한다. 자기보고 미응답은 생성 조건으로 사용하지 않는다.

## 2. 요청·응답 제안

| 요청 | 본문 | 성공 | 역할 |
|---|---|---|---|
| `POST /api/promises` | `taskTitle`, `toolType`, `scheduledAt` | 201, `data.promise` | 일반 약속 생성 |
| `GET /api/promises/active` | 없음 | 200, `data.promise` 또는 null | 재진입 시 활성 약속 복원. null은 오류가 아님 |
| `GET /api/promises/{promiseId}` | 없음 | 200, `data.promise` | 자기 약속 상세 조회 |
| `PATCH /api/promises/{promiseId}/schedule` | `scheduledAt`, `expectedVersion` | 200, `data.promise` | 날짜 이동을 포함한 동일 약속 수정 |
| `POST /api/promises/{promiseId}/cancel` | `expectedVersion` | 200, `data.promise` | 삭제가 아닌 Cancelled 종료, 취소 사유는 받지 않음 |

`/active`는 `{promiseId}`보다 우선하는 고정 경로다. 컬렉션 생성·상세 조회·활성 조회의 응답은 동일한 `data.promise` 형태를 제안한다. 공통 fixture의 직접 `data.promiseId` 예시와 다르므로 FE 리뷰 때 결정하며 기존 fixture는 변경하지 않는다.

### 입력

| 필드 | 제약 |
|---|---|
| `taskTitle` | 필수 문자열. 최신 LOCKED 첨부 §1.3의 최대30자·단일행·앞뒤 공백 제거·제거 후 빈값 불가·줄바꿈/제어문자 거절. 문자 계수·trim 집합·제어문자 세부 범위는 기술 합의 전이며 이 초안에서 코드로 고정하지 않음 |
| `toolType` | `COMPUTER` 또는 `BOOK` 제안. 지원 도구는 승인 정책 §4에 따름 |
| `scheduledAt` | 필수 오프셋 포함 ISO 8601 문자열. 기존 시간 계약에 따라 서버에서 순간으로 변환 |
| `expectedVersion` | 수정·취소 시 필수 비음수 정수. 가장 최근 조회 응답의 `version`. 충돌 시 자동 덮어쓰기 금지 |

### Promise 응답

| 필드 | 형태·의미 |
|---|---|
| `promiseId` | 불투명 UUID 문자열 |
| `taskTitle`, `toolType` | 저장된 작업명·도구 |
| `scheduledAt`, `scheduledDateKst` | 최종 T와 서버가 계산한 KST 일자 |
| `authWindowEndsAt` | 최종 T+30. 새로 진입해도 연장하지 않음 |
| `lifecycleStatus`, `finalOutcome` | 진행 상태와 최종 결과를 분리. 아래 예시는 Scheduled/null과 Closed/Cancelled만 사용. 다른 상태의 전체 enum 계약은 후속 증분 |
| `version` | 이 약속 변경 검사용 버전. DB `row_version`에 대응 제안이며 `schedule_version`과 구분 |
| `scheduleVersion` | 알림 무효화용 일정 세대. UI가 값을 쓰거나 변경하지 않음 |
| `actions.canReschedule`, `actions.canCancel` | 응답 생성 시 서버가 계산한 boolean. 권한 보증이 아니며 클릭 시 다시 검증 |

타인 참가자 ID·세션·구독·스토리지 키는 응답에 포함하지 않는다. 재생성/기술복구 생성 및 Evaluating/결과 상태의 필드는 후속 계약에서 확장하며 이 예시를 전체 enum/상태 모델로 사용하지 않는다.

## 3. 시간·동시성·로그

- 생성·수정: 검증 시점 `now+5분 ≤ 새 T ≤ now+72시간`, `새 T+30분 ≤ 공식 개입 종료`. 수정은 **기존 T 이전**인지 별도로 확인한다. 정확히 기존 T의 수정·취소는 불가.
- 수정은 최대1회, 재생성 약속은 재수정 불가. 날짜 이동도 동일 promiseId의 수정1회이며 자동 취소·재생성하지 않는다.
- 생성은 참가자 활성1개 및 최종 KST 일자 사용 조건을 원자적으로 검증한다. DB V1에는 활성 약속 유니크 제약이 아직 없으므로 V1만으로 동시 생성 안전성이 확보되었다고 보지 않는다. V2+ 협의와 트랜잭션 구현이 필요하다.
- 수정·취소는 `expectedVersion`과 현재 버전을 비교해 성공한 변경만 version을 증가시킨다. 충돌 시 409로 반환하고 FE는 재조회 후 사용자에게 확인한다.
- 생성·수정·취소의 `Idempotency-Key`는 기술 제안: 참가자+method+정규화 경로+키 범위로 처리하고 같은 키/다른 본문은 충돌시킨다. 동일 키 재전송은 새 약속·수정 이력·취소 이벤트를 추가하지 않는다. **키 형식·보관기간·처리 중 응답·마감 후 성공 재현 응답 방식은 아직 미정**이므로 FE 자동 재전송과 운영 활성화는 보류한다. 키가 있어도 현재 세션·소유권 검증을 생략하지 않는다.
- 저장·날짜 사용량·일정 세대 변경·로그/알림 예약 전달 의도는 함께 성공하거나 롤백되어야 한다. 외부 알림 발송을 DB 트랜잭션 안에서 실행하지 않는다. 전달 방식은 알림 담당 은서와 협의한다.
- 수정 시 이전 알림을 무효화하고 최종 T 기준으로 갱신한다. 취소 후 알림은 발송하지 않는다. 실행 직전에도 현재 상태·세대·철회를 확인한다.
- `promise_created`, `promise_rescheduled`, `promise_cancelled`는 서버 저장 성공 사실에 연결한다. 수정 전후 T를 남기되 작업명·접근정보를 디버그 로그에 출력하지 않는다. 네트워크 retry를 새 행동으로 집계하지 않는다.

## 4. 오류와 FE 처리

| HTTP | type / code | 처리 |
|---|---|---|
| 400 | VALIDATION / `PROMISE_SCHEDULE_TOO_SOON`, `PROMISE_SCHEDULE_TOO_FAR`, `PROMISE_SCHEDULE_AFTER_STUDY_END`, `DATETIME_OFFSET_REQUIRED` | 입력을 유지하고 해당 필드 안내 |
| 400 | VALIDATION / `INVALID_REQUEST` (추가 제안) | 필수·형식·도구·작업명 오류. `field` 포함, 사용자 입력 원문은 반환하지 않음 |
| 401 | UNAUTHENTICATED / `ACCESS_REQUIRED`, `SESSION_REPLACED`, `CREDENTIAL_REVOKED`, `ACCESS_REVOKED` | 접근 계약에 따라 처리. 자동 코드 재입력·업무 요청 재전송 금지 |
| 404 | NOT_FOUND / `RESOURCE_NOT_FOUND` | 없음/타인 자원 동일 안내. 소유자 정보 노출 금지 |
| 409 | CONFLICT / `PROMISE_ACTIVE_EXISTS`, `PROMISE_DATE_ALREADY_USED` | 현재 약속 재조회, 입력 유실 없이 안내 |
| 409 | CONFLICT / `PROMISE_RESCHEDULE_LIMIT_REACHED`, `PROMISE_NOT_MODIFIABLE`, `STUDY_PERIOD_ENDED` | 재조회 후 가능 동작 갱신. 브라우저 시간을 바꿔 재시도하지 않음 |
| 409 | CONFLICT / `PROMISE_VERSION_CONFLICT`, `IDEMPOTENCY_KEY_REUSED` (추가 제안) | 자동 덮어쓰기 금지. 같은 키에 다른 본문을 재전송하지 않음 |
| 503 | TECHNICAL / `SERVICE_UNAVAILABLE` | 성공·저장완료로 표시하지 않음. retryable은 자동 재시도 허가가 아님 |

오류 우선순위는 세션 검증 → 소유권 조회 → 해당 동작의 상태·버전·입력 검증 방향을 제안한다. 타인 자원에 대한 시간·상태 정보가 먼저 노출되지 않아야 한다. 복수 입력 오류 중 어느 필드를 먼저 반환할지는 구현 계약에서 보완한다.

## 5. Mock·검토·검증

[약속 사례 JSON](fixture/promise/cases.json)은 `cases[].response.httpStatus`와 `body`를 mock 응답으로 사용한다. 요청의 `body`는 입력이며 헤더에는 실제 토큰을 넣지 않았다. 성공 예시도 가상 데이터이며 저장·알림 실행 증거가 아니다.

포함: 생성·조회·빈 활성·수정·취소·시간 입력 오류·세션 교체·타인/없는 자원·버전 충돌·기술오류. Evaluating 및 인증 만료는 사진 계약의 후속 범위이며 이 CRUD 사례로 대체하지 않는다.

- [x] 일반 CRUD 계약 및 mock 사례 작성
- [ ] 제목 문자 계산·trim·제어문자 기술 합의
- [ ] 멱등 상세와 전체 상태·재생성/기술복구 계약
- [ ] 은서의 인증·알림·DB 연결 검토
- [ ] 연정의 필드·오류·빈 상태 검토
- [x] 실제 HTTP·DB·동시성 경합 검증 — Docker/Testcontainers 통합 테스트로 생성·수정·취소 확인

지정 Notion 기준 페이지는 이번에도 404다. 접근 가능한 운영정책 페이지는 2026-10-12 수정 표시와 20261013 첨부를 유지하며, 하위 전체 최신성은 확인하지 못했다. 미승인 카메라 요청·기술오류 UI·기록 범위는 SCRUM-75에 남긴다.
