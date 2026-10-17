# 구현 및 티켓 동기화 기록

이 파일은 구현 완료 범위와 Jira 추적 상태를 함께 기록한다. 구현이 완료된 항목은 관련 티켓의 완료 조건과 검증 결과를 남긴다. 원격 Jira 상태를 변경할 수 없는 경우에는 완료로 표시하지 않고 사유를 기록한다.

## 2026-10-18

### SCRUM-23 / 약속 생성·수정·취소 API

- 상태: 완료 조건 충족, Jira 완료 처리.
- 추가: `backend/src/main/resources/db/migration/V3__promise_creation_idempotency.sql`, `PromiseIdempotencyKeyPolicy`.
- 검증: Testcontainers PostgreSQL에서 V1~V3 마이그레이션 적용, 관련 테스트 15건 통과.
- 완료: 멱등성 키·지문 충돌·병렬 생성, 수정 row_version 충돌·KST 날짜 원장, 병렬 취소 단일 종료를 검증했다.

### SCRUM-23 / 약속 생성 컨트롤러 멱등성 연결

- 상태: 완료.
- 추가: `POST /api/promises`가 `Idempotency-Key`를 받고 참가자별 키·요청 지문을 트랜잭션으로 예약한다.
- 동작: 동일 키·동일 지문은 기존 약속을 반환하고, 동일 키·상이한 지문은 `409 IDEMPOTENCY_KEY_REUSED`로 거절한다.
- 검증: `PromiseControllerIntegrationTests` 2건에서 실제 PostgreSQL 세션으로 동일 키 재요청 재사용, 본문 변경 충돌, 병렬 요청의 단일 promise 생성을 확인했다.
- 검증: `PromiseControllerIntegrationTests` 3건에서 병렬 약속 수정의 200·409 낙관적 잠금 결과와 변경된 KST 날짜 원장 1건을 확인했다.
- 검증: `PromiseControllerIntegrationTests` 4건에서 병렬 취소의 200·409 결과와 단일 `Closed/Cancelled` 상태를 확인했다.
- 남은 범위: 응답 envelope 정리는 별도 후속 품질 작업이며 SCRUM-23 핵심 완료 조건에는 포함하지 않았다.

### SCRUM-35/37 / 사진 인증 JDBC 경계 보정

- 상태: 연동 경계 보정.
- 수정: 사진 제출·판정 결과 API의 PostgreSQL `timestamptz` 읽기·쓰기 변환을 명시했다.
- 검증: 전체 백엔드 테스트 50건 통과. 사진 API의 운영 저장소·AI worker 연결은 여전히 남아 있다.

### SCRUM-35/37 / 사진 판정 결과 상태 전이

- 상태: 구현 진행 중.
- 추가: `COMPLETED` 결과의 `AuthCompleted`, `TECH_ERROR` 결과의 `Closed/TechFailed` 전이와 취소·철회·중복 완료 보호.
- 검증: `PhotoEvaluationResultControllerIntegrationTests` 2건 통과.
- 남은 범위: 외부 사진 저장소·AI worker, 사진 제출 멀티파트 통합 테스트, T+30 지연 결과 전체 시나리오.

### SCRUM-35 / 사진 제출 멀티파트 통합 검증

- 상태: 메타데이터 저장·중복 요청 범위 검증됨.
- 검증: 실제 MockMvc multipart와 PostgreSQL에서 사진 메타데이터·attempt 연결, 동일 요청 재사용, 지문 변경 409를 확인했다(2건).
- 남은 범위: 외부/private object storage 업로드, AI worker 연동, T+30 지연 결과 전체 시나리오.

### 전체 검증 회귀 결과

- `backend\\mvnw.cmd test`: 56건 실행, 실패 0, 오류 0. Docker/Testcontainers PostgreSQL과 Flyway V1~V3 포함.
- `frontend\\npm run build`: 성공.
- `git diff --check`: 성공.

### SCRUM-76 / 화면 API 인수 문서 대조

- 상태: 구현 대조 진행 중.
- 갱신: `docs/api/SCREEN_API_HANDOFF.md`, `PROMISE_API_CONTRACT.md`, `PHOTO_SUBMISSION_CONTRACT.md`에 실제 약속·사진 API 검증 범위를 반영하고 폐기된 외부 기준 ID를 제거했다.
- 검증 완료: 약속 생성·수정·취소, 사진 제출·판정 결과의 HTTP/DB 동시성 범위.
- 남은 범위: FE/BE 역할별 인수 리뷰, 공통 response envelope 합의, 알림·집계·자기보고 전체 모듈 계약과 fixture.
- Jira: SCRUM-76은 남은 인수 조건으로 진행 중이며 완료 처리하지 않는다.

### Docker 기반 DB 통합 검증

