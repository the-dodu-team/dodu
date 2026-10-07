# 공통 API 응답·오류 계약 (초안)

- 상태: 초안. mono의 계약 리뷰 전이며 확정된 제품 API 명세가 아닙니다.
- 작업: SCRUM-7
- 작성: 문은서 / 기술 리뷰: mono / 예시 확인: Elle Yoo
- 근거: `docs/dodu/sources/SERVICE_POLICY_v2.5.md` §0·§8, `docs/dodu/sources/FLOW_LOG_MAPPING_v2.5.md` §3~4, `docs/DEVELOPMENT_CONVENTIONS.md` "API·시간·상태 계약"

## 1. 범위

| 이 문서가 정하는 것 | 이 문서가 정하지 않는 것 |
|---|---|
| 성공·오류 응답 형식 | 개별 엔드포인트의 경로·필드 (화면·정책 확정 후 별도 명세) |
| 오류 유형·HTTP 상태 매핑·코드 규칙 | 판정 결과 전달 방식, 상태 전이 경계 (OPEN, 6장) |
| 시간 표현 규칙 | DB 구조 (SCRUM-10) |
| 미정 항목 표시 방법 | 참가자 식별·접근 제어 상세 (SCRUM-12) |

## 2. 현재 구현과 제안의 구분

| 구분 | 내용 |
|---|---|
| 구현됨 | `GET /api/health` — 로컬에서 호출해 확인한 응답: `{"service":"dodu-backend","status":"ok"}`. 이 계약의 `status`/`data`/`serverTime` 형식과 다르고, `status` 값도 `ok`입니다. 계약에 맞출지는 mono 확인 사항입니다 (7장) |
| 제안 | 이 문서의 모든 형식. 컨벤션의 작성 기준에 맞춘 제안이며 확정된 제품 API가 아닙니다 |

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

| 코드 | type | HTTP | 근거 | 비고 |
|---|---|---|---|---|
| `PROMISE_SCHEDULE_IN_PAST` | VALIDATION | 400 | 정책 §1 | 과거 시간 |
| `PROMISE_SCHEDULE_TOO_SOON` | VALIDATION | 400 | 정책 §1 | 최소 예약 시점 미달. 경계 포함 여부는 D11 대기 |
| `PROMISE_SCHEDULE_TOO_FAR` | VALIDATION | 400 | 정책 §1 | 최대 예약 범위 초과. 경계 포함 여부는 D11 대기 |
| `PROMISE_SCHEDULE_AFTER_STUDY_END` | VALIDATION | 400 | 정책 §1 | 행동검증 종료 이후 예약 |
| `DATETIME_OFFSET_REQUIRED` | VALIDATION | 400 | 본 문서 4장 | 오프셋 없는 시각 요청 |
| `PROMISE_ACTIVE_EXISTS` | CONFLICT | 409 | 정책 §1 | 활성 약속 1개 제한 |
| `PROMISE_DATE_ALREADY_USED` | CONFLICT | 409 | 정책 §1 | 동일 예정 시작일(KST) 1개 제한. 취소해도 복구하지 않음. 날짜 수정·기술 실패 후 재예약에 적용하는 범위는 D06 대기 |
| `PROMISE_RESCHEDULE_LIMIT_REACHED` | CONFLICT | 409 | 정책 §2 | 정규 수정 1회 |
| `PROMISE_NOT_MODIFIABLE` | CONFLICT | 409 | 정책 §2 | 시작 시간 도달 후 수정·취소 불가. 경계는 D11 대기 |
| `ACCESS_REQUIRED` | UNAUTHENTICATED | 401 | 컨벤션 | 상세는 SCRUM-12 |
| `ACCESS_FORBIDDEN` | FORBIDDEN | 403 | 컨벤션 | 타인 데이터 접근 차단. 상세는 SCRUM-12 |
| `RESOURCE_NOT_FOUND` | NOT_FOUND | 404 | 컨벤션 | |
| `SERVICE_UNAVAILABLE` | TECHNICAL | 503 | 정책 §8.1 | 서버 오류 |
| `INTERNAL_ERROR` | TECHNICAL | 500 | 정책 §8.1 | 내부 처리·상태 저장 실패 |
| `PHOTO_UPLOAD_FAILED` | TECHNICAL | 500 | 정책 §8.1 | 사진 업로드 실패 |
| `JUDGMENT_TIMEOUT` | TECHNICAL | 504 | 정책 §8.1 | 이미지 판정 시간초과 |

