# 구현 및 티켓 동기화 기록

이 파일은 구현 완료 범위와 Jira 추적 상태를 함께 기록한다. 구현이 완료된 항목은 관련 티켓의 완료 조건과 검증 결과를 남긴다. 원격 Jira 상태를 변경할 수 없는 경우에는 완료로 표시하지 않고 사유를 기록한다.

## 2026-10-25 현재 Sprint 3 상태 및 dev 반영

- 통합 브랜치: `integration/sprint-3-20261021`
- `dev` 반영: 최신 통합 검증 커밋까지 fast-forward 완료, 원격 `origin/dev` push 완료.
- 강민(mono) 완료: SCRUM-23, SCRUM-31, SCRUM-32.
- 강민(mono) 부분 구현·미완료: SCRUM-70, SCRUM-76, SCRUM-83, SCRUM-84, SCRUM-85, SCRUM-86, SCRUM-87. 외부 저장소·AI worker·알림 발송/스케줄러·FE 연결·QA 조건은 완료로 표시하지 않는다.
- 강민(mono) 미착수 또는 결정·인수 필요: SCRUM-6, SCRUM-9, SCRUM-14, SCRUM-17, SCRUM-20, SCRUM-21, SCRUM-25, SCRUM-35, SCRUM-37, SCRUM-75. 각 티켓의 기간은 2026-10-25~2026-11-01로 설정했다.
- 문은서: SCRUM-22는 완료, SCRUM-15·27·28·30·34·36·40·42는 미완료.
- Elle Yoo: SCRUM-13·16·77·78·79·80·81·82는 완료, SCRUM-11·24·26·39·69는 미완료.
- 김경제: SCRUM-71·72는 완료, SCRUM-88은 검토 중.
- 검증: Docker 재기동 후 `npm run verify` 통과. 백엔드 174개 테스트, 실패 0·오류 0, Flyway V1~V5, 프런트엔드 lint/build, 백엔드 패키징, `git diff --check`.

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
- 남은 범위: 응답 형식 정리는 별도 후속 품질 작업이며 SCRUM-23 핵심 완료 조건에는 포함하지 않았다.

### SCRUM-35/37 / 사진 인증 JDBC 경계 보정

- 상태: 연동 경계 보정.
- 수정: 사진 제출·판정 결과 API의 PostgreSQL `timestamptz` 읽기·쓰기 변환을 명시했다.
- 검증: 전체 백엔드 테스트 50건 통과. 사진 API의 운영 저장소·AI 작업자 연결은 여전히 남아 있다.

### SCRUM-35/37 / 사진 판정 결과 상태 전이

- 상태: 구현 진행 중.
- 추가: `COMPLETED` 결과의 `AuthCompleted`, `TECH_ERROR` 결과의 `Closed/TechFailed` 전이와 취소·철회·중복 완료 보호.
- 검증: `PhotoEvaluationResultControllerIntegrationTests` 2건 통과.
- 남은 범위: 외부 사진 저장소·AI 작업자, 사진 제출 멀티파트 통합 테스트, T+30 지연 결과 전체 시나리오.

### SCRUM-35 / 사진 제출 멀티파트 통합 검증

- 상태: 메타데이터 저장·중복 요청 범위 검증됨.
- 검증: 실제 MockMvc multipart와 PostgreSQL에서 사진 메타데이터·attempt 연결, 동일 요청 재사용, 지문 변경 409를 확인했다(2건).
- 남은 범위: 외부 비공개 객체 저장소 업로드, AI 작업자 연동, T+30 지연 결과 전체 시나리오.

### 전체 검증 회귀 결과

- `backend\\mvnw.cmd test`: 56건 실행, 실패 0, 오류 0. Docker/Testcontainers PostgreSQL과 Flyway V1~V3 포함.
- `frontend\\npm run build`: 성공.
- `git diff --check`: 성공.

### SCRUM-76 / 화면 API 인수 문서 대조

- 상태: 구현 대조 진행 중.
- 갱신: `docs/api/SCREEN_API_HANDOFF.md`, `PROMISE_API_CONTRACT.md`, `PHOTO_SUBMISSION_CONTRACT.md`에 실제 약속·사진 API 검증 범위를 반영하고 폐기된 외부 기준 ID를 제거했다.
- 검증 완료: 약속 생성·수정·취소, 사진 제출·판정 결과의 HTTP/DB 동시성 범위.
- 남은 범위: FE/BE 역할별 인수 리뷰, 공통 응답 형식 합의, 알림·집계·자기보고 전체 모듈 계약과 테스트 fixture.
- Jira: SCRUM-76은 남은 인수 조건으로 진행 중이며 완료 처리하지 않는다.