- 상태: 완료 범위 검증됨.
- 확인: Docker Desktop 엔진에서 Testcontainers PostgreSQL 16을 기동하고 Flyway V1/V2를 실제 적용했다.
- 수정: 사진 자산 중복 제약조건 테스트가 의도한 인덱스를 단독 검증하도록 fixture를 분리했고, 트랜잭션 프록시 대상 Controller의 Spring 컨텍스트 기동을 보정했다.
- 검증: `backend\\mvnw.cmd test` — 46건 실행, 실패 0, 오류 0.
- 남은 범위: 제품 기능의 전체 수용조건과 외부 알림·사진 저장소·AI worker 연동은 별도 작업이다.
- 대응 Jira: SCRUM-83 검증 범위에 반영. 티켓 완료 여부는 사진 제출 저장 기능의 전체 수용조건 충족 후 판단한다.

## 2026-10-16

### 외부 기준·Jira 연결 확인 결과

- Notion 기준 페이지: 확인 실패. 연결된 Notion 통합이 `object_not_found (404)`를 반환하여 `3d64c293-890d-80cc-940b-dc3cef7aa32c` 및 하위 항목의 최신 내용을 읽지 못했다.
- Jira 연결: `codex_apps` Atlassian 연결에서 `https://dodu-mobile.atlassian.net`을 확인했다. `SCRUM-22` 조회와 보드 1의 활성 `SCRUM Sprint 3`를 확인했으며, 이후 Jira 작업은 이 연결과 cloud ID를 사용한다.
- 참고: 별도 일반 Atlassian 연결은 `https://dodu-app.atlassian.net`을 반환하므로 사용하지 않는다.

### NEXT01 / 참가자 식별·재발급·접근 제어

- 상태: 구현 진행 중
- 완료 범위: 접근 상태 판정, 접근 코드 생성, HMAC-SHA-256 해시, 세션 교체, `POST /api/access/enter`
- 검증: 관련 단위 테스트 통과, 백엔드 패키징 통과
- 남은 범위: 실제 DB 통합 테스트와 철회·재발급 API
- 원격 티켓: 현재 연결된 Jira 권한으로 `dodu-mobile` 상태 변경 불가

### NEXT02 / 약속 생성·수정·취소·상태 복원 API

- 상태: 구현 진행 중
- 완료 범위: 약속 생성, 활성 약속 조회, 생성 입력 경계 검증, 서버 시간 기준 검증, 약속 날짜 사용 원장, 수정·취소 API 초안
- 검증: 요청 경계 단위 테스트 통과, 백엔드 패키징 통과
- 남은 범위: DB 통합 테스트, 멱등성, 수정·취소 동시성 검증, 응답 envelope 정리, 프런트 연결
- 원격 티켓: 현재 연결된 Jira 권한으로 `dodu-mobile` 상태 변경 불가

### 적용 규칙

- 새 구현 작업이 기존 티켓에 없으면 원격 Jira에 먼저 생성하고 Sprint 3에 배정한다.
- 기존 티켓의 완료 조건을 모두 충족하면 검증 결과와 함께 완료 처리한다.
- 원격 Jira가 연결되지 않거나 권한이 없으면 로컬 문서에 대상 티켓·남은 범위·차단 사유를 기록하고 완료 처리하지 않는다.

## 3-6 Progress

### Database and migrations

- Status: foundation confirmed.
- Confirmed: PostgreSQL/Flyway dependencies, V1 schema, access/promise/auth/survey tables and constraints.
- Remaining: product-level integration scenarios, concurrency/idempotency verification, and external service integration.

### Notifications and photo authentication

- Status: policy layer confirmed; integration API remains open.
- Confirmed: T/T+10/T+20/T+30 boundaries, one concurrent Evaluating attempt, and late valid submission result preservation in existing policy tests.
- Remaining: notification delivery jobs and photo upload/evaluation API integration.

### Authentication history and self-report

- Status: eligibility and 48-hour response boundary implemented.
- Added: backend/src/main/java/com/dodu/survey/ValidationSurveyPolicy.java
- Verified: only AuthCompleted/AuthIncomplete are eligible; availableAt is inclusive and expiresAt is exclusive.
- Added: backend/src/main/java/com/dodu/survey/ValidationSurveyController.java
- Verified: participant-scoped survey read/submit endpoints, server-side 48-hour boundary, duplicate submission conflict, and separate actual_started fields from promise authentication outcome.
- Remaining: history screen integration and production notification wiring.

### Figma fidelity

- Status: not started.
- Remaining: compare Figma handoff frames and adjust frontend UI.

### Ticket synchronization

- Local tracking: NEXT04/NEXT05/NEXT07 and related DODU records.
- Remote Jira: the current connected session is verified against `dodu-mobile` with Sprint 3. Each implementation commit is synchronized to its matching ticket; completion is still limited to tickets whose acceptance criteria are fully satisfied and assigned to `mono`.
- Remote tickets are not marked complete.
