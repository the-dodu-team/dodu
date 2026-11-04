# 구현 및 티켓 동기화 기록

## 2026-11-05 / SCRUM-83 배포 환경의 약속 인증 버튼

- 원인: `PromiseAuthPreview`의 버튼 활성화와 클릭 처리에 있던 DEV 전용 조건으로 인해 배포 환경의 실제 약속도 인증 진입 불가.
- 수정: API·서버 약속 ID가 있는 경우에만 실제 인증 플래그를 전달해 `LivePhotoAuth`에 진입한다. 운영 mock 진입 및 시간·최종 상태 제한은 유지한다.
- 검증: DEV=false에서 실제 약속 버튼·시작/종료 경계·제외 상태·mock 차단 렌더 테스트 통과. `npm run verify` 통과(FE 테스트/린트/빌드, 보안, PostgreSQL 기반 BE 240건, 패키지). 기존 FE lint 경고 1건 유지.
- 남은 작업: Vercel 최신 배포를 Production으로 승격 후 실제 사진 업로드·Supabase 저장 확인. 카메라 유무가 원인이라고 기록하지 않으며 실제 provider 업로드 성공은 아직 미확인.

## 2026-11-05 / SCRUM-94 참가자 30명 일괄 등록·발급

- 담당: mono, Sprint 4(ID 36). 사용자 요청으로 추가 30명을 현재 기술검증 기간에 등록·발급할 로컬 명령을 구현했다.
- 구현: `OperatorBatchEnrollmentService`, `enroll-batch` CLI 및 `issue-participant.ps1 -Count 30`. 기존 등록은 건너뛰고 기간 날짜·기존 자격·세션을 유지한다. 신규 등록·해시 자격·감사 기록은 한 트랜잭션이며 코드 출력은 커밋 후에만 수행한다.
- 검증: Docker PostgreSQL 16 운영자 통합 테스트 21건 통과(신규 일괄 발급 검증 5건 포함). PowerShell 구문 검사 통과. `npm run verify` 통과: FE 테스트·린트·빌드, 보안 스캔, BE 240건 실패/오류/스킵 0, 패키지 빌드. 기존 FE lint 경고 1건 유지.
- 남은 작업: 로컬 발급 창에 Render와 동일한 DB 접속정보·pepper 입력 후 실제 30건 발급 확인. 비밀값·발급 원문은 문서·티켓·Git에 남기지 않는다. 실제 발급 전 완료 처리하지 않는다.

## 2026-11-05 / SCRUM-76 참가자 입장 대기와 신규 참가자 빈 약속 응답

- 사용자 증상: 입장 버튼이 확인 중으로 유지됨. Network 화면과 공개 JS 번들에서 구형 `/api/access/enter` 호출 확인. 운영 접근 코드는 기록하지 않음.
- 원격 진단: health 최초 조회 30초 timeout, 후속 조회 HTTP 200. `/api/participant/sessions` preflight는 실제 프런트 출처에서 200 및 일치하는 허용 출처 확인. 지연의 서버 내부 원인은 미확인이다.
- 수정: fetch와 응답 본문 읽기 합산 60초 제한, abort, 지연 안내 및 버튼 재활성화. 자동 재전송 없음. 신규 참가자 활성 약속이 없는 빈 본문/null을 null로 처리.
- 검증: `npm run test:api --workspace frontend`, FE build/lint, `npm run security:scan`, `git diff --check` 통과. 기존 lint 경고 1건 유지. 백엔드 변경이 없어 전체 DB 테스트는 재실행하지 않음.
- 남은 작업: 최신 dev 프런트 배포의 Production 승격, 실제 입장·약속·사진 업로드 검증. Supabase 업로드 성공이나 서버 지연 원인 해결로 기록하지 않음.

## 2026-11-04 / 참가자 초기 데이터·로컬 발급 준비 및 FE 입장 API 수정