### Docker 기반 DB 통합 검증

- 상태: 완료 범위 검증됨.
- 확인: Docker Desktop 엔진에서 Testcontainers PostgreSQL 16을 기동하고 Flyway V1/V2를 실제 적용했다.
- 수정: 사진 자산 중복 제약조건 테스트가 의도한 인덱스를 단독 검증하도록 fixture를 분리했고, 트랜잭션 프록시 대상 Controller의 Spring 컨텍스트 기동을 보정했다.
- 검증: `backend\\mvnw.cmd test` — 46건 실행, 실패 0, 오류 0.
- 남은 범위: 제품 기능의 전체 수용조건과 외부 알림·사진 저장소·AI 작업자 연동은 별도 작업이다.
- 대응 Jira: SCRUM-83 검증 범위에 반영. 티켓 완료 여부는 사진 제출 저장 기능의 전체 수용조건 충족 후 판단한다.

## 2026-10-16

### 외부 기준·Jira 연결 확인 결과

- Notion 기준 페이지: 확인 실패. 연결된 Notion 통합이 `object_not_found (404)`를 반환하여 `3d64c293-890d-80cc-940b-dc3cef7aa32c` 및 하위 항목의 최신 내용을 읽지 못했다.
- Jira 연결: `codex_apps` Atlassian 연결에서 `https://dodu-mobile.atlassian.net`을 확인했다. `SCRUM-22` 조회와 보드 1의 활성 `SCRUM Sprint 3`를 확인했으며, 이후 Jira 작업은 이 연결과 클라우드 ID를 사용한다.
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
- 남은 범위: DB 통합 테스트, 멱등성, 수정·취소 동시성 검증, 응답 형식 정리, 프런트 연결
- 원격 티켓: 현재 연결된 Jira 권한으로 `dodu-mobile` 상태 변경 불가

### 적용 규칙

- 새 구현 작업이 기존 티켓에 없으면 원격 Jira에 먼저 생성하고 Sprint 3에 배정한다.
- 기존 티켓의 완료 조건을 모두 충족하면 검증 결과와 함께 완료 처리한다.
- 원격 Jira가 연결되지 않거나 권한이 없으면 로컬 문서에 대상 티켓·남은 범위·차단 사유를 기록하고 완료 처리하지 않는다.

### SCRUM-70 / 참가자 코드 IP 임시 차단

- 상태: 구현 연동 완료, 운영 보강 작업 남음.
- 추가: Flyway V4 `access_failure_rate_limit`, `AccessFailureRateLimiter`, `/api/access/enter` 연동.
- 검증: 10분 안에 10회 실패하면 15분 차단되고, 차단 중 요청은 만료 시각을 연장하지 않으며, 접근 성공 시 기록이 초기화되고, HTTP 429에 `TOO_MANY_ATTEMPTS`와 `Retry-After`가 포함되는 것을 확인했다.
- 남은 범위: 신뢰 프록시 원본 IP 설정, 제한된 정리·보관, 봇 토큰 제공자 선정·검증, 다중 노드 공격·오탐 QA.
- Jira: SCRUM-70은 진행 중으로 유지한다. 외부 배포와 봇 검증 조건을 결정하고 확인하기 전에는 완료할 수 없다.

## 3-6 진행 현황

### 데이터베이스 및 마이그레이션

- 상태: 기반 구성 확인.
- 확인: PostgreSQL/Flyway 의존성, V1 스키마, 접근·약속·인증·설문 테이블과 제약조건.
- 남은 범위: 제품 수준 통합 시나리오, 동시성·멱등성 검증, 외부 서비스 연동.

### 알림 및 사진 인증

- 상태: 정책 계층 확인, 연동 API는 미완료.
- 확인: T/T+10/T+20/T+30 경계, 동시에 하나만 허용하는 Evaluating 시도, 지연된 정상 제출 결과 보존을 기존 정책 테스트에서 확인했다.
- 남은 범위: 알림 발송 작업과 사진 업로드·판정 API 연동.

