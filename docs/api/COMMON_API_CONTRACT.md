# 공통 API 응답·오류 계약 (초안)

- 상태: 초안. 강민님의 계약 리뷰 전이며 확정된 제품 API 명세가 아닙니다.
- 작업: SCRUM-7 (이전 DODU-16, 로컬 S1-BE01), 분할 초안 SPLIT-16-1
- 작성: 문은서 / 기술 리뷰: 강민님 / 예시 확인: 연정님
- 근거: `docs/dodu/sources/SERVICE_POLICY_v2.5.md` §0·§8, `docs/dodu/sources/FLOW_LOG_MAPPING_v2.5.md` §3~4, `docs/dodu/OPEN_DECISIONS.md`(D01~D13, 2026-09-27 APPROVED), `docs/DEVELOPMENT_CONVENTIONS.md` "API·시간·상태 계약"

## 1. 범위

| 이 문서가 정하는 것 | 이 문서가 정하지 않는 것 |
|---|---|
| 성공·오류 응답 형식 | 개별 엔드포인트의 경로·필드 (화면·정책 확정 후 별도 명세) |
| 오류 유형·HTTP 상태 매핑·코드 규칙 | 판정 결과 전달 방식, 상태 전이표, 멱등키·retry 식별 방식 (별도 계약, 7장 Q4) |
| 시간 표현 규칙 | DB 구조 (`docs/dodu/DATA_MODEL.md`, SCRUM-10) |
| 확정된 결정이 오류 코드에 반영되는 방식 | 참가자 식별·접근 제어 상세 (SCRUM-12) |

## 2. 현재 구현과 제안의 구분

| 구분 | 내용 |
|---|---|
| 구현됨 | `GET /api/health` — 로컬에서 호출해 확인한 응답: `{"service":"dodu-backend","status":"ok"}`. 이 계약의 `status`/`data`/`serverTime` 형식과 다르고, `status` 값도 `ok`입니다. 계약에 맞출지는 강민님 확인 사항입니다 (7장 Q6) |
| 구현됨 (도메인 정책) | `com.dodu.intervention` 패키지의 `InterventionWindowPolicy` 등(SCRUM-64). 예약·제출 시각 판정을 결정값(`TOO_EARLY`, `TOO_LATE`, `PAST_INTERVENTION_END`, `BEFORE_START`, `WINDOW_EXPIRED`)으로 돌려주며, 아직 API에 연결되지 않았습니다 |
| 제안 | 이 문서의 모든 응답 형식과 오류 코드 이름. 컨벤션의 작성 기준에 맞춘 제안이며 확정된 제품 API가 아닙니다 |

## 3. 응답 형식

### 3.1 성공

```json
{
  "status": "success",
  "data": {},
  "serverTime": "2026-10-06T14:02:11+09:00"
}
```

### 3.2 오류

```json
{
  "status": "error",
  "error": {
    "type": "VALIDATION",
    "code": "PROMISE_SCHEDULE_TOO_SOON",
    "field": "scheduledAt",
    "message": "사용자에게 보여도 안전한 문구",
    "retryable": false
  },
  "serverTime": "2026-10-06T14:02:11+09:00"
}
```

- `field`는 입력 오류에서 문제가 된 항목을 가리킬 때만 포함합니다.
- `retryable`은 TECHNICAL 오류 응답에만 넣습니다. 같은 요청을 그대로 다시 보내면 성공할 수 있는지를 알려주는 값이며, 약속이 기술 실패로 종료되었는지(TechFailed)와는 별개입니다.
### 3.3 오류 유형과 HTTP 상태

| `error.type` | HTTP | 의미 | 사용자 미인증으로 집계 |
|---|---|---|---|
| `VALIDATION` | 400 | 입력 오류 | 해당 없음 |
| `UNAUTHENTICATED` | 401 | 인증 정보 없음 | 해당 없음 |
| `FORBIDDEN` | 403 | 권한 부족 | 해당 없음 |
| `NOT_FOUND` | 404 | 자원 없음 | 해당 없음 |
| `CONFLICT` | 409 | 상태 충돌 | 해당 없음 |
| `TECHNICAL` | 5xx | 서비스·판정 시스템 문제 | **아니오** (정책 §8.2) |

- 기술오류를 사용자 미인증·인증 미완료로 기록하지 않습니다.
- FE는 `error.type`과 `error.code`로 분기합니다. `message` 문구로 분기하지 않습니다.

### 3.4 오류 코드와 메시지 규칙