- 원격 읽기 검증: Render `/api/health` 200, `https://dodu-two.vercel.app` 출처의 입장 preflight 200 및 일치하는 허용 출처 확인.
- 사용자 확인: 운영 DB의 `study_period`, `participant`, `policy_version` 모두 0건.
- 구현: 기술 검증용 7일 개입·9일 접근 기간과 참가자 1명 생성 SQL, 비밀값 숨김 입력 로컬 발급 스크립트 추가. FE를 `/api/participant/sessions` 및 공통 응답 `data.sessionToken`으로 연결하여 운영 CLI와 입장 해시 계약을 일치시킴.
- 검증: FE API 테스트·빌드, 보안 스캔, PowerShell 구문 검사 통과. 격리 PostgreSQL 16에서 Flyway V1~V9 SQL 적용 및 초기 SQL 두 번 실행 후 기간·참가자·정책 각 1건 유지.
- 미실행: 실제 Supabase 초기 데이터 SQL, 운영 접근 코드 발급, 실사진 업로드·삭제 및 AI 판정. 실제 DB 자격정보·pepper는 로컬 실행 환경에 없으며 사용자 입력 필요. 전체 DB 통합 테스트는 이번 변경에서 재실행하지 않음.

## 2026-11-04 / SCRUM-83 Supabase 활성화 시 서버 기동 오류

- 범위: `SupabasePhotoObjectStore`의 운영 생성자에 `@Autowired`를 추가하여 Spring 생성자 선택을 명시했다.
- 근거: Render 런타임 로그의 `No default constructor found`와 동일한 오류를 새 Spring 컨텍스트 테스트에서 수정 전에 재현했다.
- 검증: `SupabasePhotoObjectStoreContextTests`는 provider 활성화 시 읽기·쓰기 빈이 같은 객체인지, `none`일 때 자격정보 없이 시작하는지 확인한다. 실제 자격정보·사진·DB를 사용하지 않는다.
- 실행 결과: `mvnw.cmd -Dtest=SupabasePhotoObjectStoreContextTests,SupabasePhotoObjectStoreTests test` 4건 통과, `mvnw.cmd -DskipTests package` 성공, `npm run security:scan` 및 `git diff --check` 통과. 이번 변경에서 전체 DB 통합 테스트는 재실행하지 않았다.
- 남은 작업: 수정 커밋으로 Render 수동 배포 후 기동·CORS·실사진 업로드/삭제 확인. 외부 통합 검증 전 SCRUM-83은 진행 중으로 유지한다.

## 2026-11-04 / Vercel Linux 빌드 호환성 보강

- 원인: Vercel Linux 환경에서 `rolldown`과 `lightningcss`의 Windows 네이티브 바인딩만 설치되어 Linux 바인딩을 찾지 못함.
- 조치: `frontend/package.json`에 두 도구의 Linux x64 GNU/MUSL 선택적 바인딩과 Node.js 22 엔진 범위를 추가하고 `package-lock.json`을 갱신했다.
- 검증: `npm run build --workspace frontend` 통과, `git diff --check` 통과. Vercel 재배포 결과 확인 필요.
- 남은 범위: Vercel에서 최신 커밋으로 재배포 후 `Ready` 상태와 실제 사이트 접근 확인.

## 2026-11-04 / 사진 업로드 자동 압축

- 대응 범위: FE 사진 인증 업로드 전처리. `frontend/src/features/photo-auth/compressPhoto.ts`에서 JPEG/PNG를 긴 변 1920px, 최대 5 MiB 목표의 JPEG로 변환한다.
- 연결: `LivePhotoAuth`가 압축 완료 파일만 `POST /api/promises/{id}/photo-attempts`로 전송한다. 압축 실패 시 원본을 전송하지 않는다.
- 서버 보호: Spring multipart 파일 6 MB, 요청 7 MB 한도를 설정했다.
- 검증: `npm run test:photo --workspace frontend`, `npm run build --workspace frontend`, `npm run lint --workspace frontend` 통과. Docker 실행 후 `npm run verify`도 통과했으며, 보안 스캔·FE 테스트/빌드·BE 233건·패키지 빌드를 확인했다. FE lint에는 기존 `LiveParticipantPanel.tsx` 경고 1건이 남아 있다.
- 남은 범위: 실제 모바일 브라우저·기기별 압축 확인, Supabase 저장소 및 AI worker 연결 후 실사진 통합 QA.

## 2026-11-02 / FE 실제 API 흐름·검증 보강