### 인증 이력 및 자기보고

- 상태: 대상 판정과 24시간 응답 경계 구현.
- 추가: `backend/src/main/java/com/dodu/survey/ValidationSurveyPolicy.java`
- 검증: `AuthCompleted`와 `AuthIncomplete`만 대상이 되고, `availableAt`은 포함하며 `expiresAt`은 제외하는 것을 확인했다.
- 추가: `backend/src/main/java/com/dodu/survey/ValidationSurveyController.java`
- 검증: 참가자 범위 설문 조회·제출 API, 서버 기준 24시간 경계, 중복 제출 충돌, 약속 인증 결과와 `actual_started` 필드 분리를 확인했다.
- 남은 범위: 이력 화면 연동과 운영 알림 연결.

### Figma 화면 일치도

- 상태: 미착수.
- 남은 범위: Figma 핸드오프 화면을 비교하고 프런트엔드 UI를 보정한다.

### 티켓 동기화

- 로컬 추적: NEXT04/NEXT05/NEXT07 및 관련 DODU 기록.
- 원격 Jira: 현재 연결 세션은 Sprint 3이 있는 `dodu-mobile` 기준으로 확인했다. 각 구현 커밋은 대응 티켓에 동기화하며, 완료 처리는 수용 조건을 모두 충족하고 `mono`에게 할당된 티켓으로 제한한다.
- 원격 티켓은 근거 없이 완료 처리하지 않는다.
### SCRUM-84 / 지연 결과를 위한 정상 접수 보호

- 상태: 부분 구현 검증, 외부 AI·결과 전체 생명주기는 미완료.
- 추가: 결과 저장 시 서버에 저장된 `valid_received_at`이 `scheduled_at <= valid_received_at < scheduled_at + 30 minutes`를 만족하도록 하고, 같은 갱신에서 참가자 소유권·철회 여부·`Evaluating` 상태도 확인한다.
- 검증: 정상 접수된 시도는 개입 창 이후에도 완료할 수 있고, 정상 접수 근거가 없는 시도는 `Evaluating`으로 남아 409를 반환한다.
- 남은 범위: 외부 AI 작업자 콜백 인증, 이전 시도·철회 동시성 검증표, 운영 결과 전달.
## SCRUM-77 UI 목업

- Jira: 완료.
- 범위: 참가자 코드 입력·오류·최초/재진입/접근 종료 목업 분기와 포커스 복귀.
- 후속: 실제 인증 API 연결은 SCRUM-24, 전체 온보딩은 SCRUM-78에서 담당한다.
## SCRUM-78 UI 목업

- Jira: 완료.
- 범위: 서비스 소개·PWA 안내·권한 복구 화면과 참가자 진입 뒤 분기.
- 후속: 실제 권한·세션 API 연결과 운영 PWA 검증은 별도 범위다.
## SCRUM-79 UI 목업

- Jira: 완료.
- 범위: 약속 작성·확인·상세·수정·취소의 UI 상태와 입력 경계.
- 후속: 서버 API 연결 및 실제 DB 동시성 검증은 SCRUM-23 범위다.
## 통합 브랜치 마이그레이션 조정

- `origin/dev`의 참가자 접근 V2와 기존 사진 제출 V2가 충돌해 사진 제출 마이그레이션을 `V5__photo_submission_storage.sql`로 승격했다.
- 승격 전 전체 검증은 Flyway duplicate version 2로 실패했으며, 수정 후 Docker 기반 전체 검증을 다시 수행한다.
### SCRUM-31 / 동일 멱등성 키 병렬 약속 생성 경합 보정 — 2026-10-23

- 상태: 구현 및 검증 완료. Jira 완료 상태는 기존 완료 상태를 유지한다.
- 변경: `PromiseController`가 멱등성 키 원장을 활성 약속·KST 날짜 검증보다 먼저 선점하도록 순서를 변경했다. 동일 키의 후속 요청은 첫 요청의 결과를 반환한다.
- 근거: 병렬 요청이 두 번의 날짜 검증을 통과한 뒤 후속 요청이 `PROMISE_DATE_ALREADY_USED`로 실패하던 경합을 제거해야 한다.
- 검증: Docker/Testcontainers PostgreSQL에서 대상 병렬 테스트 통과, 전체 `npm run verify` 통과(백엔드 174개 테스트, 실패 0·오류 0, Flyway V1~V5, 프런트엔드 lint/build, 패키징).

