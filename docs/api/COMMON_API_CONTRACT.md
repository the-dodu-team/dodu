# 공통 API 응답·오류 계약 (초안)

- 상태: 초안. 신규 API를 만들 때 쓰는 작성 기준이며 확정된 제품 API 명세가 아닙니다. 7장에서 확정 결정에 근거한 내용과 기술 제안을 구분하며, 기술 제안은 확정 정책이 아닙니다.
- 작업: SCRUM-7 (이전 DODU-16, 로컬 S1-BE01), 분할 초안 SPLIT-16-1
- 작성: 문은서
- 근거: `docs/dodu/sources/SERVICE_POLICY_v2.5.md` §0·§8, `docs/dodu/sources/FLOW_LOG_MAPPING_v2.5.md` §3~4, `docs/dodu/OPEN_DECISIONS.md`(D01~D13, 2026-09-27 APPROVED), `docs/DEVELOPMENT_CONVENTIONS.md` "API·시간·상태 계약"

## 1. 범위

| 이 문서가 정하는 것 | 이 문서가 정하지 않는 것 |
|---|---|
| 성공·오류 응답 형식 | 개별 엔드포인트의 경로·필드 (화면·정책 확정 후 별도 명세) |
| 오류 유형·HTTP 상태 매핑·코드 규칙 | 판정 결과 전달 방식, 상태 전이표, 멱등키·retry 식별 방식 (별도 계약. 기존 티켓에 연결, 7장 Q4) |
| 시간 표현 규칙 | DB 구조 (`docs/dodu/DATA_MODEL.md`, SCRUM-10) |
| 확정된 결정이 오류 코드에 반영되는 방식 | 참가자 식별·접근 제어 상세 (SCRUM-12) |

## 2. 현재 구현과 제안의 구분