- 대응 티켓: SCRUM-87, SCRUM-76. 외부 배포·Push·AI worker 연결은 별도 운영 조건으로 남김.
- FE 약속 흐름: `POST /api/promises`, `PATCH /api/promises/{id}/schedule`, `POST /api/promises/{id}/cancel` 연결. 멱등성 키와 `expectedVersion`을 실제 호출에 사용.
- FE 사진 흐름: JPG/PNG 단일 파일을 `POST /api/promises/{id}/photo-attempts`로 제출하고 `processingStatus`를 화면에 표시.
- FE 이력·자기보고·알림: 인증 이력, 자기보고 상태/건너뛰기, 알림 조회·읽음 처리를 실제 API client로 연결. 민감한 사진 원문과 접근 자격정보는 화면·로그에 노출하지 않음.
- Swagger 체크리스트: 이번 변경에서 직접 Swagger를 실행한 결과는 기록하지 않음. 기존 MockMvc/Testcontainers 결과를 근거로 자동 검증 범위를 갱신하며, 수동 Swagger 결과 칸은 사용자 환경 확인 전까지 `⏳`로 유지.
- Figma: 최종 파일 스크린샷은 확인했으나 지정 노드의 구조화된 디자인 컨텍스트 선택 오류로 정확한 치수·컴포넌트 매핑은 확인하지 못함. 임의의 픽셀 단위 보정은 하지 않음.
- 테스트: FE API 계약 테스트 추가. `npm run verify` 성공: 보안 스캔 335개 Git-visible 파일, FE 테스트 4종, lint/build, 백엔드 232건, Flyway V1~V9/Testcontainers PostgreSQL, 패키지 빌드.

## 2026-11-02 / Swagger 실행 중 발견한 입장 API 결함

- 로컬 Swagger 실행에서 `POST /api/access/enter`가 `participant_device.last_seen_at`의 Instant 바인딩 오류로 HTTP 500을 반환함.
- `ParticipantAccessController`의 기기 생성·갱신 SQL 시간 파라미터를 PostgreSQL JDBC가 인식하는 `Timestamp`로 변환함.
- 수정 후 Swagger 실제 결과를 이어서 기록하며, 수정 전 500은 실패 결과로 남김.
- 추가 확인: 수정 전 UUID 형식 세션은 입장 200 후 후속 API가 401이 됨. `SessionToken.generate` 사용으로 발급·인증 형식을 통일함.

## 2026-11-02 / 주간 지난 약속 요일 색상 보정

- 주간 지난 약속 카드의 요일 라벨에 토요일 파란색, 일요일 빨간색을 적용함.
- 날짜 숫자와 약속 인증 상태 색상은 기존 표시 규칙을 유지함.
- 검증: `npm run build` 성공, 홈 렌더링 테스트 통과, `git diff --check` 통과.

## 2026-11-02 / 약속 취소 확인 문구 보정

- 취소 확인 화면의 보조 버튼 문구를 `유지`에서 `돌아가기`로 변경함.
- 취소 확정 동작과 접근성 포커스 흐름은 변경하지 않음.
- 검증: 프런트 타입 검사·빌드와 `git diff --check` 통과.

## 2026-11-02 / 로컬 Swagger 실제 실행 결과

실행 환경: `http://127.0.0.1:8080`, Swagger UI `/swagger-ui/index.html`, OpenAPI `/v3/api-docs`, PostgreSQL 16 Docker, 합성 테스트 참가자. 접근 코드·토큰·사진 원문은 기록하지 않음.

| 항목 | 실제 결과 |
|---|---|
| Swagger UI / OpenAPI | UI 200 / OpenAPI 200, 17개 path 노출 |
| A-01 참가자 입장 | 200, 세션 토큰 발급 |
| A-02 잘못된 코드 | 401 |
| A-03 재입장 후 기존 세션 | 기존 토큰 401, 신규 토큰 200 |
| A-04 토큰 없음 | 400 |
| B-01 약속 생성 | 200 |
| B-11 동일 멱등성 키 재요청 | 200, 동일 promise 반환 |
| C-01 예정 시간 수정 | 200, version 1 |
| C-03 2차 수정 | 409 |
| D-01 약속 취소 | 200 |
| E-01 T 이전 사진 제출 | 409 `PHOTO_WINDOW_CLOSED` |
| E-history 인증 이력 조회(판정 전) | 404 |
| 자기보고 조회(대상 없음) | 404 |
| G-06 알림 조회(발송 데이터 없음) | 200, 빈 배열 |

직접 실행하지 않은 항목: 실제 AI callback, Push provider 수신·재시도, T/T+10/T+20 시간 조작 시나리오, Supabase 사진 저장소 운영 연결.

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
- Jira 동기화: `SCRUM-86`에 검증 결과 코멘트를 남기려 했으나 현재 Atlassian 연결이 cloud ID 권한 거부를 반환했다. 티켓 완료 처리는 하지 않았으며, 연결 권한이 정상화되면 커밋 `59c4b30`과 검증 결과를 다시 반영해야 한다.