### SCRUM-86/87 / 세션 종속 알림 구독 API — 2026-10-25

- 상태: 구독 등록·해지 API 구현. Push 발송·스케줄러·수신/열람 검증은 미완료.
- 변경: `PUT`으로 현재 세션에 구독 키 해시를 등록하고, `DELETE`로 현재 세션의 활성 구독을 해지한다. 참가자 세션 인증과 구독 소유권을 서버에서 확인한다.
- 보안: 원문 구독 키는 저장·로그·응답에 포함하지 않는다. 세션 교체 시 기존 활성 구독은 기존 참가자 인증 흐름에서 비활성화된다.
- 검증 완료: Docker/Testcontainers PostgreSQL에서 전체 `npm run verify` 통과(백엔드 174개 테스트, 실패 0·오류 0, Flyway V1~V5, 프런트엔드 lint/build, 백엔드 패키징). 첫 실행은 Docker Desktop 중단으로 실패했으며 Docker 재기동 후 재실행했다.

### SCRUM-83/87 / 사진 인증 이력 조회 API — 2026-10-25

- 상태: 참가자 소유권 검증을 포함한 이력 조회 API 구현. FE 연결과 외부 AI worker 조회는 미완료.
- 변경: `GET /api/promises/{promiseId}/auth-attempts`에서 시도별 처리 상태, 판정 결과, 오류 구분, 모델 버전, 접수·유효·판정 시각을 반환한다.
- 보안: 사진 원문·저장소 키·요청 지문·접근정보는 응답하지 않으며 타 참가자 약속은 404로 처리한다.
- 검증 완료: Docker/Testcontainers PostgreSQL 기반 전체 `npm run verify` 통과(백엔드 174개 테스트, 실패 0·오류 0, Flyway V1~V5, 프런트엔드 lint/build, 백엔드 패키징).

### SCRUM-87 / 자기보고 Skip·24시간 만료 상태 전이 — 2026-10-25

- 상태: 서버 상태 전이 구현. Push·인앱 알림, FE 상태 표시, QA는 미완료.
- 변경: 유효한 응답 기간 안의 `POST /api/promises/{promiseId}/validation-survey/skip`을 `Skipped`로 저장하고, 조회 시 `expiresAt` 이상이면 `Pending`·`Prompted`·`Reminded`를 `Expired`로 전이한다.
- 보호: 제출·건너뛰기·만료 상태를 서로 덮어쓰지 않으며, 자기보고 미응답은 다음 약속 생성을 차단하지 않는다.
- 검증 완료: Docker/Testcontainers PostgreSQL 기반 전체 `npm run verify` 통과(백엔드 174개 테스트, 실패 0·오류 0, Flyway V1~V5, 프런트엔드 lint/build, 백엔드 패키징).

### SCRUM-87 / 자기보고 최초 진입 Prompted 전이 — 2026-10-25

- 상태: 응답 가능 기간의 최초 조회 시 `Pending`을 `Prompted`로 전이하도록 구현.
- 보호: `prompted_at`은 최초 시각을 유지하고 `Submitted`·`Skipped`·`Expired` 상태는 변경하지 않는다. 반복 알림 `Reminded` 전이는 별도 스케줄러 범위다.

## Sprint 3 통합 브랜치 — 2026-10-21

- 브랜치: `integration/sprint-3-20261021`
- PR: https://github.com/the-dodu-team/dodu/pull/18 → `dev`
- 병합한 제품 브랜치: SCRUM-62, SCRUM-64, SCRUM-69, SCRUM-70, SCRUM-77, SCRUM-78, SCRUM-79, SCRUM-80, SCRUM-81 및 최신 `origin/dev`.
- 검증: `npm run verify` 성공, 프런트엔드 lint/build 성공, 백엔드 174개 테스트 통과, Docker/Testcontainers PostgreSQL과 Flyway V1~V5 통과, `git diff --check` 통과.
- Jira 완료 정책: 이번 작업에서는 `mono` 담당 티켓만 완료할 수 있으며, 다른 담당자의 티켓은 변경하지 않는다.
### SCRUM-31/32/86 — 서버 시각 및 공식 개입 종료 강제

