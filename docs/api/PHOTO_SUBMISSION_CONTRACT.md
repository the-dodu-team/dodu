# 사진 제출 API 계약 — SCRUM-83

- 상태: 메타데이터 저장·중복 제출·판정 결과 상태 전이 구현 증분 검증 완료. 사진 바이트의 외부 저장소 업로드와 AI worker 계약은 후속 티켓에서 다룬다.
- 담당: 강민
- 기준: `InterventionWindowPolicy`, `PhotoAttemptIdentityPolicy`, `PhotoEvaluationReservations`, D04·D08·D10·D11.

## 요청

`POST /api/promises/{promiseId}/photo-attempts`

- 인증: `Authorization: Bearer <active-session-token>`
- 전송: `multipart/form-data`
- 필드: `clientRequestId`(UUID), `toolType`(1~32자), `photo`(비어 있지 않은 파일)
- 서버는 세션의 `participant_id`와 약속 소유자를 비교한다.
- 서버 수신시각을 사용한다. 클라이언트가 보낸 시각은 판정에 사용하지 않는다.

## 처리 규칙

- `T ≤ server_received_at < T+30`인 경우에만 새 제출을 허용한다.
- 취소·종료 약속은 새 제출을 허용하지 않는다.
- 같은 약속의 같은 `clientRequestId`와 같은 fingerprint는 기존 attempt를 그대로 반환한다.
- 같은 `clientRequestId`에 다른 fingerprint를 보내면 `PHOTO_REQUEST_KEY_REUSED` 충돌이다.
- 원자적 DB 제약으로 약속당 `Evaluating` attempt를 하나만 허용한다.
- 사진 원문은 DB나 로그에 저장하지 않는다. 현재 증분은 `photo_asset`에 저장 키·MIME·크기·SHA-256·수신시각만 저장한다.

## 응답 및 오류

정상 응답은 `attemptId`, `promiseId`, `processingStatus`, `receivedAt`을 반환하며 신규 attempt의 상태는 `Evaluating`이다.

| HTTP | code | 조건 |
|---|---|---|
| 400 | `PHOTO_REQUIRED` | 파일이 없거나 비어 있음 |
| 400 | `TOOL_TYPE_INVALID` | tool type이 비어 있거나 32자를 초과 |
| 401 | `ACCESS_REQUIRED` / `SESSION_REPLACED` | 활성 세션이 없음 |
| 404 | `RESOURCE_NOT_FOUND` | 약속이 참가자 소유가 아님 |
| 409 | `PHOTO_WINDOW_CLOSED` | T 이전 또는 T+30 이상 |
| 409 | `PROMISE_NOT_ACTIVE` | 취소·종료 약속 |
| 409 | `PHOTO_REQUEST_KEY_REUSED` | 같은 요청 키에 다른 내용 |
| 409 | `PHOTO_EVALUATION_IN_PROGRESS` | 동시 Evaluating attempt 존재 |

## 검증 범위

- 단위 테스트: 동일 입력 fingerprint 안정성, MIME/tool type 변경 시 fingerprint 변경.
- DB 통합 테스트: V2/V3 포함 Flyway 마이그레이션, 사진 메타데이터 연결, 실제 multipart 중복 제출을 Docker/Testcontainers로 검증했다.
- AI adapter, 객체 저장소 업로드·삭제, 판정 결과 반영, 지연 결과·철회 보호는 SCRUM-84·85·86에서 검증한다.
## Delayed result guard

An evaluation result may be stored after the T+30 submission window only when the attempt has a server-stored valid receipt in `scheduled_at <= valid_received_at < scheduled_at + 30 minutes`. Missing or out-of-window receipts, non-current `Evaluating` attempts, and withdrawn participants are rejected with a conflict. This does not authorize a new photo submission after T+30.