### SCRUM-85 / AI 호출 adapter와 비밀값 보호 — 2026-10-25

- 상태: 부분 구현 검증. AI 응답의 `laptop`·`book`·`other` 매핑, HTTP 오류별 retry/기술 실패 경계, 사진 저장소 reader 경계를 추가했다.
- 변경: `AiPhotoEvaluationRecoveryHandler`, `AiPhotoEvaluationResponseMapper`, `PhotoObjectReader`를 추가하고 복구 worker가 사진 메타데이터를 adapter에 전달하도록 연결했다. 사진 제출은 JPEG/JPG/PNG만 허용한다.
- 보안: 로컬 AI 테스트 폴더와 인증 비밀값 파일을 Git에서 제외하고 `npm run security:scan`을 `npm run verify`에 포함했다. 비밀값 자체는 저장소·로그·문서에 기록하지 않는다.
- 검증: AI 응답 매핑 3개 단위 테스트, 로컬 HTTP server를 이용한 실제 multipart 호출 2개 기능 테스트, Docker/Testcontainers 복구 worker 2개 기능 테스트가 통과했다.
- 남은 범위: 실제 외부 사진 저장소 provider 구현, 운영 endpoint smoke test, AI 서버 운영 QA. provider와 저장소가 확정되기 전에는 SCRUM-85를 완료 처리하지 않는다.

### SCRUM-85 / AI worker 실제 응답 대조 — 2026-11-01

- 확인: 전달된 DINOv2-base FastAPI worker의 `/health`, `/predict`, `/predict/batch` 응답을 실제 호출로 확인했다. `laptop`·`book`·`other` 결과와 `model: dinov2-base-prototype`을 확인했다.
- 변경: Dodu mapper가 `model_version`뿐 아니라 현재 worker의 `model` 필드도 모델 버전으로 저장하도록 보강했다. AI worker 계약을 `docs/api/AI_WORKER_CONTRACT.md`에 기록했다.
- 보안: AI 패키지·샘플·공개 URL·인증 비밀값이 저장소에 올라가지 않도록 외부 bundle 디렉터리를 `.gitignore`에 추가했다.
- 검증: `AiPhotoEvaluationRecoveryHandlerTests` 통과, `npm run security:scan` 통과, 실제 AI 단건·3장 batch HTTP 200 확인.
- 남은 범위: 실제 사진 저장소 `PhotoObjectReader` 구현·운영 endpoint 환경변수 등록·AI 담당자 운영 QA. SCRUM-85는 완료 처리하지 않는다.

### SCRUM-89 / Sprint 4 사진 저장소·AI worker 실제 연결 티켓 — 2026-11-01

- Jira: `SCRUM-89` 생성, `SCRUM Sprint 4`(ID 36) 배정, 담당 `mono`.
- 범위: Supabase 비공개 사진 저장소 adapter, `PhotoObjectReader`, AI worker 실제 호출, 결과·재시도·철회 보호, 30일 보관·삭제와 통합 검증.
- 외부 준비: Supabase bucket·Render 환경변수 등록은 운영 담당자 준비 범위. 비밀값은 로컬 문서·Jira·Git에 기록하지 않는다.
- 상태: 해야 할 일. 외부 설정과 구현·운영 QA 완료 전까지 완료 처리하지 않는다.
- 구현 증분: `SupabasePhotoObjectStore` 업로드·조회·삭제 adapter와 사진 제출 시 선택적 원본 저장 연결을 추가했다. `DODU_PHOTO_STORAGE_PROVIDER`가 설정되지 않으면 저장소 호출을 활성화하지 않는다.
- 검증: adapter 단위 테스트와 AI handler 테스트 통과, 백엔드 package 통과. 전체 통합 테스트 40건은 Docker 환경 부재로 실행 실패.
- 추가 구현: `delete_due_at` 기반 30일 보관·삭제 worker를 추가하고 provider 삭제 성공 후 DB 연결 metadata를 정리하도록 했다. provider 실패 시 다음 실행에서 재시도한다.
- 남은 범위: 실제 Supabase migration·bucket smoke test, 참가자 삭제 요청 연동, Docker 통합 검증과 운영 QA.

### 사진 재사용 정책 보강 — 2026-10-29