- 위 목록은 초안이며, 엔드포인트별로 쓰는 코드는 개별 명세에서 정합니다.
- 카메라·알림 권한 거부는 브라우저 상태라서 서버 오류 코드로 만들지 않습니다. 판정거절·기술오류와 같은 것으로 취급하지 않습니다 (정책 §7.3).

## 4. 시간 표현 규칙

1. 모든 시각은 오프셋이 포함된 ISO 8601입니다. 오프셋 없는 시각은 요청·응답 모두 사용하지 않습니다. 오프셋 없는 요청은 `DATETIME_OFFSET_REQUIRED`로 거절합니다.
2. 서버 응답은 `Asia/Seoul`(`+09:00`)로 직렬화합니다. 컨벤션은 UTC/오프셋 포함 형식을 모두 허용하므로 둘 중 하나로 정하는 것이 이 제안의 요점입니다.
3. KST 일자가 필요한 값은 서버가 계산해 `...DateKst`(`YYYY-MM-DD`)로 내려줍니다. 클라이언트가 직접 계산하지 않습니다.
4. 서버 시간과 저장 상태가 최종 판단 기준입니다. 브라우저 타이머는 안내용입니다. 모든 응답에 `serverTime`을 넣어 FE가 남은 시간을 서버 기준으로 계산하게 합니다. (`serverTime`은 제안입니다.)
5. `Clock` 주입과 KST 변환의 기술 방향은 TIME_CONTRACT.md 에서 구체화합니다.

## 5. 예시

FE 검토용 예시이며, 약속 생성 API(`POST /api/promises`, 경로는 가칭)를 기준으로 합니다. 값은 모두 가상 데이터입니다.

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

경계에 걸리지 않는 값(요청 시각보다 10분 뒤)을 사용합니다.

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

## 6. 미정 항목 (OPEN)

OPEN 항목은 승인된 정책이 아닙니다. 이 계약에서는 자리만 표시하고, 기대 결과를 예시나 테스트의 정답으로 고정하지 않습니다.

| 항목 | 관련 D-ID | 이 계약에서의 처리 |
|---|---|---|
| 예약 끝점 비교 (정확히 최소·최대 예약 시점인 경우) | D11 | 오류 코드만 정의하고 경계 포함 여부는 정하지 않음 |
| 시작 시간 도달 시점의 수정·취소 가능 여부 | D11 (확인 필요) | `PROMISE_NOT_MODIFIABLE`의 경계는 정하지 않음 |
| 유효 서버 접수의 정의 (인증 마감 경계) | D11 | 정하지 않음. 예시에서 제외 |
| 마감 직전 기술오류의 마감 후 복구, 판정 대기 결과, 판정 중 중복 제출 | D04, D10 | 정하지 않음 |
| 여러 사진 시도의 연결키·중복 제거 | D08 | 정하지 않음 |
| 철회·권한 부재·판정 외 오류의 상태 전이 | D05 | 정하지 않음. 상태 전이는 별도 계약 |
| 날짜 수정 시 원래 날짜의 생성 제한, 기술 실패 후 당일 재예약 | D06 | `PROMISE_DATE_ALREADY_USED`의 적용 범위를 정하지 않음 |

D-ID 대응은 `docs/dodu/OPEN_DECISIONS.md`(D01~D13, 모두 OPEN)와 대조했습니다. 'T 시점 수정·취소'는 D11의 근거 범위(정책 §1~2, §5)에 속하지만 문서가 정확히 T인 경우를 명시하지는 않아서 mono와 확인이 필요합니다.


## 7. 변경 이력

| 날짜 | 변경 | 작성 |
|---|---|---|
| 2026-10-06 | 초안 작성 | 문은서 |