- 상태: 통합 브랜치에 구현됨. Jira 완료 처리는 현재 코드와 테스트가 실제로 확인한 수용 조건으로 제한한다.
- 약속 생성·수정·취소는 주입된 애플리케이션 `Clock`으로 서버 시각을 판단한다.
- 신규 사진 시도는 참가자의 스터디에 연결해 `intervention_ends_at` 이후 `InterventionLifecycleGate`로 거절한다. 종료 전 정상 접수 결과의 지연 처리는 별도 경로로 유지한다.
- 검증: 이 변경에 대한 Spring 통합 테스트와 저장소 전체 검증을 실행했다.
- SCRUM-86 남은 범위: 운영 알림과 복구 스케줄러 전체에 게이트를 연결해야 하며, 해당 경로는 현재 저장소에 구현되어 있지 않다.

### 강민(mono) Sprint 3 완료 감사 — 2026-10-21

- 근거 검토 후 Jira 완료 처리: SCRUM-31, SCRUM-32.
- 이전에 완료 처리되어 현재도 완료 상태: SCRUM-23.
- 구현 근거는 기록했지만 미완료: SCRUM-70(신뢰 프록시, 공유 저장소·다중 노드 운영, 봇 검증·QA), SCRUM-76(FE/BE 인수와 공통 응답 형식), SCRUM-83(외부 객체 저장소·AI 작업자 경계), SCRUM-84(콜백 인증과 전체 동시성 검증표), SCRUM-85(재시도·백오프 작업자와 복구 큐), SCRUM-86(알림·복구 스케줄러 연결), SCRUM-87(자기보고·알림·FE 연동).
- 기획 또는 승인 의존으로 미완료: SCRUM-6, SCRUM-9, SCRUM-14, SCRUM-17, SCRUM-20, SCRUM-21, SCRUM-25, SCRUM-35, SCRUM-37, SCRUM-75. 다른 역할의 인수, 외부 서비스·제공자 결정 또는 더 넓은 통합 범위가 필요하므로 허위 완료 처리하지 않았다.
- 이번 감사의 모든 Jira 변경은 확인된 `dodu-mobile` 사이트와 Sprint 3 클라우드 ID를 사용했다. 다른 담당자의 티켓은 상태를 변경하지 않았다.

### SCRUM-86 / 인앱 알림 원장과 T/T+10/T+20 스케줄러 — 2026-10-25

- 상태: 인앱 알림 발송 기록·조회 API와 서버 시간 기준 스케줄러 구현 완료. Push 실제 발송은 provider 계약 부재로 차단.
- 변경: Flyway V6 `notification_delivery`, `NotificationSchedulePolicy`, `NotificationDeliveryService`, `GET /api/participant/notifications`, `PushNotificationSender` 경계를 추가했다.
- 동작: T, T+10, T+20을 `dispatch_key`로 멱등 생성한다. 약속 `schedule_version`을 키에 포함해 약속 시간 수정 전 예약이 새 시간의 알림을 덮어쓰지 않는다. `Evaluating` 중에는 T+10/T+20을 만들지 않는다. 공식 개입 종료 이후에는 새 알림을 만들지 않는다.
- 인앱: 발송 시각과 열람 시각을 분리해 저장하며, 알림 발송 자체를 수신·열람 증거로 사용하지 않는다.
- Push: 현재 `notification_subscription`에는 구독 키 해시만 있고 실제 Web Push endpoint·암호화 키가 없다. 기본 어댑터는 `UNCONFIGURED`로 기록하며 성공으로 표시하지 않는다. provider 계약·운영 자격정보·worker 연결 후에만 실제 Push 완료로 갱신한다.
- 검증: `NotificationSchedulePolicyTests` 포함 `npm run verify` 성공. 프런트엔드 lint/build 성공, Flyway V1~V6 적용, 백엔드 178개 테스트 통과, 패키징 성공.

### SCRUM-85 / 사진 평가 retry/backoff·복구 worker — 2026-10-25