- 상태: 같은 약속 내 재시도 허용과 다른 날짜 사진 재사용 차단을 구현·검증했다.
- 변경: 사진 메타데이터의 SHA-256 해시와 참가자·약속 예정일을 비교해 다른 날짜에 이미 사용된 사진이면 `PHOTO_ALREADY_USED_ON_ANOTHER_DAY` 409를 반환한다.
- 검증: 같은 약속의 기존 멱등 재요청은 계속 허용하고, 다른 날짜 약속의 동일 바이트 사진은 PostgreSQL/Testcontainers 기능 테스트로 차단한다.
- 남은 범위: 외부 사진 저장소 provider 연결과 실제 운영 삭제·보관 실행은 별도 차단 범위다.

### BE Swagger 수동 검증 결과 — 2026-10-29

- 출처: BE 담당자가 공유한 Swagger API 테스트 결과와 백엔드 정책 검증 체크리스트.
- 확인된 기본 성공 경로:
  - 참가자 입장 API: CLI로 발급한 접근 코드로 세션 토큰이 발급됨.
  - 약속 생성 API: 토큰과 `Idempotency-Key`를 사용한 요청이 정상 생성됨. 현재 응답은 200이며, 201 Created로의 변경은 후속 리팩터링이다.
  - 진행 중 약속 조회 API: 같은 참가자 세션으로 생성된 약속이 조회됨.
  - 알림 조회 API: 200과 빈 배열을 확인함. 약속 생성 직후라 스케줄러 발송 데이터가 없는 상태다.
- 자동 테스트와 별도인 수동 검증 대기 범위: 인증·세션 실패/철회, 약속 시간 경계·멱등성·동시성, 수정·취소·재생성, 사진 인증 경계와 중복 제출, 철회 후 비동기 결과, T/T+10/T+20 및 자기보고 알림, 공통 오류 응답과 서버 시간·KST 경계.
- 해석: 이번 결과는 기본 성공 경로의 수동 확인이며, 정책 전체 수용조건 또는 Jira 완료 근거로 확대하지 않는다. 체크리스트의 각 항목은 실제 응답 코드와 함께 순차적으로 채워야 한다.

### 알림 철회·자기보고 경계 통합 검증 — 2026-10-29

- 상태: 통합 테스트 보강 완료.
- 검증: 철회된 참가자에게 T/T+10/T+20 개입 알림을 생성하지 않으며, 공식 개입 종료 후에도 마지막 자기보고 리마인더는 만료 전이면 생성되고 설문 상태가 `Reminded`로 전이된다.
- 테스트: `NotificationDeliveryServiceIntegrationTests.withdrawnParticipantDoesNotReceiveInterventionNotifications`, `NotificationDeliveryServiceIntegrationTests.surveyReminderRemainsAvailableAfterOfficialInterventionEnd`.
- 남은 범위: 실제 Push provider 발송·재시도와 운영 수신 QA, Swagger 실제 응답 코드 기록은 별도다.

- 추가 검증: 취소된 약속은 개입 알림을 받지 않으며, 자기보고 만료 시각에는 리마인더를 생성하지 않는다.

### 약속·사진 시간 경계 통합 검증 — 2026-10-29

- 상태: 통합 테스트 보강 완료.
- 검증: 약속 수정은 1회 초과 시 `PROMISE_RESCHEDULE_LIMIT_REACHED`, 시작 이후 취소는 `PROMISE_NOT_MODIFIABLE`로 거절한다. 사진은 예정 시작 전과 `T+30` 이후에 `PHOTO_WINDOW_CLOSED`로 거절한다.
- 테스트: `PromiseControllerIntegrationTests.secondRescheduleIsRejectedAfterTheSingleAllowedChange`, `PromiseControllerIntegrationTests.cancellationAfterScheduledStartIsRejected`, `PhotoSubmissionControllerIntegrationTests.submissionBeforeScheduledStartIsRejected`, `PhotoSubmissionControllerIntegrationTests.submissionAtOrAfterTPlusThirtyIsRejected`.
- 남은 범위: Swagger 실제 응답 코드 기록과 외부 사진 저장소·AI worker 연동은 별도다.

### 알림 조회 상태 필터 통합 검증 — 2026-10-30

- 상태: 통합 테스트 보강 완료.
- 검증: 참가자 알림 조회 API는 현재 참가자 소유의 `IN_APP`이면서 `SENT`인 알림만 반환하고, `FAILED`·`SCHEDULED` Push/인앱 원장은 반환하지 않는다.
- 테스트: `NotificationControllerIntegrationTests.listsOnlySentInAppNotificationsForTheCurrentParticipant`.
- 남은 범위: 실제 Push 발송·재시도와 읽음 처리 API는 별도 운영 연동 범위다.