- 코드는 `UPPER_SNAKE_CASE`, `<영역>_<상황>` 형태입니다. 코드는 FE가 분기에 쓰므로 변경은 계약 변경으로 보고 FE·BE가 함께 확인합니다.
- 메시지에는 사용자에게 보여도 안전한 문구만 넣습니다. 스택, 접근정보, 사진 원문 데이터는 넣지 않습니다.
- 정책 수치(허용 범위 등)는 행동검증 시작 전까지 조정될 수 있어서 메시지에 하드코딩하지 않습니다.

### 3.5 오류 코드 초안

"근거"에 D-ID가 있는 경계 규칙은 `OPEN_DECISIONS.md`에서 2026-09-27 확정된 내용입니다. 코드 이름은 모두 제안입니다.

| 코드 | type | HTTP | 근거 | 비고 |
|---|---|---|---|---|
| `PROMISE_SCHEDULE_TOO_SOON` | VALIDATION | 400 | D11 | 예약 시각이 서버 시각 +5분 미만(과거 포함). 정확히 +5분은 허용. 도메인 결정 `TOO_EARLY`에 대응 |
| `PROMISE_SCHEDULE_TOO_FAR` | VALIDATION | 400 | D11 | 서버 시각 +72시간 초과. 정확히 +72시간은 허용. `TOO_LATE`에 대응 |
| `PROMISE_SCHEDULE_AFTER_STUDY_END` | VALIDATION | 400 | D03 | T+30이 공식 행동검증 개입 종료시각을 넘음(`T+30 ≤ 종료시각`만 허용). `PAST_INTERVENTION_END`에 대응 |
| `DATETIME_OFFSET_REQUIRED` | VALIDATION | 400 | 본 문서 4장 | 오프셋 없는 시각 요청 |
| `PROMISE_ACTIVE_EXISTS` | CONFLICT | 409 | 정책 §1 | 활성 약속 1개 제한 |
| `PROMISE_DATE_ALREADY_USED` | CONFLICT | 409 | 정책 §1, D06 | 같은 KST 예정 시작일 제한. 취소 후 같은 날짜 재생성은 최대 1회, 기술실패 복구 재예약은 별도 최대 1회 |
| `PROMISE_RESCHEDULE_LIMIT_REACHED` | CONFLICT | 409 | 정책 §2, D06 | T 이전 수정 최대 1회 |
| `PROMISE_NOT_MODIFIABLE` | CONFLICT | 409 | 정책 §2 | 시작 시간 도달 이후 수정·취소 불가. 정확히 T인 순간의 처리는 문서에 명시가 없어 확인 필요 (7장 Q2) |
| `AUTH_NOT_STARTED` | CONFLICT | 409 | D11 | 사진 제출 접수 시각이 T 이전. `BEFORE_START`에 대응 |
| `AUTH_WINDOW_EXPIRED` | CONFLICT | 409 | D04, D11 | 접수 시각이 T+30 이상이라 신규 제출 불가. `WINDOW_EXPIRED`에 대응 |
| `AUTH_EVALUATION_IN_PROGRESS` | CONFLICT | 409 | D10 | 약속당 동시 Evaluating 사진은 최대 1개. 판정 중 추가 제출 차단 |
| `STUDY_PERIOD_ENDED` | CONFLICT | 409 | D03 | 공식 행동검증 개입 종료 이후의 신규 약속·수정·재생성·기술복구·알림·개입 |
| `ACCESS_REQUIRED` | UNAUTHENTICATED | 401 | 컨벤션 | 상세는 SCRUM-12 |
| `ACCESS_FORBIDDEN` | FORBIDDEN | 403 | 컨벤션 | 타인 데이터 접근 차단. 상세는 SCRUM-12 |
| `RESOURCE_NOT_FOUND` | NOT_FOUND | 404 | 컨벤션 | |
| `SERVICE_UNAVAILABLE` | TECHNICAL | 503 | 정책 §8.1 | 서버 오류 |
| `INTERNAL_ERROR` | TECHNICAL | 500 | 정책 §8.1 | 내부 처리·상태 저장 실패 |
| `PHOTO_UPLOAD_FAILED` | TECHNICAL | 500 | 정책 §8.1 | 사진 업로드 실패 |
| `JUDGMENT_TIMEOUT` | TECHNICAL | 504 | 정책 §8.1 | 이미지 판정 시간초과 |

- 위 목록은 초안이며, 엔드포인트별로 쓰는 코드는 개별 명세에서 정합니다.
- 카메라·알림 권한 거부는 브라우저 상태라서 서버 오류 코드로 만들지 않습니다. 판정거절·기술오류와 같은 것으로 취급하지 않습니다 (정책 §7.3, D05).
- `PROMISE_SCHEDULE_IN_PAST`는 두지 않습니다. 과거 시각은 서버 시각 +5분 미만이라 `PROMISE_SCHEDULE_TOO_SOON`에 포함됩니다.