| 구분 | 내용 |
|---|---|
| 구현됨 | `GET /api/health` — 로컬에서 호출해 확인한 응답: `{"service":"dodu-backend","status":"ok"}`. 이 계약의 `status`/`data`/`serverTime` 형식과 다르고, `status` 값도 `ok`입니다. **운영 점검용 예외로 유지**하며 이 계약은 신규 업무 API에 적용합니다. 이 문서 작업에서는 `HealthController`를 바꾸지 않습니다 (강민님 기술 제안, 7장 Q6) |
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
    "message": "사용자에게 보여도 안전한 문구"
  },
  "serverTime": "2026-10-06T14:02:11+09:00"
}
```

- `field`는 입력 오류에서 문제가 된 항목을 가리킬 때만 포함합니다.
- `retryable`은 TECHNICAL 오류 응답에만 넣습니다. 같은 요청을 그대로 다시 보내면 성공할 수 있는지를 알려주는 값이며, 약속이 기술 실패로 종료되었는지(TechFailed)와는 별개입니다.
- `retryable: true`는 같은 요청을 다시 보내도 되는 오류라는 표시일 뿐, FE가 자동으로 재시도해도 된다는 허가가 아닙니다. 재시도 횟수·간격·자동 여부는 별도로 정합니다. 입력 오류(VALIDATION) 응답에는 `retryable`을 넣지 않습니다.

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
| `PROMISE_NOT_MODIFIABLE` | CONFLICT | 409 | 정책 §2, D06 | 서버 시각이 기존 시작 시각 T 이전일 때만 수정·취소 가능. **정확히 T 이후는 불가**(D06 확정). 코드 이름과 409는 기술 제안. 사진 제출은 정확히 T가 허용이라 경계가 반대임 (7장 Q2) |
| `AUTH_NOT_STARTED` | CONFLICT | 409 | D11 | 사진 제출 접수 시각이 T 이전. `BEFORE_START`에 대응 |
| `AUTH_WINDOW_EXPIRED` | CONFLICT | 409 | D04, D11 | 접수 시각이 T+30 이상이라 신규 제출 불가. `WINDOW_EXPIRED`에 대응 |
| `AUTH_EVALUATION_IN_PROGRESS` | CONFLICT | 409 | D10 | 약속당 동시 Evaluating 사진은 최대 1개. 판정 중 추가 제출 차단 |
| `STUDY_PERIOD_ENDED` | CONFLICT | 409 | D03 | 공식 행동검증 개입 종료 이후의 신규 약속·수정·재생성·기술복구·알림·개입 |
| `ACCESS_REQUIRED` | UNAUTHENTICATED | 401 | 컨벤션 | 상세는 SCRUM-12 |
| `SESSION_REPLACED` | UNAUTHENTICATED | 401 | 기술 제안(강민님) | 다른 기기 진입으로 세션이 종료됨. 서버가 종료 사유를 검증할 수 있을 때만 사용. 코드 이름 미확정 (7장 Q7) |
| `CREDENTIAL_REVOKED` | UNAUTHENTICATED | 401 | 기술 제안(강민님) | 참가자 코드 폐기·재발급. 서버가 사유를 검증할 수 있을 때만 사용. 재발급 시 이전 자격으로 발급된 세션을 무효화할지는 SCRUM-56에서 검토. 코드 이름 미확정 (7장 Q7) |
| `ACCESS_FORBIDDEN` | FORBIDDEN | 403 | 컨벤션 | 타인 데이터 접근 차단. 상세는 SCRUM-12 |
| `RESOURCE_NOT_FOUND` | NOT_FOUND | 404 | 컨벤션 | |
| `SERVICE_UNAVAILABLE` | TECHNICAL | 503 | 정책 §8.1 | 서버 오류 |
| `INTERNAL_ERROR` | TECHNICAL | 500 | 정책 §8.1 | 내부 처리·상태 저장 실패 |
| `PHOTO_UPLOAD_FAILED` | TECHNICAL | 500 | 정책 §8.1 | 사진 업로드 실패 |
| `JUDGMENT_TIMEOUT` | TECHNICAL | 504 | 정책 §8.1 | 이미지 판정 시간초과 |

- 위 목록은 초안이며, 엔드포인트별로 쓰는 코드는 개별 명세에서 정합니다.
- 카메라·알림 권한 거부는 브라우저 상태라서 서버 오류 코드로 만들지 않습니다. 판정거절·기술오류와 같은 것으로 취급하지 않습니다 (정책 §7.3, D05).
- `SESSION_REPLACED`·`CREDENTIAL_REVOKED`는 사유를 서버가 확인할 수 없으면 쓰지 않고 일반 인증 오류(`ACCESS_REQUIRED`)로 응답합니다. 코드나 참가자의 존재 여부를 응답으로 드러내지 않기 위해서입니다. 이 두 코드의 응답에는 원문 코드·토큰·새 기기 정보를 넣지 않습니다.
- 참가자 코드 IP 임시 차단(SCRUM-70)의 429·Retry-After와 오류 유형·코드는 후속 인증 계약에서 보완합니다. 사진 재시도와는 구분하며, 코드 이름은 정하지 않았습니다.
- `PROMISE_SCHEDULE_IN_PAST`는 두지 않습니다. 과거 시각은 서버 시각 +5분 미만이라 `PROMISE_SCHEDULE_TOO_SOON`에 포함됩니다.

## 4. 시간 표현 규칙

1. 모든 시각은 오프셋이 포함된 ISO 8601입니다. 오프셋 없는 시각은 요청·응답 모두 사용하지 않습니다. 오프셋 없는 요청은 `DATETIME_OFFSET_REQUIRED`로 거절합니다.
2. 서버 응답은 `Asia/Seoul`(`+09:00`)로 직렬화합니다. 컨벤션은 UTC/오프셋 포함 형식을 모두 허용하므로 둘 중 하나로 정하는 것이 이 제안의 요점이며, 이 초안은 `+09:00`으로 작성합니다(기술 제안이며 제품 정책이 아닙니다. 확정은 강민님 재검토와 FE/BE 합의 후입니다). `2026-10-07T20:00:00+09:00`은 `2026-10-07T11:00:00Z`와 같은 시점입니다. UI는 표시 시간대를 `Asia/Seoul`로 명시합니다.
3. KST 일자가 필요한 값은 서버가 계산해 `...DateKst`(`YYYY-MM-DD`)로 내려줍니다. 클라이언트가 직접 계산하지 않습니다.
4. 서버 시간과 저장 상태가 최종 판단 기준입니다. 브라우저 타이머는 안내용이며, 네트워크 지연과 기기 시계 오차 때문에 최종 허용 여부는 서버가 판단합니다. 모든 응답에 `serverTime`을 넣어 FE가 남은 시간을 서버 기준으로 계산하게 합니다. (`serverTime`은 제안입니다.)
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
| D06 | T 이전 수정 최대 1회, 취소 후 같은 KST 날짜 재생성 최대 1회, 기술실패 복구 재예약 별도 최대 1회. 수정·취소는 서버 시각이 T 이전일 때만(정확히 T 불가) | `PROMISE_RESCHEDULE_LIMIT_REACHED`, `PROMISE_DATE_ALREADY_USED`, `PROMISE_NOT_MODIFIABLE` |
| D08 | 약속·attempt·행동 이벤트 분리, 네트워크 retry는 같은 attempt, 새 사진은 새 attempt | 멱등키·retry 식별 방식은 별도 계약 (SCRUM-64의 요청 ID/fingerprint 방식 참고) |
| D10 | 약속당 동시 Evaluating 사진 최대 1개 | `AUTH_EVALUATION_IN_PROGRESS` |
| D11 | 예약 `now+5분 ≤ T ≤ now+72시간`, 제출 `T ≤ 접수시각 < T+30` | `PROMISE_SCHEDULE_TOO_SOON`, `PROMISE_SCHEDULE_TOO_FAR`, `AUTH_NOT_STARTED`, `AUTH_WINDOW_EXPIRED` |

### 정책 원문과 다른 점

`docs/dodu/sources/SERVICE_POLICY_v2.5.md` §1·§2의 최소 예약 시점 `+30분`은 이전 기준이고, 2026-09-27에 확정된 D11·D06(`+5분`)이 우선합니다. 정책 원문 파일은 수정하지 않고, 이 계약과 시간 계약에 차이를 기록합니다.

## 7. 확인 결과와 남은 확인 (강민님)

강민님의 검토 답변입니다. "확정"은 이미 확정된 결정(D06·D11 등)에 근거한 답이고, "기술 제안"은 확정 정책이 아니라 신규 API 작성을 위한 제안입니다. 기술 제안은 확정으로 취급하지 않습니다. Q4~Q7의 기술 제안은 강민님 재검토와 FE/BE 합의 전에는 확정으로 표시하지 않습니다.

| ID | 확인 사항 | 답변 요약 | 구분 | 문서 반영 |
|---|---|---|---|---|
| Q1 | 최소 예약 시점 `+30분` vs `+5분` | `+5분`이 맞고 `+30분`은 이전 기준(`+5분`은 미결정 항목이 아님). 생성·수정 모두 서버 현재 시각 +5분 이상, +72시간 이하(양 끝 허용)이며 D03의 `T+30 ≤ 공식 종료` 조건도 함께 충족. 정책 원문은 덮어쓰지 않고 차이를 기록하며 파생 문서와 결정 기록을 최신 기준에 맞춤 | 확정 (D11·D06) | 6장 "정책 원문과 다른 점", 시간 계약 8장 |
| Q2 | 정확히 T인 순간의 수정·취소 | 불가(서버 시각이 기존 예정 시각 이전이어야 함). `PROMISE_NOT_MODIFIABLE`/409는 제안. 사진 제출 경계와는 다름. 수정은 기존 T의 가능 여부와 새 T의 예약 조건을 따로 검사하고, 실제 저장 트랜잭션에서 상태·횟수·동시 충돌을 다시 확인해야 함 | 확정(불가) + 기술 제안(코드·HTTP) | 3.5, 시간 계약 7장 |
| Q3 | `DEVELOPMENT_CONVENTIONS.md`의 "D11 승인 전" 문장 갱신 | D11이 승인됐으므로 갱신 필요. 컨벤션 문서가 제품 정책을 다시 정의하지 않도록 승인된 D11을 따른다는 문구로 정리 | 확정 (D11 승인 반영) | 같은 변경에서 컨벤션 문서의 해당 문장을 수정 |
| Q4 | 상태 전이표·결과 전달·멱등 계약의 위치 | 새 티켓 없이 기존 티켓에 연결: 약속 CRUD·수정·취소·재생성 전이는 SCRUM-60, 사진 attempt·판정 상태·결과 전달·중복·순서 어긋난 콜백·내부 복구는 SCRUM-64, 이벤트 멱등 저장·집계는 SCRUM-62, 참가자 인증·세션은 SCRUM-56, 자기보고 상태·24시간 응답은 SCRUM-63. 복원된 기존 티켓(SCRUM-22·23 등)과 중복 구현하지 않도록 담당자 간 참조를 정리. 기존 요청 ID·fingerprint·소유권·판정 예약 정책 코드의 완료를 HTTP/DB/다중 서버 통합 완료로 보지 않음. 동일 요청 retry는 같은 attempt, 거절 후 새 사진은 새 attempt. 결과 전달 방식(예: 폴링)은 별도 기술 합의 필요 | 기술 제안 | 1장 범위의 "별도 계약"은 위 티켓의 문서에서 정함 |
| Q5 | 응답 시각 `+09:00` vs UTC | `+09:00` 표기를 일관되게 유지하는 방향. `Z`와 `+09:00`은 같은 시점이며 UTC 저장과 별개이고, 제품 정책으로 표기를 강제하지 않음. `+09:00`만으로 브라우저 시간대 문제가 해결되지는 않으므로 UI는 Asia/Seoul 명시, 업무 날짜는 서버의 `...DateKst`, 타이머는 안내용(최종 허용은 서버). `Clock`의 zone과 `Instant`를 구분하고 UTC `Clock`도 명시적 KST 날짜 변환을 적용하면 같은 정책 결과여야 함. 최종 선택 후 계약·시간 계약·fixture를 함께 맞춤 | 기술 제안 (확정은 강민님 재검토·FE/BE 합의 후) | 4장, 시간 계약 2·3장 |
| Q6 | `GET /api/health` 형식 | 운영 점검용 예외로 유지, 신규 업무 API에만 공통 계약 적용. 이 문서 작업에서는 `HealthController`를 변경하지 않음. 예외 경로의 응답은 업무 API 응답 검증 대상이 아니며, 변경이 필요하면 실제 소비자·점검 도구를 조사한 뒤 별도 작업으로 다룸 | 기술 제안 | 2장 |
| Q7 | 세션 종료·재발급 오류 코드 | `UNAUTHENTICATED`/401 안에서 `SESSION_REPLACED`, `CREDENTIAL_REVOKED`를 구분하되 서버가 사유를 검증할 수 있을 때만 사용. 그렇지 않으면 일반 인증 오류. FE 측 처리(인증 상태·민감 캐시 정리, 재진입 안내, 자동 코드 재입력·무한 재시도 지양)는 검토 요청. 재발급 시 이전 자격으로 발급된 세션 무효화와 이전 알림 구독 비활성화는 SCRUM-56에서 검토. 코드명 미확정 | 기술 제안 | 3.5 |

추가 지적 반영: `retryable: true`는 자동 재시도 허가가 아님(3.2), 입력오류 예시에서 `retryable` 제거(3.2), 429·Retry-After는 후속 인증 계약(SCRUM-70)에서 보완(3.5), fixture는 실제 증거가 아님(fixtures README).

## 8. 변경 이력

| 날짜 | 변경 | 작성 |
|---|---|---|
| 2026-10-07 | 초안 작성 | 문은서 |
| 2026-10-07 | 최신 `dev`의 확정 결정(D01~D13, 2026-09-27)과 `InterventionWindowPolicy` 반영. OPEN 표를 확정 결정 표로 교체, 최소 예약 시점 +5분(D11), 오류 코드 보강 | 문은서 |
| 2026-10-08 | 확인이 필요한 항목을 7장 별도 섹션(Q1~Q7)으로 분리, 세션 종료·재발급 오류 코드 확인 항목 추가 | 문은서 |
| 2026-10-09 | 리뷰 결과 반영: 3.2 예시에서 `retryable` 제거, Q1·Q2 확정 내용 반영, 응답 시각 `+09:00`은 기술 제안으로 표시, 수정 재검증·attempt 구분 등 Q1~Q7 답변 반영, 컨벤션 문서의 "D11 승인 전" 문장 갱신(Q3), 오류 코드 `SESSION_REPLACED`·`CREDENTIAL_REVOKED` 제안 추가, health 예외 명시, 7장을 확인 결과 표로 정리, 별도 계약을 기존 티켓에 연결 | 문은서 |