### SCRUM-86 / 인앱 알림 읽음 처리 — 2026-10-30

- 상태: 구현 및 통합 검증 완료. `PUT /api/participant/notifications/{notificationId}/read`가 현재 참가자의 `IN_APP`·`SENT` 알림만 읽음 처리한다.
- 보호: 타 참가자·실패·예약 상태 알림은 `404 RESOURCE_NOT_FOUND`로 처리하며, 같은 알림을 반복 읽어도 최초 `read_at`을 보존한다.
- 검증: `NotificationControllerIntegrationTests`에서 정상 읽음, 반복 요청 멱등성, FAILED 알림 차단을 PostgreSQL/Testcontainers로 확인했다.
- 남은 범위: 실제 Push provider 발송·재시도와 운영 수신 QA.

### SCRUM-86 / Push 애플리케이션 재시도 백오프 — 2026-10-30

- 상태: provider와 분리된 애플리케이션 재시도 구조 구현.
- 변경: Flyway V8에 Push `attempt_count`·`next_attempt_at`을 추가하고, 일시 실패만 30초·2분·10분 백오프로 최대 3회 재시도한다. `UNCONFIGURED`는 성공으로 표시하지 않고 재시도하지 않는다.
- 검증: `PushRetryPolicyTests`에서 백오프 경계와 최대 시도 제한을 확인했다.
- 남은 범위: 실제 FCM/Web Push provider 자격정보·endpoint 연결과 운영 발송·수신 QA.

### SCRUM-87·76 / FE API client 기반 — 2026-10-30

- 상태: FE 연결 기반 구현 완료. `frontend/src/api/doduApi.ts`에 알림·인증 이력·자기보고 typed client와 공통 성공/오류 envelope 처리를 추가했다.
- 보호: 토큰은 `Authorization` 헤더에만 전달하고, 오류 분기는 안전한 오류 코드로 처리한다.
- 남은 범위: 기존 mock 화면을 실제 세션·약속 데이터에 연결하는 화면 전환, 운영 FE 환경변수와 사용자 QA.

### SCRUM-84 / 사진 판정 동시성 보호 통합 검증 — 2026-10-30

- 상태: 현재 attempt의 단일 terminal 전이를 통합 검증했다.
- 검증: 동시에 `COMPLETED`·`REJECTED` 결과를 제출해 한 요청만 200으로 반영되고 다른 요청은 409로 거절되며, `decided_at`과 terminal 상태가 한 번만 저장되는 것을 PostgreSQL/Testcontainers에서 확인한다.
- 남은 범위: 외부 AI callback 서명·운영 worker와 철회 동시성의 실제 provider 시나리오.

### SCRUM-87 / 이력·자기보고 소유권 및 민감정보 비노출 검증 — 2026-10-30

- 상태: 통합 검증 보강 완료.
- 검증: 사진 인증 이력과 자기보고 조회가 소유 참가자에게만 반환되고, 타 참가자 약속은 404로 숨겨지며, 사진 원문·저장소 키·요청 지문·설문 원문 필드가 응답에 포함되지 않는 것을 확인했다.
- 남은 범위: FE 화면 연결과 운영 Push/인앱 QA.

### SCRUM-23 / 활성 약속 중복 생성 실패 경로 — 2026-10-30

- 상태: 멱등성·동시성 실패 경로 통합 검증 보강 완료.
- 검증: 다른 멱등성 키로 두 번째 활성 약속을 생성하면 `PROMISE_ACTIVE_EXISTS` 409가 반환되는 것을 확인했다.
- 남은 범위: 공통 envelope 전환은 FE/BE 계약 합의가 필요한 별도 작업이다.

### SCRUM-76 / 공통 응답·화면 인수 문서 보강 — 2026-10-30

- 상태: 구현 대조 문서 보강 완료.
- 갱신: 공통 응답 계약에 실제 적용 범위와 기존 DTO 호환 경계를 기록하고, 화면 API 인수 지도에 알림 목록·읽음 처리 계약과 404 소유권 규칙을 추가했다.
- 남은 범위: FE/BE가 모든 업무 API를 envelope로 전환할지에 대한 최종 합의와 실제 화면 인수 승인.
### SCRUM-70·76·83·86·87 / 비AI 통합 마감 점검 — 2026-11-01

