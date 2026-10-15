# 약속 재생성·기술복구·멱등 처리 — SCRUM-76

- 작성: 강민 / 2026-10-15 / **검토 전 기술 제안**, 실행 API 아님.
- 선행: [약속 CRUD 초안](PROMISE_API_CONTRACT.md), [공통 응답](COMMON_API_CONTRACT.md), [접근 계약](../security/PARTICIPANT_ACCESS_CONTRACT.md).
- 정책 근거: [최신 운영정책](https://app.notion.com/p/3e780115466280188446daadeb64a950) §1.1~1.2·§2.3~2.4·§13, 승인 D03·D04·D06·D08·D09·D11. 지정 기준 페이지는 404여서 하위 전체 최신성은 미확인.

## 1. 세 가지 복구를 분리

| 동작 | 식별자 | 횟수·시간 | 책임 |
|---|---|---|---|
| 취소 후 사용자 재생성 | 새 promiseId, sourcePromiseId로 취소된 약속 연결 | 원래 약속의 **최종 KST 예정 시작일**에 최대1회. 재생성 약속 재수정 불가 | SCRUM-60 강민 |
| TechFailed 후 기술복구 재예약 | 새 promiseId, sourcePromiseId로 기술실패 약속 연결 | 동일 최종 KST 예정 시작일에 별도 최대1회. 사용자 재생성 카운터와 분리 | SCRUM-60 강민 |
| 정상 접수 사진의 내부 재처리 | 같은 promiseId·attemptId | T+30 전에 유효 접수된 시도만 마감 후 내부 처리. 새 약속/새 제출 창을 만들지 않음 | SCRUM-85 강민 |

‘동일 날짜’는 오늘 또는 최초 생성일이 아니다. 수정된 원본은 수정 후 최종 T의 KST 날짜다. 자정 이후 원래 날짜에 유효한 `now+5분` 이상의 시간이 없으면 복구를 다음 날로 옮기지 않는다.

## 2. 파생 약속 요청 제안

| 요청 | 본문 | 성공 |
|---|---|---|
| `POST /api/promises/{sourcePromiseId}/recreations` | `taskTitle`, `toolType`, `scheduledAt`, `expectedSourceVersion` | 201, CRUD와 같은 `data.promise` + `creationType: USER_RECREATION`, `sourcePromiseId` |
| `POST /api/promises/{sourcePromiseId}/technical-recoveries` | 같은 필드 | 201, `creationType: TECHNICAL_RECOVERY`, `sourcePromiseId`. 수정 가능 actions의 제품 의미는 아래 확인 항목 |

`creationType`은 서버가 경로에서 결정하고, 참가자·날짜·횟수·최종 결과는 클라이언트에서 받지 않는다. 일반 생성은 `NORMAL`/sourcePromiseId null을 확장 제안한다. `expectedSourceVersion`은 원본 조회 버전인 비음수 정수다. 원본의 상태 변경이 파생 생성에 의해 발생한다고 가정하지 않는다.

원본은 세션 참가자 소유여야 한다. 타인/없는 원본은 동일404이며, 원본 날짜·결과를 노출하지 않는다. 모든 성공에 새 promiseId를 발급하고 원본을 덮어쓰거나 삭제하지 않는다.

### 저장 시 원자적으로 확인할 조건

1. 현재 세션·참가자·철회·공식 개입 종료를 검사한다. 종료 후 신규 복구는 금지한다.
2. 참가자 및 원본 소유권·expectedSourceVersion, 원본의 최종 KST 일자와 종료 결과를 검사한다. 재생성 원본은 시작 전 Cancelled, 기술복구 원본은 확정 TechFailed여야 한다. 일시적 TECHNICAL HTTP 오류나 Evaluating만으로 기술복구를 열지 않는다.
3. 새 T의 `now+5분 ≤ T ≤ now+72시간`, `T+30분 ≤ 공식 개입 종료`, 원본 최종 KST 날짜와 일치 여부를 검사한다.
4. 참가자의 활성1개, 정상 개입 완료 날짜 조건, 날짜별 해당 카운터 미사용 여부를 확인한다. **요청마다 새 source ID를 바꿔도 날짜별 한도를 우회하지 못한다.**
5. 날짜 카운터 증가·새 약속·원본 연결·생성 이벤트·알림 전달 의도를 함께 저장한다. 실패 시 카운터도 롤백한다. 일반 생성 경로로 재생성 한도를 우회하지 못하게 동일한 날짜 이력을 검사한다.

V1의 `promise_date_usage` 두 카운터와 `promise.source_promise_id`를 재사용한다. 읽은 뒤 별도 UPDATE하는 구조가 아니라 참가자 잠금/조건부 증가 등으로 직렬화한다. 실제 DB 경합 테스트는 SCRUM-60에서 수행하며 V1은 수정하지 않는다. 은서의 V2+ 변경과 조율한다.

동일 날짜 재생성/기술복구는 D09 반복 생성 횟수에 추가하지 않는다. 인증 완료·미완료와 취소·기술실패도 합산하지 않는다. 이 관계는 저장 로그로 보존하되 사용자 작업명과 자격정보를 디버그 로그에 출력하지 않는다.

## 3. 멱등 처리 상세 제안

기술 합의 대상으로 아래를 제안한다. **정책 승인이나 운영 적용 완료가 아니다.** 기존 CRUD 초안의 미정 항목을 이 제안으로 비교 검토하며 자동 retry는 여전히 활성화하지 않는다.

| 항목 | 제안 |
|---|---|
| 적용 | 약속의 일반 생성·수정·취소·재생성·기술복구 쓰기 요청에 `Idempotency-Key` 필수 |
| 키 | 요청 의도마다 클라이언트가 무작위 UUID v4 생성. 네트워크 재전송에서는 같은 키·같은 본문 유지. 코드·제목·참가자 ID를 키에 넣지 않음 |
| 범위 | 세션이 아닌 participant + method + 서버가 정규화한 route/자원ID + key. 새 기기로 바뀌어도 데이터는 동일 participant에 귀속 |
| 본문 비교 | 검증된 DTO의 명시적 필드 직렬화를 fingerprint로 비교. JSON 필드 순서와 오프셋이 다른 동일 순간은 같게 정규화. 제목 정규화는 제목 계약 합의 후 정의 |
| 같은 키·다른 본문 | 409 `IDEMPOTENCY_KEY_REUSED`. 원문·fingerprint를 응답하지 않고 새 작업을 실행하지 않음 |
| 처리 중 | 409 `REQUEST_IN_PROGRESS`. 같은 키로 상태 확인/수동 재요청하도록 안내. 무제한 자동 retry 금지 |
| 성공 재전송 | 현재 세션·철회·소유권 검증 후 기존 결과 재현. 신규 저장·카운터·이벤트·알림 없음. 업무 성공 스냅샷과 HTTP 상태 유지, envelope serverTime은 현재 시각. FE는 현재 상태를 별도 GET으로 재조회 |
| 시간 경계 경과 | 이미 성공한 요청의 재현은 새 개입이 아님. 아직 성공하지 않은 요청은 현재 시간·공식 종료·버전 규칙을 재검증. 성공 재현으로 수정/인증 시간을 되돌리지 않음 |
| 저장 | 결과 기록과 업무 변경을 동일 DB 트랜잭션으로 커밋. 잠금 대기/크래시 후 불완전 기록을 성공으로 반환하지 않음 |
| 보관 | 연구 접근 종료 시각까지 재전송 키 재사용을 막는 최소 기록 유지 제안. 사진·제목·설문 원문을 포함한 응답 전체의 무기한 캐시는 금지. 삭제 요청 시 연결 기록도 삭제. 최소 필드·보관/삭제 상세는 은서와 검토 |

성공 응답 재현 시 저장 스냅샷을 그대로 화면 최종 상태로 쓰면 취소 이후 Scheduled가 보일 수 있으므로 GET 재조회가 필요하다. 성공 재현 검증은 신규 수정의 expectedVersion 검사보다 앞서 수행하되, 접근 검증은 항상 먼저다. 유효 세션 교체가 아닌 폐기/철회/만료 세션에는 이전 응답도 반환하지 않는다.

## 4. 추가 오류 제안 및 mock

| HTTP / type | code | 의미 |
|---|---|---|
| 409 / CONFLICT | `PROMISE_SOURCE_NOT_ELIGIBLE` | 취소/TechFailed 등 해당 경로의 원본 조건 불충족 |
| 409 / CONFLICT | `PROMISE_SOURCE_VERSION_CONFLICT` | 원본 버전 불일치, 재조회 |
| 400 / VALIDATION | `PROMISE_SOURCE_DATE_MISMATCH` | 새 T가 원본 최종 KST 날짜와 다름. field=scheduledAt |
| 409 / CONFLICT | `PROMISE_RECREATION_LIMIT_REACHED` | 날짜별 사용자 재생성 이미 사용 |
| 409 / CONFLICT | `PROMISE_TECHNICAL_RECOVERY_LIMIT_REACHED` | 날짜별 기술복구 이미 사용 |

기존 `STUDY_PERIOD_ENDED`, 예약 시간 오류, `PROMISE_ACTIVE_EXISTS`, `PROMISE_DATE_ALREADY_USED`, 소유권404도 재사용한다. 이름·우선순위는 팀 리뷰 전이다.

[파생 약속 mock](fixture/promise/recovery-cases.json)은 재생성 정상1개와 날짜 불일치·재생성 한도·기술복구 한도·부적격 원본·공식 종료 오류5개다. context는 **테스트 전제**이며 실제 요청/응답에 포함하지 않는다. 기술복구 성공 actions는 미승인 수정 규칙을 고정하지 않기 위해 이번 mock에서 제외한다.

## 5. 확인·남은 범위

- [x] 승인 D06의 날짜·별도 카운터·원본 연결·내부 재처리 구분
- [x] 재생성/기술복구 경로·오류·멱등 처리 상세 제안
- [ ] 기술복구로 생성된 약속의 수정 가능 여부: 정책 §2.1의 ‘최초 약속당’과 §2.4를 근거로 PO 확인 필요. 사용자 재생성 수정 불가와 자동으로 동일시하지 않음
- [ ] 복합 재생성/기술복구 체인의 예외 우선순위와 분석 적격성 팀 확인
- [ ] 멱등 기록의 V2+ 스키마·정규화·보관/삭제 및 FE 인수
- [ ] 실제 API·DB 동시성·마감 후 retry·철회 경합 테스트