## 4. 시간 표현 규칙

1. 모든 시각은 오프셋이 포함된 ISO 8601입니다. 오프셋 없는 시각은 요청·응답 모두 사용하지 않습니다. 오프셋 없는 요청은 `DATETIME_OFFSET_REQUIRED`로 거절합니다.
2. 서버 응답은 `Asia/Seoul`(`+09:00`)로 직렬화합니다. 컨벤션은 UTC/오프셋 포함 형식을 모두 허용하므로 둘 중 하나로 정하는 것이 이 제안의 요점입니다. `2026-10-07T20:00:00+09:00`은 `2026-10-07T11:00:00Z`와 같은 시점입니다. **[강민님 확인]**
3. KST 일자가 필요한 값은 서버가 계산해 `...DateKst`(`YYYY-MM-DD`)로 내려줍니다. 클라이언트가 직접 계산하지 않습니다.
4. 서버 시간과 저장 상태가 최종 판단 기준입니다. 브라우저 타이머는 안내용입니다. 모든 응답에 `serverTime`을 넣어 FE가 남은 시간을 서버 기준으로 계산하게 합니다. (`serverTime`은 제안입니다.)
5. `Clock` 주입과 KST 변환의 기술 방향은 `TIME_CONTRACT.md`에서 구체화합니다.
6. 저장은 UTC 순간으로 하고 KST 날짜가 필요한 레코드에는 별도 날짜를 보존하는 것이 `docs/dodu/DATA_MODEL.md`의 설계입니다. 응답 직렬화(위 2번)와는 별개입니다.

## 5. 예시

FE 검토용 예시이며, 약속 생성 API(`POST /api/promises`, 경로는 가칭)를 기준으로 합니다. 값은 모두 가상 데이터입니다. 같은 예시를 `fixtures/` 폴더에 JSON 파일로 두었습니다.

### 5.1 정상 (201)

```json
{
  "status": "success",
  "data": {
    "promiseId": "00000000-0000-4000-8000-000000000001",
    "lifecycleStatus": "Scheduled",
    "taskTitle": "예시 작업",
    "toolType": "COMPUTER",
    "scheduledAt": "2026-10-07T20:00:00+09:00",
    "scheduledDateKst": "2026-10-07",
    "authWindowEndsAt": "2026-10-07T20:30:00+09:00"
  },
  "serverTime": "2026-10-06T14:02:11+09:00"
}
```

### 5.2 입력오류 (400)

최소 예약 시점(서버 시각 +5분)에 못 미치는 값(예: 요청 시각보다 2분 뒤)을 사용합니다. 경계값(정확히 +5분)은 쓰지 않습니다.

```json
{
  "status": "error",
  "error": {
    "type": "VALIDATION",
    "code": "PROMISE_SCHEDULE_TOO_SOON",
    "field": "scheduledAt",
    "message": "시작 시간을 지금보다 조금 더 뒤로 설정해주세요."
  },
  "serverTime": "2026-10-06T14:02:11+09:00"
}
```

### 5.3 기술오류 (503)

```json
{
  "status": "error",
  "error": {
    "type": "TECHNICAL",
    "code": "SERVICE_UNAVAILABLE",
    "message": "일시적인 오류가 발생했어요. 잠시 후 다시 시도해주세요.",
    "retryable": true
  },
  "serverTime": "2026-10-06T14:02:11+09:00"
}
```

## 6. 확정된 결정

`docs/dodu/OPEN_DECISIONS.md`의 D01~D13은 2026-09-27에 모두 APPROVED입니다. 이 계약은 아래 결정을 오류 코드와 시간 규칙에 반영하고, 확정되지 않은 부분은 7장에서 확인을 요청합니다.

| D-ID | 확정 내용 (요약) | 이 계약에서의 처리 |
|---|---|---|
| D03 | `T+30 ≤ 공식 종료시각`, 종료 후 신규 개입 중단 | `PROMISE_SCHEDULE_AFTER_STUDY_END`, `STUDY_PERIOD_ENDED` |
| D04 | T+30은 신규 제출 마감, 그 전 접수는 이후 판정 가능 | `AUTH_WINDOW_EXPIRED`. 판정 결과 전달 방식은 별도 계약 |
| D05 | AuthCompleted/AuthIncomplete와 Cancelled/TechFailed/권한 문제/Withdrawn을 분석상 분리 | 기술오류를 미인증으로 집계하지 않음(3.3). 상태 전이표는 별도 계약 |
| D06 | T 이전 수정 최대 1회, 취소 후 같은 KST 날짜 재생성 최대 1회, 기술실패 복구 재예약 별도 최대 1회 | `PROMISE_RESCHEDULE_LIMIT_REACHED`, `PROMISE_DATE_ALREADY_USED` |
| D08 | 약속·attempt·행동 이벤트 분리, 네트워크 retry는 같은 attempt, 새 사진은 새 attempt | 멱등키·retry 식별 방식은 별도 계약 (SCRUM-64의 요청 ID/fingerprint 방식 참고) |
| D10 | 약속당 동시 Evaluating 사진 최대 1개 | `AUTH_EVALUATION_IN_PROGRESS` |
| D11 | 예약 `now+5분 ≤ T ≤ now+72시간`, 제출 `T ≤ 접수시각 < T+30` | `PROMISE_SCHEDULE_TOO_SOON`, `PROMISE_SCHEDULE_TOO_FAR`, `AUTH_NOT_STARTED`, `AUTH_WINDOW_EXPIRED` |