- 상태: DB 기반 평가 작업 큐, 백오프 재시도, worker 상태 전이와 단위·기능 테스트를 구현했다. 실제 AI worker 연결은 provider 계약 부재로 남아 있다.
- 변경: Flyway V7 `photo_evaluation_job`, `PhotoRecoveryBackoffPolicy`, `PhotoEvaluationRecoveryWorker`, `PhotoEvaluationRecoveryHandler`를 추가하고 사진 제출 시 내구성 작업을 생성한다.
- 동작: 작업당 최대 3회 실행, 30초/120초 백오프, lease·row lock·`skip locked`로 중복 worker 실행을 보호한다. 성공 시 `AuthCompleted`, 거절 시 attempt만 `Rejected`, 최대 실패 시 promise `TechFailed`로 전이한다.
- 보호: 철회 참가자, 유효 접수 시간이 없는 attempt, 이미 Evaluating이 아닌 attempt는 결과를 덮어쓰지 않는다. 지연된 정상 접수 attempt는 공식 종료 후에도 처리 대상이 될 수 있다.
- 외부 차단: 기본 handler는 `AI_WORKER_UNCONFIGURED`를 재시도 가능한 오류로 기록한다. 실제 AI worker endpoint·인증·결과 계약이 확정되기 전에는 SCRUM-85를 완료 처리하지 않는다.
- 검증: `PhotoRecoveryBackoffPolicyTests`, `PhotoEvaluationRecoveryWorkerIntegrationTests` 추가. `npm run verify` 성공 — 프런트엔드 lint/build, Flyway V1~V7, 백엔드 189개 테스트(실패 0·오류 0), 패키징 성공.

### 구현 순서·논의 질문 분리 — 2026-10-25

- `docs/dodu/IMPLEMENTATION_ROADMAP_20261025.md`에 추가 정보 없이 진행 가능한 순서, AI 담당자 질문, 팀 논의 질문을 분리해 기록했다.
- AI worker endpoint·인증·결과 계약, 사진 저장소, Push provider, 운영 QA는 외부 결정 의존으로 별도 차단 목록에 유지한다.
- 다음 즉시 구현 범위는 공식 개입 종료 이후 신규 개입성 알림 차단과 그 기능 테스트이며, 종료 전 정상 접수 사진 판정과 자기보고 24시간 응답은 계속 허용한다.

### SCRUM-86 / 공식 개입 종료 후 알림 스케줄러 게이트 — 2026-10-25

- 상태: 구현 및 기능 테스트 진행 완료. Push provider 연동과 운영 QA는 미완료.
- 변경: `NotificationDeliveryService`가 스터디의 `intervention_ends_at`과 `InterventionLifecycleGate`를 사용해 공식 종료 이후 T/T+10/T+20 신규 알림을 생성하지 않도록 했다. 종료 전 정상 접수된 사진 복구와 자기보고 리마인드 경로는 이 게이트로 차단하지 않는다.
- 검증: `NotificationDeliveryServiceIntegrationTests`에서 종료 후 알림 0건, 종료 전 지연된 알림 3건 생성, 스케줄러 재실행 후 중복 0건, `NotificationSchedulePolicyTests`를 확인한다.
- 남은 범위: 실제 Push endpoint·암호화 키·provider 계약, 운영 발송·수신 QA, 자기보고 리마인드의 종료 후 운영 기준 합의.

### SCRUM-85 / AI 호출 adapter와 비밀값 보호 — 2026-10-25

- 상태: 부분 구현 검증. AI 응답의 `laptop`·`book`·`other` 매핑, HTTP 오류별 retry/기술 실패 경계, 사진 저장소 reader 경계를 추가했다.
- 변경: `AiPhotoEvaluationRecoveryHandler`, `AiPhotoEvaluationResponseMapper`, `PhotoObjectReader`를 추가하고 복구 worker가 사진 메타데이터를 adapter에 전달하도록 연결했다. 사진 제출은 JPEG/JPG/PNG만 허용한다.
- 보안: 로컬 AI 테스트 폴더와 인증 비밀값 파일을 Git에서 제외하고 `npm run security:scan`을 `npm run verify`에 포함했다. 비밀값 자체는 저장소·로그·문서에 기록하지 않는다.
- 검증: AI 응답 매핑 3개 단위 테스트, 로컬 HTTP server를 이용한 실제 multipart 호출 2개 기능 테스트, Docker/Testcontainers 복구 worker 2개 기능 테스트가 통과했다.
- 남은 범위: 실제 외부 사진 저장소 provider 구현, 운영 endpoint smoke test, AI 서버 운영 QA. provider와 저장소가 확정되기 전에는 SCRUM-85를 완료 처리하지 않는다.