- 이번 범위: 운영 설정을 알 수 없는 상태에서 재현 가능한 코드 경계만 보강했다. `ClientIpResolver`를 추가해 trusted proxy가 비어 있으면 전달 IP 헤더를 무시하고, 허용된 IPv4/IPv6 네트워크에서만 `X-Forwarded-For`/`Forwarded`의 원본 주소를 사용한다. 참가자 코드 rate limit은 기존 PostgreSQL 저장소를 그대로 사용한다.
- 검증: `ClientIpResolverTests` 3건, `npm run security:scan`, `git diff --check`, 백엔드 컴파일을 확인했다. 전체 `npm run verify`는 프런트 검증까지 통과했지만 Testcontainers가 Docker 엔진을 찾지 못해 백엔드 통합 테스트 40건이 실행되지 않아 실패했다.
- 완료로 표시하지 않는 범위: 실제 FCM/Web Push 자격정보·endpoint·발송/수신 QA, 실제 사진 객체 저장소와 AI worker, FE 화면의 세션·약속 실데이터 연결 및 사용자 QA, 모든 API envelope 전환 승인, 실제 배포 프록시/CIDR와 다중 노드 운영 QA, Figma 최종 화면 비교, Sprint 3 최종 승인.
- Figma: 현재 기준 파일을 [Dodu 디자인 파일](https://www.figma.com/design/SyTucdjEX94fcqYzRmK5xE/%EB%94%94%EC%9E%90%EC%9D%B8-%ED%8C%8C%EC%9D%BC?node-id=393-1781)로 갱신했다. `393:1781` 페이지 `v2.5 디자인 핸드오프`가 존재하는 것을 metadata로 확인했다. 이전 `Qx26p9tdYo9doDhbxYGnfB` 파일은 보조 기준으로 내리고, 현재 화면 비교·상태 QA는 아직 남아 있다.
- Jira: SCRUM-70·76·83·84·85·86은 외부 연동·인수 조건이 남아 진행 중으로 유지하고, SCRUM-87은 FE 사용자 검증 전까지 해야 할 일/진행 범위로 유지한다.
### SCRUM-83 / 사진 원본 저장소 선택 — 2026-11-01

- 결정 변경: 자동 결제를 허용하지 않으므로 R2·Supabase Storage를 운영 provider로 확정하지 않는다. provider 확정 전에는 개발용 비공개 마운트 볼륨만 허용하고 운영 원본 사진 기능은 비활성화한다.
- 정책: 공식 개입 종료시각+30일을 DB 기준으로 삭제 worker가 처리하며, provider 확정 후에만 Lifecycle 안전망을 추가한다. 브라우저·DB·로그에는 저장소 비밀값과 공개 URL을 남기지 않는다.
- 문서: `docs/operations/PHOTO_STORAGE.md`, `Decisions.md`의 `DEV-20261101-02`.
- 남은 범위: 자동 결제 없는 운영 저장소·배포 호스트·백업 방식 확정, 실제 adapter 구현, 업로드/조회/삭제 smoke test, 삭제 worker와 통합 테스트. 운영 방식이 확정되기 전에는 완료 처리하지 않는다.
### SCRUM-87·76 / React Native 전환 기반 — 2026-11-01

- 구현: API 타입을 shared 영역으로 분리하고, 브라우저/RN 저장소를 교체할 수 있는 `KeyValueStorage`·`ParticipantSessionStore`를 추가했다. 기존 mock 화면은 변경하지 않았다.
- 검증: `test:platform`에 저장소·세션 어댑터 테스트를 추가하고 전체 프런트 검증 workflow에 포함했다.
- 남은 범위: RN 프로젝트 생성, 네이티브 화면 재작성, 카메라·알림 권한 adapter, 실제 세션·약속 API 화면 연결과 기기 QA.
### 참가자 철회 요청 API

- 구현: `DELETE /api/participant/account` 추가.
- 범위: 인증된 세션 소유자만 자기 계정을 철회할 수 있으며 서버 시각을 사용한다. 세션·구독 종료, 활성 약속 `Withdrawn` 마감, 자기보고·미발송 알림 중지, 사진 retention worker 삭제 예약을 하나의 트랜잭션으로 처리한다.
- 검증: Docker Desktop을 사용한 전체 백엔드 테스트 및 Flyway 9단계 마이그레이션 검증 통과. Surefire 실패·오류 없음.
- 미완료: 실제 Supabase storage 삭제 smoke test와 운영 환경 QA는 자격정보·배포 환경 설정 후 별도 확인 필요.
### FE 실제 API client 계약 보강

- 구현: 세션 입장, 약속 CRUD, multipart 사진 제출, 참가자 철회 client를 추가하고 raw 응답과 공통 envelope 응답을 구분했다.
- 검증: `frontend/npm run build`, `frontend/npm run lint`, `git diff --check` 통과.
- 미완료: 실제 FE 화면에서 토큰 발급·약속·사진 API를 연결하는 사용자 흐름 QA, 운영 API base URL 등록, 실제 Supabase·AI worker 연동.
### FE 참가자 입장 화면 실제 API 전환

- 구현: `VITE_DODU_API_BASE_URL` 설정 시 입장 화면이 실제 `/api/access/enter`를 호출하고 브라우저 세션 저장소에 토큰을 보관한다. 미설정 환경은 기존 mock preview를 유지한다.
- 검증: `frontend/npm run build`, `frontend/npm run lint`, `git diff --check` 통과.
- 미완료: 실제 Render API 주소를 넣은 운영 FE smoke test 및 약속·사진·이력 화면 연결.
### FE 홈 활성 약속 조회 연결

- 구현: 실제 API base URL과 세션 토큰이 있으면 `/api/promises/active`를 호출하고 KST 화면 모델로 변환해 홈에 표시한다.
- 검증: `frontend/npm run build`, `frontend/npm run lint`, `git diff --check` 통과.
- 미완료: 약속 생성·수정·취소·사진·이력 화면의 실제 mutation/조회 wiring과 운영 API QA.
### SCRUM-23·SCRUM-87 / 서버 기반 약속 이력 조회 및 FE 연결 — 2026-11-02

- 구현: `GET /api/promises/history`가 참가자 소유 약속 원장의 종료 상태와 마감 후 미응답 약속을 반환한다. FE 로그인 시 이 API를 호출해 주간 지난 약속과 이력 화면에 반영한다.
- 상태 매핑: `AuthCompleted`는 인증 완료, `AuthIncomplete`와 기술 실패는 미완료로 표시하며 `Cancelled`·`Withdrawn`은 인증 이력에서 제외한다. `Evaluating` 지연 결과는 자동 미완료 처리하지 않는다.
- 보완: API가 반환하는 마감 후 `Scheduled`·`AuthAvailable` 상태도 미완료로 표시한다.
- 검증: PromiseController Testcontainers 통합 테스트 통과, 전체 백엔드 233건 통과, FE API 계약·홈·사진·플랫폼 테스트 통과, FE build 통과, lint 경고만 존재.
- 남은 범위: 실제 배포 API와의 smoke test, 전체 Swagger 결과 기록, 운영 QA 및 Jira 완료 승인은 별도 진행.
## 2026-11-02 / 주간 요일 색상 및 취소창 문구 최종 보정

- 주간 약속 카드의 토요일·일요일 라벨을 기본 텍스트 색상으로 복원함.
- 취소 확인창의 보조 버튼 문구를 `돌아가기`에서 `닫기`로 변경함.
- 취소 확정 동작과 모달 포커스 흐름은 변경하지 않음.
- 검증: FE 테스트·빌드 및 `git diff --check`.
## 2026-11-02 / 클릭 중심 배포 설정

- 완료 범위: Render Blueprint, 백엔드 멀티스테이지 Dockerfile, Vercel Vite 설정, Spring Boot `PORT`·호스팅 DB URL·명시적 CORS origin 환경변수 지원, 배포 클릭 가이드.
- 파일: `render.yaml`, `deploy/backend.Dockerfile`, `.dockerignore`, `frontend/vercel.json`, `docs/operations/DEPLOYMENT_CLICK_GUIDE.md`.
- 검증 범위: Docker 이미지 빌드 성공, `npm run verify` 성공(프런트 테스트·빌드, 백엔드 233개 테스트·패키지), 비밀값 검사 성공, `git diff --check` 통과. 실제 Render·Vercel·Supabase 계정 배포와 외부 smoke test는 계정 입력 후 진행한다.
- Jira: SCRUM-70 운영 배포 경계와 SCRUM-83 사진 저장소 연동 범위에 구현 근거를 남긴다. 외부 계정 설정 전에는 완료 처리하지 않는다.