## 7. 확인 필요 사항 (강민님)

아래는 이 PR에서 정하지 않고 확인을 요청하는 항목입니다. "이 문서의 기본 처리"는 답을 받기 전까지 문서가 임시로 따르는 기준이며, 확정 정책이 아닙니다. 답을 받으면 해당 문서와 fixture를 같은 PR에서 고치고, 구현은 답을 받은 뒤 시작합니다.

| ID | 확인 사항 | 근거 | 이 문서의 기본 처리 | 답에 따라 바뀌는 곳 |
|---|---|---|---|---|
| Q1 | 최소 예약 시점이 `+30분`(정책 §1·§2)인지 `+5분`(D11, `InterventionWindowPolicy`)인지, 정책 문서를 갱신할지 | `SERVICE_POLICY_v2.5.md` §1·§2, D11 | D11의 `+5분` | `PROMISE_SCHEDULE_TOO_SOON` 설명, 시간 계약 7장 예시 |
| Q2 | 서버 시각이 정확히 T인 순간에 수정·취소가 되는지 | D06 "T 이전", 정책 §2 "시작시간 도달 이후 불가" (정확히 T는 명시 없음) | 정하지 않음. `PROMISE_NOT_MODIFIABLE`의 경계로 고정하지 않음 | `PROMISE_NOT_MODIFIABLE` 경계, 수정·취소 API 비교 연산(`<` / `≤`) |
| Q3 | `DEVELOPMENT_CONVENTIONS.md`의 "예약 끝점 비교·유효 서버 접수의 정의는 D11 승인 전 임의로 확정하지 않습니다" 문장을 D11 확정에 맞게 고칠지 | D11 APPROVED (2026-09-27) | D11을 확정으로 취급 | 컨벤션 문서 |
| Q4 | 상태 전이표, 판정 결과 전달 방식, 멱등키·retry 식별 방식을 어느 티켓에서 계약으로 정할지 | D05, D08, SCRUM-64의 요청 ID/fingerprint | 이 문서 범위 밖으로 둠 | 계약 1장 범위, fixture 추가 사례 |
| Q5 | 응답 시각을 `+09:00`으로 할지 UTC(`Z`)로 할지 | `DATA_MODEL.md`는 UTC 저장 | `+09:00` (제안) | 계약 3·4·5장, fixture, 시간 계약 3장 |
| Q6 | 기존 `GET /api/health` 응답을 이 계약 형식에 맞출지, 운영 점검용 예외로 둘지 | 현재 `{"service":"dodu-backend","status":"ok"}` | 맞추지도 예외로 두지도 않고 2장에 차이만 기록 | 계약 2장, `HealthController` |
| Q7 | 다른 기기 진입으로 세션이 종료된 경우를 `UNAUTHENTICATED` 안의 별도 오류 코드(예: `SESSION_REPLACED`)로 구분할지. 코드 재발급 시 기존 세션·기기·알림 구독 처리는 SCRUM-22에서 정하며, 그때 `CREDENTIAL_REVOKED` 같은 코드가 필요한지 함께 결정 | `DATA_MODEL.md` 규칙 11~13, `DATA_MODEL_GUIDE.md` 참가자 코드 정책 | 3.5 코드 목록에 넣지 않음 | 3.5 코드 목록, FE의 세션 종료 안내 |


## 8. 변경 이력

| 날짜 | 변경 | 작성 |
|---|---|---|
| 2026-10-07 | 초안 작성 | 문은서 |
| 2026-10-07 | 최신 `dev`의 확정 결정(D01~D13, 2026-09-27)과 `InterventionWindowPolicy` 반영. OPEN 표를 확정 결정 표로 교체, 최소 예약 시점 +5분(D11), 오류 코드 보강 | 문은서 |
| 2026-10-08 | 확인이 필요한 항목을 7장 별도 섹션(Q1~Q7)으로 분리, 세션 종료·재발급 오류 코드 확인 항목 추가 | 문은서 |