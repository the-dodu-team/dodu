# 개발 결정 기록

## DEV-20261013-01 — Notion 최신 작업명 정책 및 구현 근거 동기화

- 배경: 사용자 요청으로 Notion 신규 작업과 Jira를 최신화한다. 페이지 본문과 10월13일 MD 첨부가 달라 첨부를 직접 확인했다.
- 선택·근거: LOCKED 첨부 §1.3의 작업명 최대30자·단일행·공백제거·빈값/제어문자 거절을 파생 문서와 기존 티켓31/23/24/60/69에 반영한다. PR9/10/11 병합과 Jira7/10/12 완료는 보존하고 Jira55/56/58의 완료 기반/잔여 연결을 구분한다.
- 승인 근거: Notion 최종 운영정책의 `Service_Operation_Policy_v2.5_Baseline_LOCKED_20261013_UPDATED.md` 및 사용자의 최신화 요청(2026-10-13). 별도 담당자의 추가 승인 사실은 추정하지 않는다.
- 대안: 이전 이미지20자 또는 개발 제안50자 적용, 본문만 읽고 신규 정책 누락, V1/계약을 다시 미완료로 분류하는 방식은 선택하지 않는다. 최근 작업명5개는 현행 첨부에서 확인되지 않아 확정 구현으로 추가하지 않는다.
- 영향: 원문/앱코드/DB V1 변경 없음. 새 티켓·Sprint3·담당/상태 재배치 없음. 문자 계산·최근목록 범위와 보안 운영 OPEN 사항은 유지한다. 민감 test 파일과 키는 제외한다.
- 검증: 최신 원문·PR/Jira·V1 SQL 대조, git diff --check 및 변경파일 범위 확인. 앱 테스트·DB·AI·기기QA 재실행 없음. 지정 기준 페이지404로 해당 하위 전체 확인 불가; 접근 가능한 최종 페이지만 확인했다.

## DEV-20261010-01 — DB 선택(PostgreSQL)과 Flyway 핵심 체인 V1

- 날짜: 2026-10-10
- 관련 커밋 제목 / Jira: `feat: [SCRUM-10] add PostgreSQL/Flyway baseline and core schema V1` / SCRUM-10.
- 변경 파일: DB_SELECTION.md, DATA_MODEL.md, V1__core_schema_draft.sql,
  CoreSchemaMigrationTests.java, docker-compose.yml, .env.example,
  pom.xml, application.yml(application.properties 대체), Decisions.md
- 근거: `docs/dodu/DATA_MODEL.md`(논리 모델·제약 후보), `docs/dodu/OPEN_DECISIONS.md` D03·D06·D08·D10·D11, `docs/dodu/DATA_MODEL_GUIDE.md`.
- 대안 및 선택 이유: PostgreSQL을 택한 이유는 부분 유니크 인덱스로 활성 약속 1개·Evaluating 사진 1개 제약을 DB에서 보장할 수 있기 때문입니다. MySQL은 같은 제약에 슬롯 테이블이나 잠금이 필요하고, H2는 PostgreSQL 전용 기능을 검증하지 못해 제외했습니다. V1은 약속·시도·이벤트·설문 연결의 핵심 9개 테이블과 SCRUM-22 구현이 바로 쓸 인증 4개 테이블(접근 자격·기기·세션·알림 구독)을 만들고, 권한 기록·알림 발송·사진·삭제 테이블은 계약이 정해진 뒤 추가합니다. 인증 테이블은 DATA_MODEL의 컬럼과 제약 후보 11번을 그대로 옮겼고, 재발급·세션 종료 처리 방식은 SCRUM-12·22에서 확정합니다. V1이 머지되기 전이라 V1에 포함했고, 머지 후 구조가 바뀌면 새 마이그레이션으로 고칩니다. ORM은 선택하지 않았습니다. 설정 파일은 앞으로 항목이 늘어날 것을 대비해 가독성이 좋은 `application.yml`로 바꿨고, 기존 `application.properties`는 같은 내용을 옮긴 뒤 삭제했습니다(둘 다 두면 같은 키가 properties 값으로 덮이기 때문).
- 영향·주의점: 상태 값(enum)은 확정되지 않아 CHECK로 고정하지 않았고, 참가자당 활성 약속 1개 제약은 상태 값이 정해질 때까지 주석으로만 남겼습니다. 앱이 DB 접속 정보를 요구하게 되어 DoduApplicationTests가 Testcontainers의 PostgreSQL 컨테이너에 연결하도록 수정했습니다(TestcontainersConfiguration 추가, @Import).
- 검증: [x] 빈 PostgreSQL 16 컨테이너에서 V1 적용, 테이블 13개 생성 확인(CoreSchemaMigrationTests), [x] 재실행 시 추가 적용 0건, 제약 위반 케이스가 의도한 제약명으로 거부됨(같은 클래스, 테스트 12개 통과, Windows·Docker Desktop·Testcontainers 2.0.5), [x] 기존 테스트 포함 전체 28개 통과(.\mvnw.cmd test, BUILD SUCCESS)

## DEV-20261009-01 — PR #9 리뷰 반영: 계약 오류 수정과 확인 결과 정리

- 날짜: 2026-10-09
- 관련 커밋 제목 / Jira: `docs: address PR #9 review feedback on API contract` / SCRUM-7.
- 변경 파일: `docs/api/COMMON_API_CONTRACT.md`, `docs/api/TIME_CONTRACT.md`, `docs/api/fixtures/README.md`, `docs/DEVELOPMENT_CONVENTIONS.md`(한 문장), `docs/api/fixture/` → `docs/api/fixtures/` 폴더 이름 변경, `Decisions.md`.
- 배경: 리뷰에서 계약 3.2 오류 예시에 TECHNICAL 전용인 `retryable`이 들어 있다는 점과, 문서에 적은 `docs/api/fixtures/` 경로가 실제 폴더 `docs/api/fixture/`와 달라 검증 명령이 파일을 찾지 못한다는 점이 지적됐습니다. 강민님은 7장 확인 사항(Q1~Q7)에 답하면서 확정 근거(D06·D11)가 있는 답과 기술 제안을 구분해 달라고 하셨습니다.
- 결정: 3.2 예시에서 `retryable`을 빼고 `retryable: true`가 자동 재시도 허가가 아니라는 설명을 더했습니다. 폴더 이름을 `fixtures`로 바꿔 문서의 경로와 맞췄습니다. Q1(`+5분`)과 Q2(정확히 T의 수정·취소 불가)는 확정 결정에 근거한 답으로 반영하고, 응답 시각 `+09:00`·health 예외·`SESSION_REPLACED`/`CREDENTIAL_REVOKED` 오류 코드·기존 티켓 연결은 기술 제안으로 표시해 확정으로 취급하지 않았습니다. 7장은 확인 결과 표로 바꿨습니다. Q3(컨벤션 문서의 "D11 승인 전" 문장)은 D11이 승인됐고 계약 문서와 어긋나지 않도록 같은 PR에서 한 문장만 "승인된 D11(2026-09-27)을 따른다"로 고쳤습니다. 컨벤션이 정책을 다시 정의하지 않습니다. 병합 후에도 맞도록 문서 상태 문구에서 "리뷰 전"·"반영 중" 같은 진행 상태 표현과 리뷰어 표기를 빼고, "초안이며 기술 제안은 확정 정책이 아니다"로 정리했습니다.
- 근거: PR #9 리뷰(CodeRabbit 인라인 2건, 강민님 코멘트), `docs/dodu/OPEN_DECISIONS.md` D06·D11(2026-09-27 APPROVED), `docs/dodu/DATA_MODEL.md` 참가자 세션·자격 규칙.
- 대안 및 선택 이유: 폴더 이름 대신 문서의 경로를 `fixture`로 고치는 방법도 있었지만, 문서 여러 곳과 Decisions가 이미 `fixtures`로 적혀 있고 복수형이 일반적이라 폴더를 바꿨습니다. 정책 원문(`SERVICE_POLICY_v2.5.md`)의 `+30분`은 원문을 고치지 않고 이전 기준이라는 차이만 기록했습니다.
- 영향·주의점: 문서만 바뀌고 코드·`HealthController`는 바뀌지 않았습니다. 오류 코드 이름은 모두 제안이며 미확정입니다. 429·Retry-After(SCRUM-70)와 상태 전이·결과 전달·멱등 계약은 후속 티켓(SCRUM-60·62·63·64·56)에서 정합니다.
- 검증: 문서 전용 변경이라 앱 테스트는 실행하지 않습니다.

## DEV-20261007-08 — 시간 계약 초안 추가

- 날짜: 2026-10-07
- 관련 커밋 제목 / Jira: `docs: add time contract draft` / SCRUM-7 (이전 DODU-16), 분할 초안 SPLIT-16-3.
- 변경 파일: `docs/api/TIME_CONTRACT.md`, `docs/api/COMMON_API_CONTRACT.md`(4장 5번 링크 문구), `Decisions.md`.
- 배경: 예약 범위, 알림, 인증 종료, 일자 제한이 모두 시간 계산에 의존합니다. 확정된 경계(D03·D04·D10·D11)와 이미 `dev`에 있는 `InterventionWindowPolicy`(SCRUM-64)를 문서로 맞춰야 FE·BE가 같은 가정으로 이후 API 구현(SCRUM-23 등)을 진행합니다.
- 결정: 기준은 `Asia/Seoul`, 시점은 오프셋 포함 ISO 8601로 전달하고 저장은 `DATA_MODEL.md`의 UTC 순간 + KST 일자 별도 보존 방식을 따릅니다. 서버는 주입된 `Clock`에서 현재 시각을 얻고, KST 일자는 서버가 계산해 `...DateKst`로 내려줍니다. 확정 경계(예약 `now+5분 ≤ T ≤ now+72시간`, 제출 `T ≤ 접수 < T+30`, `T+30 ≤ 공식 종료`)는 새로 정하지 않고 시간 계산 예시로 풀어 적었습니다. 구현 코드는 이 문서에 두지 않습니다.
- 근거: `docs/dodu/OPEN_DECISIONS.md` D03·D04·D10·D11(2026-09-27 APPROVED), `docs/dodu/DATA_MODEL.md`, `docs/dodu/sources/SERVICE_POLICY_v2.5.md` §0·§1·§2·§5, `docs/dodu/sources/FLOW_LOG_MAPPING_v2.5.md` §3~4, `docs/DEVELOPMENT_CONVENTIONS.md` "API·시간·상태 계약".
- 대안 및 선택 이유: 응답을 UTC(`Z`)로 통일하는 방안이 있습니다. 변환 책임이 FE에 생기므로 이 초안은 `+09:00`을 제안하고, 최종 선택은 강민님 확인 사항으로 남겼습니다.
- 영향·주의점: 서비스 정책 §1·§2의 `+30분`과 D11의 `+5분`이 달라 D11 기준으로 썼고 확인을 요청했습니다. 정확히 T인 순간의 수정·취소는 문서에 명시가 없어 정하지 않았습니다. 응답 시각 표기가 UTC로 바뀌면 계약 문서와 fixture의 시각 표기를 같은 PR에서 함께 고칩니다.
- 검증: 문서 전용 변경이라 앱 테스트는 실행하지 않습니다.

## DEV-20261007-07 — FE 검토용 API 응답 fixture 추가

- 날짜: 2026-10-07
- 관련 커밋 제목 / Jira: `docs: add API response fixtures` / SCRUM-7 (이전 DODU-16), 분할 초안 SPLIT-16-2.
- 변경 파일: `docs/api/fixtures/success.json`, `docs/api/fixtures/validation-error.json`, `docs/api/fixtures/technical-error.json`, `docs/api/fixtures/invalid-responses.json`, `docs/api/fixtures/README.md`, `Decisions.md`.
- 배경: FE가 서버 없이 정상·입력오류·기술오류 상태를 재현하고, 계약에 맞지 않는 응답을 성공으로 표시하지 않는지 확인하려면 계약에 맞는 예시 파일이 필요합니다.
- 결정: 계약 문서 5장의 예시를 응답 본문 그대로 JSON 파일로 분리하고, HTTP 상태는 README 표에서 관리합니다. 계약에 맞지 않는 응답은 정상 fixture와 분리한 `invalid-responses.json`에 이름을 붙인 사례로 둡니다. 모든 값은 가상 데이터입니다.
- 근거: `docs/api/COMMON_API_CONTRACT.md` 3~5장, `docs/jira/WORK_BREAKDOWN.md`의 SPLIT-16-2·SPLIT-18-2, `docs/DEVELOPMENT_CONVENTIONS.md`(문서 예시는 가상 데이터).
- 대안 및 선택 이유: 파일 본문에 `httpStatus`를 함께 담는 래퍼 형식은 실제 응답 본문과 달라져 FE가 계약 응답과 구분하기 어려워 채택하지 않았습니다.
- 영향·주의점: 계약 초안이 강민님 리뷰로 바뀌면 fixture도 같은 PR에서 고칩니다. 예약·제출 경계값 사례는 BE 도메인 테스트(`InterventionWindowPolicyTests`)가 맡고, 상태 전이·멱등 계약이 필요한 사례(마감 직전 기술오류 복구, 지연된 판정, 중복 요청 등)는 계약이 정해진 뒤 추가합니다. 이 fixture를 FE mock에 연결하는 작업과 사용 범위 확정은 SPLIT-18-2 범위입니다.
- 검증: 문서·예시 전용 변경이라 앱 테스트는 실행하지 않습니다.

## DEV-20261007-06 — 공통 API 응답·오류 계약 초안 방향

- 날짜: 2026-10-07
- 관련 커밋 제목 / Jira: `docs: draft common API response and error contract` / SCRUM-7 (이전 DODU-16, S1-BE01), 분할 초안 SPLIT-16-1.
- 변경 파일: `docs/api/COMMON_API_CONTRACT.md`, `Decisions.md`.
- 배경: FE가 정상·입력오류·기술오류를 같은 규칙으로 처리하려면 응답·오류·시각 표현의 공통 계약이 필요합니다. 현재 구현된 HTTP API는 `GET /api/health`뿐이고, 예약·제출 시간 경계 판정은 `InterventionWindowPolicy`(SCRUM-64)로 도메인 코드에만 있으며 API에 연결되지 않았습니다.
- 결정: 성공·오류 응답을 `status` / `data` / `error` / `serverTime` 형태로 제안하고, 오류를 `error.type`(VALIDATION·UNAUTHENTICATED·FORBIDDEN·NOT_FOUND·CONFLICT·TECHNICAL)과 `error.code`로 구분합니다. 모든 시각은 오프셋이 포함된 ISO 8601로 전달합니다. 기술오류는 사용자 미인증으로 집계하지 않습니다. 확정된 결정(D03·D04·D06·D10·D11)의 경계 규칙은 오류 코드(`PROMISE_SCHEDULE_TOO_SOON`, `AUTH_WINDOW_EXPIRED` 등)로 반영하고, 코드 이름은 제안으로 둡니다.
- 근거: 서비스 운영 정책 v2.5 §0·§8, Flow·로그 매핑 v2.5 §3~4, `docs/dodu/OPEN_DECISIONS.md`(D01~D13, 2026-09-27 APPROVED), `docs/DEVELOPMENT_CONVENTIONS.md`의 API·시간·상태 계약(입력 오류 400 등 HTTP 의미 사용, 오프셋 포함 ISO 8601).
- 대안 및 선택 이유: 입력오류에 422를 쓰는 방식은 컨벤션이 400을 기준으로 해서 채택하지 않았습니다.
- 영향·주의점: 확정된 제품 API 명세가 아니라 신규 API의 작성 기준 제안입니다. 상태 전이표·판정 결과 전달 방식·멱등키 방식은 이 계약의 범위 밖이며 별도 계약이 필요합니다. 서비스 정책 §1의 최소 예약 `+30분`과 확정 결정 D11·구현의 `+5분`이 달라 D11을 기준으로 썼고 강민님 확인 사항으로 남겼습니다. 기존 `GET /api/health` 응답이 계약 형식과 달라 이것도 확인 사항입니다.
- 검증: 문서 전용 변경이라 앱 테스트는 실행하지 않습니다. `GET /api/health`를 로컬에서 호출해 응답 형태(`{"service":"dodu-backend","status":"ok"}`)를 확인하고 §2에 반영했습니다. 최신 `dev`의 `OPEN_DECISIONS.md`, `InterventionWindowPolicy`와 대조해 확정 결정을 반영했습니다.

## DEV-20261007-05 — 공통 UI 통합 후 홈 화면 유지

- 날짜: 2026-10-07. 관련 Jira/PR: SCRUM-68 / #7, 선행 #6.
- 배경·선택: 선행 공통 UI가 Squash로 dev에 들어오면서 공통 파일과 홈 확장 사이의 충돌이 발생했습니다. 헤더 미리보기 수정은 유지하고 홈 진입점·카드·스타일은 홈 PR 버전으로 유지합니다. 양쪽 결정 기록도 보존합니다.
- 대안·영향: 공통 UI 미리보기로 홈을 대체하거나 기존 리뷰 수정을 버리지 않습니다. 원격 이력 재작성·브랜치 삭제 없이 merge로 통합합니다.
- 검증: 통합 뒤 npm run verify(미리보기 6개·백엔드 16개)와 충돌 마커 검사를 진행합니다. 실제 기기/스크린리더/API 검증은 후속 SCRUM-69로 분리합니다.

## DEV-20261007-04 — 홈 UI PR의 최신 dev 동기화

- 날짜: 2026-10-07. 관련 Jira/PR: SCRUM-68 / PR #7.
- 배경·선택: 최신 dev의 승인 정책·인증 기반과 홈 UI 리뷰 수정의 결정 기록을 모두 보존해 merge로 동기화합니다.
- 대안·영향: 기존 dev 기록 삭제와 force push는 하지 않습니다. 프런트만 구현된 것처럼 백엔드 기반을 잃지 않게 합니다. README의 기반/미리보기 범위도 함께 유지합니다.
- 검증: 병합 뒤 npm run verify를 재실행해 백엔드 16개 및 미리보기 6개 테스트 결과 확인. 기기·실제 API QA는 미완료입니다.

## DEV-20261007-02 — 동일 약속 재확인의 미리보기 상태 보존

- 날짜: 2026-10-07
- 관련 Jira/PR: SCRUM-68 / PR #7, CodeRabbit 4182277976.
- 변경 파일: App.tsx, reconfirmPromise.ts와 Node 테스트, frontend/package.json, scripts/workflow.mjs, README.md, Decisions.md.
- 배경: 동일 약속 내용을 재확인하면 인증 완료 미리보기가 pending으로 초기화됩니다.
- 선택·근거: 날짜·정규화한 작업명·예정 시각·도구가 모두 같은 경우만 기존 미리보기 상태를 보존하며 변경 시 pending으로 분리합니다. React 함수형 상태 갱신을 사용합니다. 사용자 요청에 따른 리뷰 결함 수정이며 실제 서버 인증이나 승인 정책을 변경하지 않습니다.
- 대안: 무조건 상태 유지나 초기화는 각각 변경된 약속 오인/동일 약속 상태 유실을 초래합니다. 새 테스트 라이브러리 대신 Node 내장 테스트와 타입 제거 기능을 사용합니다. mjs 테스트는 Node 전용이라 브라우저 TypeScript 범위와 분리합니다.
- 영향: 기존 코드를 주석 보존하며 순수 함수로 검증합니다. 새로운 패키지·DB·API 변경 없음. 전체 UI·기기 QA를 대체하지 않습니다.
- 검증: 커밋 전 npm run verify 및 신규 상태 전이 테스트 6개 결과를 확인합니다. 실행 명령은 README에 기록합니다.
- 최신 문서: Notion 404. 사용자가 전달한 승인 D01~D13은 승인 전으로 되돌리지 않으며 본 수정은 미리보기 내부 처리에 한정합니다.

## DEV-20261007-03 — 공통 UI PR의 최신 dev 동기화

- 날짜: 2026-10-07
- 관련 PR: #6. 변경: Decisions.md 충돌 정리 및 최신 dev 통합.
- 배경·선택: 기존 PR #3이 dev에 병합된 상태에서 결정 기록의 선두 추가가 충돌했습니다. 양쪽 기록을 모두 보존하고 merge commit으로 동기화하며 강제 push하지 않습니다.
- 근거·대안: 사용자 프런트 PR 인수 요청. dev 코드를 버리거나 리뷰 수정을 지우는 해결은 사용하지 않습니다.
- 영향: 최신 backend 기반을 유지하며 공통 UI 범위만 PR diff에 남깁니다. 의존 홈 브랜치가 있어 원격 브랜치를 삭제하지 않습니다.
- 검증: 커밋 전 npm run verify 재실행 결과 확인 및 충돌 마커·공백 검사. 기기 QA 미실행.

## DEV-20261007-01 — 공통 UI 미리보기 헤더 연결

- 날짜: 2026-10-07
- 관련 Jira/PR: SCRUM-8 / PR #6, CodeRabbit 4180740896.
- 변경 파일: frontend/src/ComponentPreview.tsx, Decisions.md.
- 배경·선택: 실제 진입점의 미리보기에 공통 헤더가 누락되어 기존 AppHeader를 추가합니다. 별도 헤더 복제나 진입점 변경은 하지 않습니다.
- 근거: 사용자의 프런트 PR 인수·리뷰 수정 요청 및 PR #6 공통 UI 검토 범위.
- 영향: 개발용 미리보기만 변경하며 제품 정책·서버·DB·비밀값은 변경하지 않습니다. 기존 코드와 주석을 보존합니다.
- 검증: 기존 컴포넌트 import·렌더 연결 확인. 커밋 전 npm run verify 결과를 확인합니다. 기기·스크린리더 QA는 미실행입니다.
- 최신 문서: 지정 Notion 기준 페이지 404로 최신 하위 항목 확인 불가.

## DEV-20261004-02 — 정상 접수 사진의 마감 후 판정 허용

- 날짜: 2026-10-04
- 관련 커밋 제목 / Jira: `feat(SCRUM-64): preserve accepted photo evaluation after deadline` / SCRUM-64.
- 변경 파일: `AcceptedPhotoEvaluationPolicy.java`, 해당 테스트, `Decisions.md`.
- 배경: 신규 제출 마감을 판정 결과 반영 시점에도 적용하면 마감 직전에 정상 접수된 사진이 사용자 미인증으로 잘못 처리될 수 있습니다.
- 결정: 서버의 유효 접수 기록이 `T ≤ valid_received_at < T+30`이고 현재 attempt가 Evaluating인 경우 판정 결과 반영을 허용하는 순수 정책을 추가합니다. 현재 시각이나 공식 종료시각을 다시 마감 조건으로 사용하지 않습니다. 참여 철회와 유효 접수 기록 누락은 거절합니다.
- 근거: 사용자 승인 D03/D04/D10/D11, 서비스 운영 정책 v2.5 §5의 접수·판정 구분 및 §13의 철회 후 신규 수집 중단, 제품 불변 조건의 철회 후 결과 방어.
- 대안 및 선택 이유: 신규 사진 제출 정책을 재사용하는 방식은 마감 후 유효 시도를 거절하므로 선택하지 않습니다. 클라이언트 시각이나 단순 requestReceivedAt을 유효 접수 증거로 사용하지 않습니다.
- 영향·주의점: 도메인 정책 기반 구현이며 API/DB 결과 반영은 아직 연결하지 않았습니다. 호출자는 서버에 저장된 유효 접수 시각과 참가자 상태를 읽고, 소유권·현재 attempt 검사와 결과 반영을 같은 상태 트랜잭션으로 처리해야 합니다. 내부 기술복구 및 다중 서버 동시성 보장은 별도 작업입니다. 사진·접근정보를 저장하거나 로그에 출력하지 않습니다. 새 제품 정책이나 OPEN 항목을 확정하지 않았습니다.
- 검증: `npm run verify` 통과 — lint, TypeScript, 프런트 빌드, 백엔드 16개 테스트(실패 0), JAR 패키징. 신규 개입 종료 뒤에도 T/마감 직전 접수는 판정 가능, T 이전/정확히 T+30 접수 및 접수 기록 누락은 거절, 철회 및 Evaluating 이외 상태의 결과 반영 거절을 검증했습니다.
- 최신 문서 확인: 지정 Notion 기준 페이지는 404로 최신 내용과 하위 항목을 확인하지 못했습니다. 승인된 사용자 결정과 로컬 원문을 근거로 구현했습니다.

## DEV-20261004-01 — 원자적 사진 판정 슬롯 예약

- 날짜: 2026-10-04
- 관련 Jira: SCRUM-64; CodeRabbit PR #3의 동시 제출 검토 의견.
- 변경 파일: `PhotoEvaluationReservations.java`, 해당 테스트, `InterventionWindowPolicy.java`, `README.md`, `Decisions.md`.
- 배경: boolean 상태 확인만으로는 동시에 진입한 요청 둘이 모두 판정을 시작할 수 있습니다.
- 선택·근거: D10에 따라 ConcurrentHashMap.putIfAbsent로 약속별 슬롯을 원자적으로 예약합니다. 같은 attempt의 재진입도 중복 예약을 허용하지 않습니다. release는 약속과 attempt가 모두 일치할 때만 실행하여 이전 결과가 새로운 판정을 해제하지 못하게 합니다.
- 대안: 서버 전체 synchronized 잠금은 다른 약속도 직렬화하므로 사용하지 않습니다. 키별 해시 연산을 사용하며 실제 속도 향상 수치는 측정하지 않았습니다.
- 영향·보안: 단일 프로세스의 기반 구현입니다. API 호출 및 여러 서버의 DB 트랜잭션 연결은 남아 있으므로 CodeRabbit 지적의 운영상 해결 완료로 표시하지 않습니다. 호출자는 참가자 소유권을 검증해야 하며 사진·토큰을 저장하거나 로그에 남기지 않습니다. 작업 종료 시 슬롯 해제가 필요합니다.
- 검증: npm run verify 통과 (lint·TypeScript·프런트 빌드·백엔드 13개 테스트·JAR 패키징). 16개 동시 요청 중 하나만 예약, retry 중복 시작 차단, 오래된 release 방어, 다른 약속 독립 실행을 검증했습니다.
- 최신 문서 확인: 지정 Notion 기준 페이지는 404로 하위 항목을 확인하지 못했습니다. 사용자 승인 D10과 로컬 정책 §5를 근거로 진행했습니다.

## DEV-20261003-03 — 사진 제출 attempt 접수 기록 모델 추가

- 날짜: 2026-10-03
- 관련 커밋 제목 / Jira: `feat(SCRUM-64): add photo attempt record` / SCRUM-64
- 변경 파일: `backend/src/main/java/com/dodu/intervention/PhotoAttempt.java`, `backend/src/test/java/com/dodu/intervention/PhotoAttemptTests.java`, `Decisions.md`
- 배경: D08의 약속·attempt·event 분리를 실제 백엔드 코드에 단계적으로 반영하기 위해, 영속 저장소가 없는 현재 저장소에서 먼저 서버가 기록해야 할 attempt 접수 모델을 고정합니다.
- 결정: `PhotoAttempt`를 불변 record로 만들고 `promiseId`, `clientRequestId`, `requestFingerprint`, 도구 유형, 서버 `requestReceivedAt`, 처리 상태를 필수로 둡니다. 새 접수 attempt의 초기 상태는 `RECEIVED`입니다.
- 근거: 논리 ERD `AUTH_ATTEMPT` 필드와 D08의 서버 접수·retry·새 사진 제출 분리 원칙.
- 대안 및 선택 이유: JPA 엔티티·DB repository·사진 파일 참조를 이번 커밋에 추가하지 않습니다. 실제 DB 제품과 마이그레이션이 결정되기 전에는 불변 도메인 모델로 계약을 먼저 검증합니다.
- 영향·주의점: 현재 record는 메모리 객체이며 재시작 후 보존되지 않습니다. 다음 저장소 작업에서 `(promise_id, client_request_id)` 유일 제약과 원자적 저장을 연결해야 합니다.
- 검증: `backend\\mvnw.cmd test` 실행 결과 테스트 11건, 실패 0건으로 BUILD SUCCESS; `git diff --check` 통과.

## DEV-20261003-02 — 사진 제출 retry와 새 attempt 식별

- 날짜: 2026-10-03
- 관련 커밋 제목 / Jira: `feat(SCRUM-64): classify photo attempt identity` / SCRUM-64
- 변경 파일: `backend/src/main/java/com/dodu/intervention/PhotoAttemptIdentityPolicy.java`, `backend/src/test/java/com/dodu/intervention/PhotoAttemptIdentityPolicyTests.java`, `Decisions.md`
- 배경: D08에 따라 네트워크 retry는 새 attempt가 아니고, 거절 후 새 사진 제출은 새 attempt로 기록해야 합니다.
- 결정: 동일 약속 안에서 `client_request_id`와 `request_fingerprint`가 모두 같으면 `RETRY_EXISTING`, request ID가 다르면 `NEW_ATTEMPT`, request ID는 같지만 fingerprint가 다르면 `CONFLICT`로 분류합니다.
- 근거: Notion LOCKED D08 및 논리 ERD의 `(promise_id, client_request_id)` 유일 키와 `request_fingerprint` 비교 규칙.
- 대안 및 선택 이유: 이번 커밋은 저장소·API·파일 업로드를 포함하지 않고 attempt 생성 전에 사용할 순수 식별 정책만 구현해 재전송과 신규 제출의 기준을 먼저 고정합니다.
- 영향·주의점: 실제 DB에서는 `promise_id`와 `client_request_id` 복합 유일 제약 및 충돌 시 원자적 처리가 필요합니다. 서로 다른 request ID가 실제 새 사진인지 확인하는 입력 계약은 업로드 유스케이스에서 검증합니다.
- 검증: `backend\\mvnw.cmd test` 실행 결과 테스트 10건, 실패 0건으로 BUILD SUCCESS; `git diff --check` 통과.

## DEV-20261003-01 — Evaluating 중 추가 사진 제출 차단

- 날짜: 2026-10-03
- 관련 커밋 제목 / Jira: `feat(SCRUM-64): block concurrent photo submission` / SCRUM-64
- 변경 파일: `backend/src/main/java/com/dodu/intervention/InterventionWindowPolicy.java`, `backend/src/test/java/com/dodu/intervention/InterventionWindowPolicyTests.java`, `Decisions.md`
- 배경: D10에 따라 한 약속에서 동시에 판정 중인 사진은 최대 1개이며, `Evaluating` 중 추가 사진 제출은 허용하지 않습니다.
- 결정: 현재 해당 약속에 판정이 진행 중인지 나타내는 서버 상태를 입력받아, 진행 중이면 추가 제출을 거절하고 진행 중이 아니면 제출을 허용하는 정책 함수를 추가합니다.
- 근거: Notion LOCKED D10 및 원문 Flow 상태 전이의 `AuthAvailable → Evaluating` 규칙.
- 대안 및 선택 이유: 이번 커밋에는 attempt 저장·동시성 잠금·상태 전이 트랜잭션을 함께 넣지 않고, 제출 전 정책 게이트만 독립적으로 구현합니다. 저장과 원자적 전이는 다음 소단위 작업에서 다룹니다.
- 영향·주의점: 현재 함수는 순수 정책 판정이며 실제 API/DB 조회와 원자적 잠금에는 아직 연결되지 않았습니다. 실제 구현에서는 약속별 Evaluating 행을 잠그거나 원자적으로 확인해야 합니다.
- 검증: `backend\\mvnw.cmd test` 실행 결과 테스트 7건, 실패 0건으로 BUILD SUCCESS; `git diff --check` 통과.

## DEV-20260928-05 — 사진 신규 제출의 T/T+30 서버 경계 검증

- 날짜: 2026-09-28
- 관련 커밋 제목 / Jira: `feat(SCRUM-64): validate photo submission window` / SCRUM-64
- 변경 파일: `backend/src/main/java/com/dodu/intervention/InterventionWindowPolicy.java`, `backend/src/test/java/com/dodu/intervention/InterventionWindowPolicyTests.java`, `Decisions.md`
- 배경: D04·D11에 따라 사진 신규 제출은 약속 시각 T부터 T+30 직전까지만 허용하고, 마감 전에 정상 접수된 시도는 이후 판정할 수 있어야 합니다.
- 결정: 서버 `Clock`의 접수 시각을 기준으로 정확히 T는 허용하고, T 이전은 `BEFORE_START`, 정확히 T+30 이상은 `WINDOW_EXPIRED`로 판정합니다.
- 근거: Notion 및 LOCKED 기준의 D04·D10·D11 최종 결정. 클라이언트 시각이나 업로드 완료 시각이 아니라 서버 접수 시각을 사용합니다.
- 대안 및 선택 이유: 이번 커밋에는 사진 저장·판정 콜백·마감 후 기존 시도 처리까지 넣지 않고, 신규 제출 가능 여부라는 순수 정책 함수만 분리했습니다.
- 영향·주의점: 현재는 정책 함수와 단위 테스트만 추가되어 실제 업로드 API에 연결되지는 않았습니다. 다음 사진 관련 작업에서 접수 attempt와 판정 상태에 연결합니다.
- 검증: `backend\\mvnw.cmd test` 실행 결과 테스트 6건, 실패 0건으로 BUILD SUCCESS; `git diff --check` 통과.

## DEV-20260928-04 — 참가자 코드의 단일 활성 기기·세션 정책 반영

- 날짜: 2026-09-28
- 관련 커밋 제목 / Jira: `docs: model single active participant session` / SCRUM-56·SCRUM-57
- 변경 파일: `docs/dodu/DATA_MODEL.md`, `docs/dodu/DATA_MODEL_GUIDE.md`, `docs/dodu/IMPLEMENTATION_CONTEXT.md`, `docs/dodu/ACCEPTANCE_CHECKLIST.md`, `Decisions.md`
- 배경: 참가자 코드는 다른 기기에서 재사용할 수 있지만 참가자별 활성 기기·세션은 하나만 허용하고, 새 기기 활성화 시 기존 세션과 알림 구독을 종료해야 한다는 추가 정책이 전달되었습니다.
- 결정: ERD에 참가자별 불투명 기기 식별 상태(`PARTICIPANT_DEVICE`), 접근 세션(`PARTICIPANT_SESSION`), 세션별 알림 구독(`NOTIFICATION_SUBSCRIPTION`)을 분리합니다. 새 기기 진입은 기존 활성 세션·구독을 비활성화하고 새 세션·구독을 활성화하며, 약속·인증·자기보고·행동로그는 계속 `participant_id`에 귀속합니다. 코드 분실·노출은 기존 `ACCESS_CREDENTIAL` 폐기와 교체 발급으로 처리합니다.
- 근거: 사용자가 전달한 동시접속·다중기기 정책, 기존 v2.5의 참가자 접근·재발급·알림 권한 요구.
- 대안 및 선택 이유: 기기 ID를 약속·인증 데이터에 직접 연결하지 않고 세션·구독을 별도 행으로 분리했습니다. 그래야 새 기기 교체가 업무 데이터의 소유자나 분석 분모를 바꾸지 않습니다.
- 영향·주의점: DB 제품·물리 인덱스·토큰 저장 방식은 아직 미정입니다. 실제 구현에서는 참가자당 활성 세션 1개, 참가자 범위의 기기 키 유일성, 활성 세션의 구독만 허용하는 제약을 트랜잭션과 함께 구현해야 합니다.
- 검증: `git diff --check` 통과. 문서·ERD·수용 기준만 변경했으므로 애플리케이션 테스트와 실제 DB 검증은 실행하지 않았습니다.

## DEV-20260928-03 — 공식 종료 시각의 신규 개입 생성 차단

- 날짜: 2026-09-28
- 관련 커밋 제목 / Jira: `feat(SCRUM-64): close intervention creation at official end` / SCRUM-64
- 변경 파일: `backend/src/main/java/com/dodu/intervention/InterventionWindowPolicy.java`, `backend/src/test/java/com/dodu/intervention/InterventionWindowPolicyTests.java`, `Decisions.md`
- 배경: D03에 따라 공식 행동검증 종료 후에는 신규 약속과 신규 개입을 중단해야 하며, 이 규칙을 일정 유효성 검증과 별도 소단위로 구현합니다.
- 결정: 서버 `Clock`의 현재 시각이 공식 종료 시각보다 엄격히 이전일 때만 신규 개입 생성을 허용합니다. 종료 시각과 종료 이후는 모두 차단합니다.
- 근거: Notion 및 LOCKED 기준의 D03 최종 결정. 공식 종료 후 신규 약속·수정·재생성·기술복구·신규 개입을 중단합니다.
- 대안 및 선택 이유: 기존 예약의 T+30 접수·판정 완료 규칙이나 API/DB 연결을 이번 커밋에 포함하지 않고, 신규 개입 생성 게이트만 독립적으로 검증합니다.
- 영향·주의점: 현재는 정책 함수와 단위 테스트만 추가되어 실제 생성 API에 연결되지는 않았습니다. 기존 약속의 사후 판정 허용은 별도 사진 제출 정책 작업에서 다룹니다.
- 검증: `backend\\mvnw.cmd test` 실행 결과 테스트 5건, 실패 0건으로 BUILD SUCCESS; `git diff --check` 통과.

## DEV-20260928-02 — 서버 약속 일정 경계 검증 함수 도입

- 날짜: 2026-09-28
- 관련 커밋 제목 / Jira: `feat(SCRUM-64): validate promise schedule window` / SCRUM-64
- 변경 파일: `backend/src/main/java/com/dodu/intervention/InterventionWindowPolicy.java`, `backend/src/test/java/com/dodu/intervention/InterventionWindowPolicyTests.java`, `Decisions.md`
- 배경: SCRUM-64의 공식 행동검증 종료 경계 작업을 사용자의 요청대로 작은 기능 단위로 분리해, 첫 커밋은 약속 일정 유효성 검증만 구현합니다.
- 결정: 서버 `Clock`을 주입받는 정책 함수가 예약 가능 범위 `현재+5분~현재+72시간`과 전체 인증 구간 `T+30 ≤ 공식 종료시각`을 함께 검증하고, HTTP 예외 대신 도메인 결정 enum을 반환합니다.
- 근거: Notion 및 LOCKED 기준의 D03·D11 최종 결정. 모든 시간 경계는 서버 시간이 기준이며, 약속은 T부터 T+30까지 온전히 받을 수 있어야 합니다.
- 대안 및 선택 이유: 이번 커밋에 컨트롤러·DB·공식 종료 후 신규 개입 차단을 함께 넣지 않고, 동일 정책을 호출할 후속 소단위 작업으로 분리해 커밋과 검증 범위를 명확히 합니다.
- 영향·주의점: 현재는 순수 정책 함수와 단위 테스트만 추가되어 API·DB 동작은 아직 연결되지 않았습니다. 다음 커밋에서 공식 종료 이후 신규 개입 차단을 별도 구현합니다.
- 검증: `backend\\mvnw.cmd test` 실행 결과 테스트 4건, 실패 0건으로 BUILD SUCCESS; `git diff --check` 통과.

## DEV-20260928-01 — D01~D13 최종 결정에 따른 파생 문서·ERD·Sprint 2 갱신

- 날짜: 2026-09-28
- 관련 커밋 제목 / Jira: `docs: align implementation docs with locked v2.5 decisions` / SCRUM-55~65
- 변경 파일: `README.md`, `docs/dodu/IMPLEMENTATION_CONTEXT.md`, `docs/dodu/OPEN_DECISIONS.md`, `docs/dodu/DATA_MODEL.md`, `docs/dodu/DATA_MODEL_GUIDE.md`, `docs/dodu/ACCEPTANCE_CHECKLIST.md`
- 배경: Notion의 2026-09-27 LOCKED D01~D13 최종 결정문이 확정되었으나 파생 문서에는 D01~D13이 OPEN으로 남아 있었고, 예약 +30분·자기보고 48시간·동일 날짜 재생성 불가 등 이전 기준이 남아 있었습니다. 기존 Sprint 2 티켓만으로는 자기보고 스케줄과 공식 행동검증 종료 경계의 백엔드 작업이 분리되지 않았습니다.
- 결정: 파생 문서와 논리 ERD를 최신 결정에 맞춰 갱신합니다. 예약은 `now+5분~+72시간`, 사진 접수는 `T ≤ server_received_at < T+30`, 자기보고는 최종 T 기준 18:00/다음날 11:00·Push 1회·인앱 재안내 1회·24시간 만료로 기록합니다. ERD에 약속 날짜 사용 원장, 자기보고 대상/재안내 필드, 파생 약속 연결, 정책·모델·분석 제외 메타데이터를 추가합니다. Jira에는 SCRUM-63(자기보고 백엔드), SCRUM-64(공식 종료 경계), SCRUM-65(D13 QA 근거)를 추가하고 SCRUM-55~62의 설명과 완료 조건을 갱신했습니다.
- 근거: 사용자가 전달한 D01~D13 최종 결정문, Notion `MVP v2.5 — 개발 질의 D01~D13 최종 결정문` 및 LOCKED PRD/운영정책/Flow 기준.
- 대안 및 선택 이유: 원문 `docs/dodu/sources/`를 수정하는 대신 파생 문서만 갱신했습니다. API·DB·스케줄러의 구체 구현은 제품 정책과 분리된 기술설계로 남겼습니다.
- 영향·주의점: 실제 DB 드라이버·ORM·마이그레이션과 앱 코드는 아직 변경하지 않았습니다. Jira 티켓은 모두 `해야 할 일` 상태이며 구현 착수 시 진행 중으로 전환합니다. D13의 Go/부분 Go/판단 보류/No-go는 제품 상태가 아니라 검증 기록으로 관리합니다.
- 검증: `git diff --check`, 관련 JSON 파싱, 문서 내 구 기준 검색을 실행했습니다. 애플리케이션 테스트와 실제 DB 검증은 문서·Jira 변경만 수행했으므로 실행하지 않았습니다.

## DEV-20260922-02 — 설명 검토에 따른 ERD 관계·삭제 수명주기 보완

- 날짜: 2026-09-22
- 관련 커밋 / Jira: `docs: reconcile ERD relationships and lifecycle explanations` / DODU-19·21·24. CI 후속은 DODU-40.
- 변경 파일: `docs/dodu/DATA_MODEL.md`, `docs/dodu/DATA_MODEL_GUIDE.md`, `docs/MAINTENANCE_PLAN.md`, `README.md`, `Decisions.md`.
- 배경: 사용자가 ERD와 별도 설명 파일을 요구했고 설명 중 모순이 보이면 그림을 수정하도록 요청했습니다. 기존 초안에 작업명 누락, 이벤트 선택 관계와 그림 불일치, version 단독 UK, 삭제 후 연결 키 잔존 문제가 있었습니다.
- 결정: 작업명·공식 종료·별도 행 버전 등을 보완하고 선택적 FK와 1:1 UK, 복합 유일성, 삭제 완료 후 연결 제거를 명시합니다. 그림을 원본으로 두고 설명서의 행 단위·NULL·생성부터 삭제까지 문장과 상호 대조합니다.
- 근거: PRD F-01·F-08, 정책 §0~13·16, Flow §1.2·3~7, 수용 기준. Notion 최신 페이지는 401로 읽지 못했습니다.
- 대안·선택 이유: 그림만 추가하면 구조 오류를 발견하기 어려워 설명서와 수정 대조표를 작성합니다. Jenkins 추가는 별도 서버 운영 부담 때문에 보류하고 실제 빌드 CI·브랜치 보호를 우선 후속 작업으로 제안합니다.
- 영향: DB·제품 코드·원문은 변경하지 않습니다. OPEN에 의존하는 인증·알림·설문은 검토 후보이며 승인 전 운영 스키마로 구현하지 않습니다. 새 문서가 제품 정책을 승인하지 않습니다.
- 검증: Mermaid 구문과 문서 상대 링크, 필드·관계·NULL·유일 제약을 대조하고 diff 공백 검사를 실행합니다. 문서 변경이므로 애플리케이션 테스트와 실제 DB 검증은 미실행입니다.

## DEV-20260921-02 — v2.5 논리 ERD와 정책 결정 경계 분리

- 날짜: 2026-09-21
- 관련 커밋 제목 / Jira: `docs: define the v2.5 logical data model` / DODU-19, DODU-21, DODU-24.
- 변경 파일: `docs/dodu/DATA_MODEL.md`, `docs/dodu/IMPLEMENTATION_CONTEXT.md`, `Decisions.md`.
- 배경: DB 구현 전에 v2.5의 약속·인증·알림·자기보고·보관·삭제 요구를 하나의 추적 가능한 데이터 구조로 정리해야 합니다. 현재 백엔드에는 DB 드라이버·ORM·마이그레이션 도구가 없습니다.
- 결정: DB 제품과 물리 타입을 선택하기 전에 논리 ERD를 작성합니다. 현재 상태와 이력, 사진 파일과 메타데이터, 발송과 사용자 반응, 철회와 과거 데이터 삭제를 분리합니다.
- 근거: 서비스 정책 §1~14, Flow 로그 매핑 §3~5, 구현 컨텍스트의 기술설계 제안, 수용 기준의 동시성·보관·삭제·접근 분리 요구를 반영했습니다.
- 대안 및 선택 이유: 바로 JPA 엔티티와 마이그레이션을 만들면 DODU-19의 저장 기술 선택과 D01~D13의 미결정 정책을 구현 사실로 굳힐 수 있습니다. 논리 모델을 먼저 검토하면 제품 정책과 저장 기술 결정을 독립적으로 수정할 수 있습니다.
- 영향·주의점: ERD의 DB 타입은 개념 표기이며 승인된 물리 스키마가 아닙니다. OPEN 결정과 관련된 enum·전이·시간 끝점·설문 대상은 제약으로 확정하지 않았습니다.
- 검증: Mermaid 관계, 엔티티별 정책 근거, OPEN ID 영향표, 동시성·중복·보관·삭제 검증 질문을 문서 대조합니다. 코드 변경이 없어 애플리케이션 테스트는 실행하지 않습니다.

## DEV-20260920-02 — 분할 문서의 파일 끝 공백 정리

- 날짜: 2026-09-20
- 관련 커밋 제목: `docs: normalize planning document endings`
- 변경 파일: `docs/jira/WORK_BREAKDOWN.md`, `docs/jira/work-breakdown.json`, `Decisions.md`.
- 배경·근거: 신규 파일을 stage한 뒤 실행한 diff 검사에서 파일 끝의 불필요한 빈 줄 2건을 발견했습니다.
- 결정·대안: 공유 브랜치의 이력을 다시 쓰지 않고 후속 커밋으로 파일 끝 개행을 하나로 정리합니다.
- 영향: 작업 범위·담당·완료 기준과 제품 정책에는 변화가 없습니다.
- 검증: 전체 PR diff의 공백 검사와 JSON 파싱을 다시 확인합니다. 문서 서식 변경으로 앱 테스트는 실행하지 않습니다.

## DEV-20260920-01 — 작은 작업 단위와 팀 확인 근거 분리

- 날짜: 2026-09-20
- 관련 커밋 제목 / Jira: `docs: split initial Jira work into reviewable deliverables` / DODU-15~18 분할 초안.
- 변경 파일: `docs/jira/WORK_BREAKDOWN.md`, `docs/jira/work-breakdown.json`, `docs/jira/templates/WORK_REVIEW.md`, `docs/jira/README.md`, `Decisions.md`.
- 배경: 사용자가 Jira 기반의 다음 작업과 담당자별 세분화, 작업 완료 때마다 팀원이 확인할 수 있는 방식을 요청했습니다. 무료 CI·리뷰 도구 도입도 검토합니다.
- 결정: 기존 부모 티켓을 유지하면서 첫 묶음을 결과물·검토자·완료 기준이 있는 하위 작업 12개로 제안합니다. PR/결과물과 사람의 확인 기록을 연결하고 구현 완료와 팀 확인 완료를 구분합니다.
- 근거: 9월 16일 Jira 등록 명세, 9월 20일 저장소·PR #1 상태 확인, 사용자 요청, PRD §19~21, 서비스 정책 §0와 OPEN_DECISIONS. 현재 Jira/Notion 인증 문제로 최신 문서는 읽지 못했습니다.
- 대안 및 선택 이유: 모든 후속 백로그를 바로 세분화하면 OPEN 정책을 전제로 하거나 기존 작업과 중복될 수 있어 가까운 기반 작업부터 분할합니다. 전원 승인 대신 지정 기술 검토자와 필요한 인수 담당자의 확인을 제안합니다.
- 도구 판단: GitHub Actions를 무료 사용 한도와 초과 과금 차단 확인 후 도입할 후보로 둡니다. CodeRabbit의 지속 무료 비공개 PR 기능은 요약 중심이고 Jenkins는 별도 호스트 운영이 필요해 설치하지 않았습니다. 공식 출처는 WORK_BREAKDOWN.md에 기록했습니다.
- 영향·주의점: 초안 ID는 실제 Jira 키가 아닙니다. 역할이 확인되지 않은 추가 인원은 미배정이며 기존 스캐폴딩을 중복 개발하지 않습니다. D01~D13은 OPEN을 유지합니다. 원격 티켓·Jira 상태·자동 알림·유료 서비스는 이번 문서 변경으로 구성되지 않습니다.
- 검증: 분할 ID·부모 참조·담당자·선행 관계 및 순환 여부, 문서 링크와 diff를 검사합니다. 문서 전용이므로 앱 테스트는 미실행입니다.

## DEV-20260917-01 — 개발 통합과 배포 브랜치 분리

- 날짜: 2026-09-17
- 관련 커밋 제목 / Jira: `docs: define dev integration and main release workflow` / DODU-15의 저장소 협업 설정 범위.
- 변경 파일: `AGENTS.md`, `README.md`, `docs/DEVELOPMENT_CONVENTIONS.md`, `docs/PR_CONVENTIONS.md`, `.github/pull_request_template.md`, `Decisions.md`.
- 배경: 초기 저장소 이전 시 `main`이 기본 브랜치였으나 사용자는 개발 통합과 배포를 분리하기를 요청했습니다.
- 결정: 기존 `main`과 같은 커밋에서 `dev`를 생성하고 GitHub 기본 브랜치를 `dev`로 변경합니다. 작업 PR은 `dev`, 배포 PR은 `main`을 대상으로 합니다.
- 근거: 2026-09-17 사용자의 명시적 브랜치 운영 요청. 기존 커밋 해시와 `main` 이력은 변경하지 않습니다.
- 대안 및 선택 이유: `main`에 개발 변경을 계속 통합하는 방식은 배포 전용 요구와 맞지 않습니다. 작업 PR은 Squash, 장기 브랜치 간 배포 PR은 merge commit으로 공통 이력을 유지합니다.
- 영향·주의점: 기본 브랜치 변경은 자동 배포나 브랜치 보호 설정을 의미하지 않습니다. 기존 제품 정책 D01~D13은 계속 OPEN입니다. 이번 문서 변경은 `dev` 대상 PR로 검토합니다.
- 검증: GitHub 기본 브랜치와 원격 `dev` 생성 확인, 문서 diff·상대 링크 확인. 문서 전용이므로 앱 테스트는 미실행입니다. Notion은 401 인증 오류로 최신 본문을 확인하지 못했으며 제품 정책 변경의 근거로 사용하지 않았습니다.

커밋마다 무엇을 바꿨는지뿐 아니라 왜 그렇게 결정했는지 기록합니다.
이 파일은 개발 판단의 이력이며 제품 정책 승인 문서를 대신하지 않습니다.
[OPEN_DECISIONS](docs/dodu/OPEN_DECISIONS.md)의 제안은 별도 승인 근거 없이 확정하지 않습니다.

## 작성 규칙

- 모든 커밋에 해당 결정 기록을 작성하고 변경 파일과 함께 포함합니다. 문서·설정·수정·되돌리기 커밋도 포함합니다.
- 기록 ID는 `DEV-YYYYMMDD-01`처럼 날짜와 당일 순번으로 부여합니다. 제품 정책 D01~D13과 구분합니다.
- 커밋 예정 제목과 변경 파일로 대상을 식별합니다. 자기 자신을 포함한 커밋 해시는 미리 알 수 없으므로 필수로 적지 않습니다.
- 배경, 결정, 근거, 검토한 대안과 선택 이유, 영향 및 검증을 적습니다. 작은 변경은 짧게 작성해도 됩니다.
- 사실과 추정을 구분합니다. 검토하지 않은 대안이나 실행하지 않은 검증을 만들어 적지 않습니다.
- 기존 기록은 보존하고 결정이 바뀌면 새 기록에서 이전 ID를 참조합니다. amend 시에는 해당 커밋 기록도 최종 변경에 맞춥니다.
- 병합 충돌 해결·Squash 과정에서도 관련 기록을 보존합니다. 새 판단이 생겼다면 배경과 근거를 추가합니다.
- 커밋 메시지는 영문 `<type>: <summary>` 제목, 빈 줄, 영문 변경 사항 목록 형식을 사용할 수 있습니다. `Co-authored-by` 등 공동 작성자 표기는 추가하지 않습니다.

## 기록 양식

```markdown
## DEV-YYYYMMDD-NN — 결정 제목

- 날짜:
- 관련 커밋 제목 / Jira(해당 시):
- 변경 파일:
- 배경: 해결할 문제와 제약.
- 결정: 선택한 방법.
- 근거: 사용자 요구, 문서, 코드 또는 검증 결과.
- 대안 및 선택 이유: 검토한 대안과 장단점. 별도 검토가 없으면 명시.
- 영향·주의점: 영향 범위와 남은 과제, 관련 OPEN ID.
- 검증: 실행 결과와 미실행 항목·이유.
```

## DEV-20260916-01 — 커밋별 결정 기록과 작성자 표기 규칙

- 날짜: 2026-09-16
- 관련 커밋 제목: `docs: document commit decision logging conventions` (예정; 커밋 실행 전)
- 변경 파일: `Decisions.md`, `AGENTS.md`, `README.md`, `docs/DEVELOPMENT_CONVENTIONS.md`, `docs/PR_CONVENTIONS.md`, `.github/pull_request_template.md`.
- 배경: 변경 목록만으로는 개발자가 선택한 방법의 배경과 근거를 나중에 확인하기 어렵습니다.
- 결정: 모든 커밋에 루트 `Decisions.md`의 해당 기록을 함께 포함하고, PR에서 누락 여부를 확인합니다. 영문 제목과 변경 목록 형식을 허용하며 공동 작성자 표기는 넣지 않습니다.
- 근거: 사용자가 커밋마다 별도 결정 기록을 요구했고, 직전 요청에서 메시지 형식을 허용하면서 공동 작성자 표기를 금지했습니다.
- 대안 및 선택 이유: 커밋 본문에만 이유를 남기는 방식보다 별도 파일이 필요하다는 사용자 요구에 맞춰 저장소 내 단일 문서를 선택했습니다. 커밋마다 파일을 나누는 방식은 도입하지 않았습니다.
- 영향·주의점: 커밋 시 문서 갱신이 추가됩니다. 자동 검사나 Git hook은 구성하지 않았습니다. 제품 정책 D01~D13은 계속 OPEN이며 이 기록으로 승인하지 않습니다. 과거 커밋의 근거를 추정하여 소급 작성하지 않습니다.
- 검증: 문서의 상대 링크와 변경 diff를 확인합니다. 문서 전용 변경이므로 앱 테스트는 실행하지 않습니다. Notion 기준 페이지는 404로 하위 항목을 확인하지 못했으며 이번 규칙의 근거로 사용하지 않았습니다.



## DEV-20261005-01 — 공통 헤더·버튼과 개발용 확인 화면 구성

- 날짜: 2026-10-05
- 관련 커밋 제목: `feat: add shared header, button, icons, and component preview`
- 변경 파일: `Decisions.md`, `frontend/src/DoduHome.tsx`, `frontend/src/dodu.css`, `frontend/src/main.tsx`, `frontend/src/ComponentPreview.tsx`, `frontend/src/components/AppHeader.tsx`, `frontend/src/components/Button.tsx`, `frontend/src/assets/icons/` 내 SVG 파일.
- 배경: SCRUM-8의 공통 화면 구조와 UI 컴포넌트를 단계적으로 구성하고, 재사용할 버튼의 상태를 확인할 화면이 필요합니다.
- 결정: 공통 헤더를 AppHeader로, 버튼을 Button으로 분리합니다. ComponentPreview에 정상·로딩·비활성 버튼 예시를 배치하고, main.tsx에서 개발용 확인 화면을 표시합니다. 제공된 SVG 아이콘을 추가하고 가이드용 배경을 주석 처리합니다. 기존 홈의 준비 화면과 API 연결 확인 코드는 주석으로 보존하고 실행에서 제외합니다.
- 근거: SCRUM-8의 공통 UI 구성 및 상태 예시 확인 범위, docs/DEVELOPMENT_CONVENTIONS.md의 React·TypeScript 구조와 명명 규칙, 사용자가 제공한 디자인 가이드입니다.
- 대안 및 선택 이유: 홈에 개발용 예시를 함께 배치하는 방식 대신 ComponentPreview로 분리하여 제품 화면과 컴포넌트 확인 목적을 구분했습니다. 기존 HTML과 CSS로 구현할 수 있어 새 UI 라이브러리는 도입하지 않았습니다.
- 영향·주의점: 현재 앱 진입 화면은 개발용 확인 화면입니다. 설정 아이콘은 이동 기능이 없으며, 로딩 버튼은 실제 요청을 실행하지 않는 표시 예시입니다. 기존 백엔드 연결 확인 UI는 실행에서 제외되지만 백엔드 API 자체는 변경하지 않습니다. 예약·인증 기능은 연결하지 않습니다. 입력·오류·빈 상태 구현은 남아 있어 SCRUM-8 전체 완료로 보지 않습니다. 제품 정책 D01~D13을 이 기록으로 승인하지 않습니다.
- 검증: 홈에서 헤더와 설정 아이콘 표시 및 좁은 미리보기에서 배치를 확인했습니다. 공통 버튼의 보라색 스타일과 키보드 포커스, 개발용 화면의 정상·로딩·비활성 버튼 예시 표시를 수동 확인했습니다. npm run verify를 실행하여 프론트 린트 경고·오류 0개, TypeScript 검사 및 Vite 빌드 성공, 백엔드 테스트 1개 통과, 애플리케이션 패키징 성공을 확인했습니다. 실제 모바일 기기와 스크린리더 검증은 수행하지 않았습니다.

## DEV-20261005-02 — 공통 UI 결정 기록 정정

- 날짜: 2026-10-05
- 관련 커밋 제목: `docs: correct common UI decision record`
- 변경 파일: `Decisions.md`.
- 배경: 이전 기록에 실제 커밋 제목과 검증 결과가 반영되지 않았습니다.
- 결정: 영어 커밋 제목, 기존 홈 코드의 주석 처리, 검증 통과 결과와 미실행 항목을 반영합니다.
- 근거: 커밋 18dcfe9의 변경 내용과 npm run verify 실행 로그입니다.
- 대안 및 선택 이유: 이미 푸시한 커밋을 수정하는 대신 후속 커밋으로 기록을 정정합니다.
- 영향·주의점: 문서만 변경하며 앱 동작과 제품 정책은 변경하지 않습니다.
- 검증: 기존 커밋과 실행 로그를 대조했습니다. 문서만 변경하므로 앱 검증은 재실행하지 않습니다.

## DEV-20261005-03 — 공통 입력·오류·빈 상태와 접근성 확인

- 날짜: 2026-10-05
- 관련 커밋 제목: `feat: add shared text field and empty state previews`
- 변경 파일: `Decisions.md`, `frontend/src/ComponentPreview.tsx`, `frontend/src/components/TextField.tsx`, `frontend/src/components/EmptyState.tsx`, `frontend/src/dodu.css`.
- 배경: SCRUM-8의 입력·오류·빈 상태 예시와 키보드·반응형 확인이 남아 있었습니다.
- 결정: 문자열 입력과 도움말·오류 문구를 받는 TextField, 제목·설명을 받는 EmptyState를 추가합니다. Preview에서 입력 반영, 오류 표시·숨기기, 정상 버튼 클릭 상태 메시지를 확인합니다. label/htmlFor, aria-describedby, aria-invalid, role=alert 및 role=status를 연결합니다.
- 근거: SCRUM-8 완료 기준, docs/DEVELOPMENT_CONVENTIONS.md의 명명·React 구조·접근성·검증 기준과 실제 브라우저 검사 결과입니다.
- 대안 및 선택 이유: 홈에 예시를 배치하는 대신 ComponentPreview에서 확인하고, 새 라이브러리 없이 기본 HTML과 React 상태를 사용합니다. 오류와 로딩은 제품 정책 검증이 아닌 표시 예시로 유지합니다.
- 영향·주의점: 실제 예약·인증·권한 요청·데이터 저장은 실행하지 않습니다. OPEN 정책을 확정하지 않습니다. 실제 모바일 기기·스크린리더와 mono 리뷰는 별도이며, 브라우저 폭 검증으로 이를 대체했다고 보지 않습니다. 기존 DEV-20261005-01에서 남겨둔 입력·오류·빈 상태 구현을 이번 변경에서 추가했습니다.
- 검증: 2026-10-05 npm run verify 통과(TypeScript·Vite 빌드, 린트, 백엔드 테스트 1개 및 패키징 성공). Codex 내장 브라우저에서 Enter로 정상 버튼 활성화와 상태 메시지, Tab으로 disabled 버튼 2개 건너뛰기 및 입력·오류 버튼 포커스 테두리, label 클릭 시 입력 포커스, 입력값 반영, 오류 표시·숨기기와 aria 연결을 확인했습니다. 320·375·768·1440px 뷰포트에서 기본 예시의 document scrollWidth가 clientWidth를 넘지 않았고, 320px 오류 표시 상태에서 줄바꿈과 빈 상태 가운데 정렬을 확인했습니다. 실제 모바일 기기와 스크린리더 음성 출력은 미실행입니다.


## DEV-20261005-04 — 홈 UI와 약속 흐름 미리보기 구성

- 날짜: 2026-10-05
- 관련 커밋 제목: `feat: add home UI and promise flow preview` (예정)
- 변경 파일: `Decisions.md`, `frontend/src/App.tsx`, `frontend/src/DoduHome.tsx`, `frontend/src/main.tsx`, `frontend/src/dodu.css`, `frontend/src/components/AppHeader.tsx`, `frontend/src/components/TodayPromiseCard.tsx`, `frontend/src/components/WeeklyPromiseCard.tsx`, `frontend/src/features/promise/` 내 화면·타입·날짜 처리 파일.
- 배경: 제공된 홈 디자인을 확인하고, 백엔드 연결 전 약속 작성부터 홈·기록 반영까지 화면 흐름을 검토할 필요가 있습니다.
- 결정: 오늘 약속과 주간 카드를 구성하고, 작성·수정·내용 확인·기록 화면을 별도 파일로 분리합니다. App에서 입력값과 미리보기 약속 상태를 관리하며 홈과 기록 화면에 전달합니다. 주간 날짜는 KST 기준으로 계산합니다. 기기 시간이 예정 시각에 도달하면 인증 버튼을 표시하고, 클릭하면 메모리 내 인증 상태를 변경하여 진행 막대와 날짜 표시를 확인합니다.
- 근거: 사용자가 제공한 홈 디자인·색상·텍스트 가이드, 프론트엔드 미리보기 요청, SCRUM-13의 메모리 내 작성·확인 프로토타입 범위, docs/DEVELOPMENT_CONVENTIONS.md의 React·TypeScript 구조와 검증 기준입니다.
- 대안 및 선택 이유: 화면마다 고정 예시를 넣는 대신 공유 상태를 전달하여 작성·수정 결과가 홈과 기록 화면에 함께 반영되도록 했습니다. 백엔드 계약을 임의로 만드는 대신 메모리 내 동작으로 구성했으며 새 라이브러리는 도입하지 않았습니다.
- 영향·주의점: 실제 예약 저장·사진 촬영·전송·인증·알림은 실행하지 않으며 새로고침하면 초기화됩니다. 인증 완료 표시는 UI 확인용입니다. 작성·확인·기록 화면은 임시 배치이며 해당 디자인에 맞춰 보완해야 합니다. 인증 종료시각 및 서버 기준 상태 검증은 미구현입니다. API가 준비되면 서버 저장·조회·인증 상태로 교체합니다. D06·D11 등 OPEN 항목을 확정하지 않습니다. 팀에서 전달받은 브랜치명 Feature/home-ui를 사용하며 Jira 연결은 추후 정리합니다.
- 검증: 2026-10-05 npm run verify 통과. 프론트 린트 경고·오류 0개, TypeScript 검사 및 Vite 빌드 성공, 백엔드 테스트 1개 통과 및 패키징 성공을 확인했습니다. 사용자 화면에서 약속 표시, 인증 버튼 클릭 후 1번 중 1번·100% 진행 막대·오늘 날짜 배경 변경을 확인했으며, 기록 화면의 인증 완료 문구 반영도 사용자가 확인했습니다. 최신 홈의 전체 상태별 반응형·키보드·스크린리더 검증과 실제 API 연동 검증은 아직 수행하지 않았습니다.

## DEV-20261015-01 — SCRUM-76 화면별 API 인수 범위 분리

- 배경: 최종 핸드오프 후 UI mock 개발과 서버 계약·실제 연결 작업의 책임을 분리할 필요가 있다.
- 선택: `docs/api/SCREEN_API_HANDOFF.md`에 화면 묶음별 담당·티켓·인수 기준과 남은 승인 항목을 기록한다. 계약 전체가 아닌 첫 문서 증분이며 구현 완료로 표시하지 않는다.
- 근거: SCRUM-76의 화면별 계약 인수 범위, 공통 API·시간·접근 계약, 승인 D03~D06·D10~D12 및 최신 Notion 운영정책. 지정 기준 페이지 접근 실패로 하위 전체 최신성은 확인하지 못했다.
- 대안: 화면마다 임의 엔드포인트와 DTO를 즉시 확정하는 방식은 팀 인수 전 호환성을 보장할 수 없어 선택하지 않았다.
- 영향: 앱과 DB 동작은 변경하지 않는다. S07 책임 확인, SCRUM-75 정책 승인과 모듈별 기술 합의·fixtures·팀 리뷰는 남는다. 다른 담당자의 구현을 수행하지 않는다.
- 검증: 문서 상대 링크 존재 여부, 화면/티켓 매핑과 승인 여부 표기, `git diff --check`를 확인한다. 문서 전용 변경으로 앱 테스트는 실행하지 않는다.

## DEV-20261015-02 — 약속 CRUD 계약과 가상 응답 사례

- 배경: SCRUM-76의 담당 지도만으로는 연정이 생성·수정·취소의 mock 입력과 오류 처리를 검토하기 어렵다.
- 선택: PROMISE_API_CONTRACT.md와 10개 mock 사례를 추가한다. 경로·DTO·추가 오류명·낙관적 버전 검사는 리뷰 전 기술 제안이며 운영 API로 연결하지 않는다. 기존 공통 fixture는 보존한다.
- 근거: PRD F-01·F-06, Flow·Log §1.2~1.3·§3~4, 최신 Notion 운영정책 §1~2·§12~13, 승인 D03·D05·D06·D11, 공통/시간/접근 계약. 지정 Notion 기준 링크는 404이고 접근 가능한 정책 페이지는 기존 20261013 첨부를 유지한다.
- 대안: 인증 담당의 세션 구현을 우회하거나 임시 participantId를 받아 실제 API를 여는 안은 소유권 검증을 훼손하므로 선택하지 않았다. 미결정 문자 계산과 멱등 보관기간은 임의로 정하지 않는다.
- 영향: 새 런타임 의존성·앱 코드·DB 변경 없음. 타인 자원 404는 접근 계약의 기술 결정을 재사용하며 공통 문서의 과거 403 설명과 충돌을 명시했다. 재생성·기술복구·전체 상태·팀 리뷰는 남는다. 사용자 소유 .vscode는 제외한다.
- 검증: Node로 10개 JSON 사례의 envelope·HTTP/type 일치·KST 날짜·T+30·수정 버전/ID 보존·취소 분리를 검사한다. 상대 링크 및 git diff --check를 확인한다. 실제 HTTP/DB·기기·동시성 검증은 미실행이다. PR8의 CodeRabbit 인라인 댓글 조회는 0건이었으며 이번 로컬 증분의 원격 리뷰 결과는 없다.

## DEV-20261015-03 — 파생 약속과 재전송 계약 분리

- 배경: 취소 후 재생성·TechFailed 복구 재예약·접수된 사진 내부 재처리가 같은 복구로 취급되면 날짜/횟수/마감 정책을 우회할 수 있다.
- 선택: PROMISE_RECOVERY_CONTRACT.md에 세 동작을 분리하고 날짜별 별도 카운터·원본 소유권·멱등 응답 재현/재조회 설계를 제안한다. 재생성 정상과 오류 mock6개를 추가한다.
- 근거: 최신 Notion 운영정책 §1~2, 승인 D03·D04·D06·D08·D09·D11, 기존 접근/CRUD 계약 및 DB V1의 날짜 사용량·원본 연결 구조. 지정 기준 페이지404와 하위 전체 최신성 미확인 상태는 유지한다.
- 대안: 원본마다 복구 한도를 초기화하거나 모든 네트워크 retry를 새 약속으로 만드는 방식은 날짜 한도를 우회하므로 배제했다. 기술복구 약속의 재수정 가능 여부는 제품 결정이 명확하지 않아 성공 actions를 임의 고정하지 않는다.
- 영향: 앱/DB 동작 변경 없음. 키 형식·저장·보관은 기술 제안이고 제품 승인과 구분한다. 실제 구현은 팀 계약 인수 및 DB/인증 연결 후 진행한다. 기존 원문·mock과 사용자 .vscode는 보존한다.
- 검증: JSON6개 envelope/오류 유형, 새 ID·원본/최종 KST 날짜 연결, T+30, 개별 한도, Evaluating과 TechFailed 분리, 공식 종료 경계를 검사한다. 상대 링크·git diff --check 확인. HTTP/DB 테스트 미실행. PR8 CodeRabbit 리뷰 본문의 포커스 전환 테스트 제안은 별도 프론트 브랜치의 정확성 보완으로, 이번 계약의 보안/성능 필수 수정과는 구분해 미변경이다.
## DEV-20261016-01 — SCRUM-83 사진 제출의 원자적 저장 경계

- 날짜: 2026-10-16
- 관련 Jira/작업: SCRUM-83, 사진 제출 API·DB 저장·원자적 판정 예약 통합
- 변경 파일: `backend/src/main/java/com/dodu/intervention/PhotoSubmissionController.java`, `PhotoSubmissionFingerprint.java`, `backend/src/main/resources/db/migration/V2__photo_submission_storage.sql`, `docs/api/PHOTO_SUBMISSION_CONTRACT.md`
- 배경: 기존 정책 클래스는 사진 제출 시간 경계와 attempt identity를 검증했지만, 활성 세션 소유권·DB 저장·요청 멱등·다중 서버 동시 Evaluating 제약을 HTTP 경계에 연결하지 않았다.
- 결정: multipart 요청의 서버 수신시각과 활성 세션의 참가자 소유권을 먼저 검증하고, 외부 저장소를 가리키는 사진 메타데이터와 `auth_attempt`를 한 트랜잭션에 저장한다. 같은 약속·요청 키는 같은 fingerprint일 때 기존 attempt를 반환하고, 다른 본문은 충돌로 처리한다. `auth_attempt`의 기존 부분 유니크 인덱스를 동시 Evaluating 예약의 DB 최종 기준으로 사용한다.
- 근거: `docs/dodu/sources/SERVICE_POLICY_v2.5.md` §3~8, `docs/dodu/OPEN_DECISIONS.md` D04·D08·D10·D11, 기존 `InterventionWindowPolicy`·`PhotoAttemptIdentityPolicy`와 SCRUM-83 완료 기준.
- 대안 및 선택 이유: 프로세스 메모리 예약만 사용하는 방법은 다중 서버에서 중복 Evaluating을 허용할 수 있어 제외했다. 사진 원문을 DB에 저장하는 방법은 민감 데이터 노출면을 늘리므로 저장 키와 검증 메타데이터만 저장한다.
- 영향·주의점: 객체 저장소 업로드·삭제, AI 결과 저장, 지연 결과·철회 보호는 이 커밋에서 완료하지 않고 SCRUM-84~86으로 남긴다. Docker/Testcontainers가 unavailable이면 DB 통합 검증을 완료로 표시하지 않는다.
- 검증: `PhotoSubmissionFingerprintTests` 2건 성공. 전체 DB 마이그레이션·동시성 통합 테스트는 Docker 환경에서 별도 실행한다.

## DEV-20261016-02 — SCRUM-83 V2 사진 메타데이터 연결 검증 준비

- 날짜: 2026-10-16
- 관련 Jira/작업: SCRUM-83
- 변경 파일: `backend/src/test/java/com/dodu/db/CoreSchemaMigrationTests.java`
- 배경: V2 마이그레이션은 사진 메타데이터와 인증 시도를 연결하지만, 기존 통합 테스트는 V1 테이블 수와 기존 `auth_attempt` 제약만 확인했다.
- 결정: Testcontainers가 실행 가능한 환경에서 `photo_asset` 생성, `auth_attempt.photo_asset_id` 연결, 하나의 사진 자산을 두 attempt가 공유하지 못하는 부분 유니크 인덱스를 검증한다.
- 근거: `V2__photo_submission_storage.sql`, SCRUM-83 완료 기준의 photo_asset/attempt 저장 요구.
- 영향·주의점: 테스트는 Docker/Testcontainers가 없으면 실행할 수 없으며, 실행 전까지 SCRUM-83 완료 조건으로 표시하지 않는다.
- 검증: 테스트 컴파일은 관련 단위 테스트 실행 시 성공했다. Docker 통합 실행은 환경 부재로 아직 실패 상태다.

## DEV-20261016-03 — SCRUM-84 판정 결과의 소유권·현재 attempt 원자성

- 날짜: 2026-10-16
- 관련 Jira/작업: SCRUM-84
- 결정: 결과 저장은 활성 세션의 참가자, 약속 소유자, 철회되지 않은 참가자, `Evaluating` 현재 상태를 하나의 SQL UPDATE 조건으로 확인한다. 상태가 이미 종료되었거나 철회된 경우 결과를 덮어쓰지 않는다.
- 근거: SCRUM-84 완료 기준, D05·D08·D10, 기존 `auth_attempt` 상태 모델.
- 주의: 실제 동시성·지연 결과·철회 경쟁은 Docker/Testcontainers 통합 검증 전까지 완료로 표시하지 않는다.

## DEV-20261016-04 — SCRUM-85 지연 판정 복구의 동일 attempt 제한

- 날짜: 2026-10-16
- 관련 Jira/작업: SCRUM-85
- 결정: 마감 후 내부 복구는 T 이전에 정상 접수된 동일 attempt이고 현재 상태가 `Evaluating`인 경우에만 허용한다. T+30 이후 새 사진 제출, 완료·거절·기술실패·철회 attempt 재처리는 허용하지 않는다.
- 근거: D04·D08·D10·D11 및 SCRUM-85 완료 기준.
- 주의: retry/backoff 큐와 실제 DB 상태 경쟁 검증은 후속 통합 단계로 남긴다.

## DEV-20261016-05 — SCRUM-86 공식 종료 후 신규 작업 차단

- 날짜: 2026-10-16
- 관련 Jira/작업: SCRUM-86
- 결정: 공식 개입 종료 이후에는 신규 작업을 닫고, 종료 전에 정상 접수된 attempt의 내부 처리만 별도 상태로 허용한다. 종료 전에는 신규 작업을 허용한다.
- 근거: D03·D04·D10·D11 및 SCRUM-86 완료 기준.
- 주의: 실제 약속·알림·기술복구 서비스의 오래된 작업 무효화는 통합 연결 단계에서 검증한다.

## DEV-20261018-02 — 전체 통합 테스트의 Spring 프록시·제약조건 검증 수정

- 날짜: 2026-10-18
- 관련 범위: Docker/Testcontainers 전체 백엔드 테스트, SCRUM-83 사진 메타데이터 연결 검증
- 배경: Docker 재기동 후 전체 테스트에서 실제 DB 마이그레이션은 성공했지만, 사진 자산 중복 테스트가 다른 유니크 제약조건을 먼저 검증했고, Spring의 기본 CGLIB 프록시가 final Controller를 생성하지 못했다.
- 결정: 사진 자산 중복 테스트의 두 번째 attempt에 고유 요청 fingerprint와 비Evaluating 상태를 사용해 photo_asset 연결 제약을 단독 검증한다. 트랜잭션 프록시가 필요한 Controller는 final을 제거해 Spring이 프록시화할 수 있게 한다.
- 근거: 전체 Maven 테스트 실패 로그, Flyway V1/V2 실제 적용 로그, Spring AOP의 CGLIB 프록시 오류, V2 부분 유니크 인덱스 검증 의도.
- 대안: 테스트에서 실패한 제약조건을 허용하거나 Spring AOP를 전역 비활성화하는 방법은 실제 무결성·트랜잭션 검증을 약화하므로 선택하지 않았다.
- 영향: 런타임 API 계약과 DB 스키마는 변경하지 않는다. Controller는 기존 트랜잭션 경계를 유지하면서 Spring 프록시 가능한 형태가 된다.
- 검증: Docker 기반 전체 백엔드 테스트 46건을 실행해 모두 통과했다. Flyway V1/V2가 Testcontainers PostgreSQL에 실제 적용됐고 Spring 애플리케이션 컨텍스트도 기동됐다.

## DEV-20261018-03 — 약속 생성 멱등성 저장 계약

- 날짜: 2026-10-18
- 관련 범위: SCRUM-23 약속 생성 API의 중복 요청 방지
- 결정: 참가자별 `Idempotency-Key`와 요청 지문을 `promise_creation_request`에 보관하고, 동일 키·동일 지문은 동일 약속을 재사용하며 동일 키·상이한 지문은 충돌로 처리한다. 키와 지문은 참가자 범위로 격리한다.
- 근거: SCRUM-23 완료 기준의 동시 요청 안정성, D03·D05·D06·D11 미결정 사항을 침범하지 않는 요청 중복 처리 범위.
- 대안: 약속 본문에 키를 직접 추가하거나 애플리케이션 메모리 캐시만 사용하는 방식은 DB 트랜잭션·재시작·다중 인스턴스 동작을 보장하지 못하므로 선택하지 않았다.
- 영향: V3 마이그레이션과 순수 요청 지문 정책이 추가된다. 기존 약속 상태·응답 계약은 컨트롤러 연결 단계에서 유지한다.
- 검증: 정책 단위 테스트 2건과 Testcontainers 기반 Flyway V1~V3 마이그레이션 검증을 실행해 모두 통과했다.

## DEV-20261018-04 — 약속 생성 멱등성의 트랜잭션 연결

- 날짜: 2026-10-18
- 관련 범위: SCRUM-23 `POST /api/promises`
- 결정: 요청 인증 후 멱등성 키와 지문을 먼저 조회하고, 신규 키는 `INSERT ... ON CONFLICT DO NOTHING`으로 예약한다. 예약 획득 후 약속 생성과 키 연결을 같은 트랜잭션에서 처리한다.
- 근거: 동일 키 동시 요청의 단일 약속 보장, 동일 키·상이한 본문의 충돌 검출, 프로세스 재시작·다중 인스턴스에서도 유지되는 DB 기준 상태.
- 대안: 애플리케이션 메모리 락이나 약속 테이블에 임의 필드를 추가하는 방식은 영속성과 참가자 범위 격리를 보장하지 못해 배제했다.
- 영향: 생성 API는 `Idempotency-Key` 헤더를 요구하며, 기존 약속·수정·취소 API의 상태 계약은 변경하지 않는다.
- 검증: Docker 기반 전체 백엔드 테스트 48건, 실패 0·오류 0. 추가한 Spring/MockMvc/PostgreSQL 통합 시나리오 1건도 통과했으며 동일 키 재요청은 동일 약속을 반환하고 지문 충돌은 409로 거절됨을 확인했다.

## DEV-20261018-05 — 약속 생성 동시 요청 통합 검증

- 날짜: 2026-10-18
- 관련 범위: SCRUM-23 약속 생성 멱등성의 병렬 요청 처리
- 결정: 동일 참가자·동일 키의 두 HTTP 요청을 병렬 실행해 DB 유니크 키 대기와 기존 약속 재조회를 통합 검증한다.
- 근거: 단순 순차 재요청만으로는 두 요청이 동시에 활성 약속 검사를 통과하는 경합을 검출할 수 없다.
- 영향: 제품 코드 변경 없이 실제 Spring MockMvc와 PostgreSQL Testcontainer 검증 범위를 확장한다.
- 검증: `PromiseControllerIntegrationTests` 2건 통과. 병렬 요청 결과의 promiseId가 동일하고 생성 요청·약속 행이 각각 1개임을 확인했다.

## DEV-20261018-06 — PostgreSQL 시각값의 JDBC 변환 명시

- 날짜: 2026-10-18
- 관련 범위: SCRUM-23 약속 API 및 SCRUM-35/37 사진 인증 API의 실제 PostgreSQL 연동
- 결정: PostgreSQL `timestamptz` 읽기는 `Timestamp.toInstant()`로, 쓰기는 `Timestamp.from(Instant)`로 명시해 드라이버의 암시적 Java time 변환에 의존하지 않는다.
- 근거: MockMvc·Testcontainers 통합 실행에서 PostgreSQL 드라이버가 `Instant` 직접 변환·바인딩을 거절한 실제 오류.
- 영향: API 상태·시간 의미는 유지하고 JDBC 경계만 명시적으로 보정한다. 민감한 사진 데이터는 테스트 출력에 기록하지 않는다.
- 검증: Docker 기반 전체 백엔드 테스트 50건, 실패 0·오류 0.

## DEV-20261018-07 — 약속 수정의 날짜 원장·낙관적 잠금 검증

- 날짜: 2026-10-18
- 관련 범위: SCRUM-23 약속 수정 API
- 결정: 일정이 KST 날짜를 변경하면 새 날짜 원장을 같은 트랜잭션에 기록하고, 기존 `row_version`을 기준으로 수정 경합을 거절한다. 같은 날짜 수정은 새 원장을 만들지 않는다.
- 근거: 참가자별 예정 시작일 중복 방지와 동시 수정 시 마지막 요청의 무검증 덮어쓰기 방지.
- 영향: 수정 API의 상태 계약은 유지하며 날짜 원장 누락을 방지한다.
- 검증: 실제 PostgreSQL 통합 테스트에서 병렬 수정 결과가 200·409 한 건씩이고 새 KST 날짜 원장이 1건임을 확인했다.

## DEV-20261018-08 — 약속 취소의 단일 종료 검증

- 날짜: 2026-10-18
- 관련 범위: SCRUM-23 약속 취소 API
- 결정: 동일 약속에 대한 병렬 취소는 행 잠금과 `row_version` 조건으로 한 번만 `Closed/Cancelled` 전이를 성공시키고, 나머지는 409 충돌로 반환한다.
- 근거: 취소 후 중복 요청이 최종 상태를 다시 덮어쓰거나 성공으로 오인되지 않아야 한다는 상태 전이 계약.
- 검증: 실제 PostgreSQL 통합 테스트 4건에서 병렬 취소 200·409 결과와 단일 종료 행을 확인했다.

## DEV-20261018-09 — 사진 판정 결과의 약속 상태 전이

- 날짜: 2026-10-18
- 관련 범위: SCRUM-35/37 사진 판정 결과 저장 API
- 결정: 현재 Evaluating attempt의 `COMPLETED` 결과는 약속을 `AuthCompleted`로, `TECH_ERROR` 결과는 `Closed/TechFailed`로 전이한다. 취소·철회·이미 인증 완료 상태는 결과가 덮어쓰지 않는다.
- 근거: 사진 인증 완료와 실제 작업 시작 자기보고를 분리하면서도 약속의 인증 결과 상태를 저장해야 하며, 기술 실패를 미인증과 분리해야 한다는 제품 정책.
- 영향: `auth_completed_at`과 `final_outcome`만 상태 전이에 사용하고 실제 작업 시작 데이터는 변경하지 않는다.
- 검증: Docker/Testcontainers PostgreSQL 기반 판정 결과 통합 테스트 2건 통과.

## DEV-20261018-10 — 사진 제출 멀티파트·중복 요청 통합 검증

- 날짜: 2026-10-18
- 관련 범위: SCRUM-35 사진 제출 API
- 결정: 실제 멀티파트 사진 제출에서 DB에는 사진 메타데이터와 auth attempt 연결만 저장하고, 동일 `clientRequestId`·동일 지문은 기존 attempt를 반환하며 지문이 다르면 409로 거절한다.
- 근거: 원시 사진 바이트를 로그·DB에 남기지 않고 외부/비공개 저장소 경계를 유지해야 하며, 중복 제출이 새 인증 시도를 만들면 안 된다는 정책.
- 영향: 현재 구현은 저장소 메타데이터 경계까지 담당하고 실제 object storage 업로드 worker는 후속 연동 범위로 남긴다.
- 검증: Docker/Testcontainers PostgreSQL 기반 멀티파트 제출 통합 테스트 2건과 전체 백엔드 테스트 56건이 모두 통과했다.

## DEV-20261018-11 — 화면 API 인수 문서의 구현 상태 대조

- 날짜: 2026-10-18
- 관련 범위: SCRUM-76 화면별 API·상태·오류·fixture 인수 문서
- 결정: 실제 HTTP/DB 검증이 끝난 약속·사진 범위만 구현 완료로 표시하고, 공통 envelope·FE 리뷰·알림·집계·자기보고 전체 계약은 제안/미완료로 남긴다. 폐기된 외부 기준 ID는 문서에서 제거한다.
- 근거: 현재 Controller·Flyway V1~V3·MockMvc/Testcontainers 결과와 SCRUM-76 완료 기준.
- 영향: 문서가 현재 코드와 검증 증거를 반영하지만 SCRUM-76 자체는 FE/BE 인수와 남은 모듈 계약 전까지 완료 처리하지 않는다.
- 검증: 전체 backend test 56건, frontend build, git diff --check를 실행했다.

## DEV-20261018-01 — AGENTS 작업 기준 최신화

- 날짜: 2026-10-18
- 배경: AGENTS.md에 존재하지 않는 Notion 기준 페이지 ID와 과거 Jira 연결 오류 기록이 남아 있어 작업자가 잘못된 외부 기준과 연결을 사용할 위험이 있었다.
- 결정: 잘못된 Notion ID를 기준 페이지로 취급하지 않고, 현재 접근 가능한 Dodu v2.5 문서 링크를 보조 참고자료로 기록한다. Jira는 dodu-mobile 사이트와 Sprint 3를 기준으로 하며 팀 역할을 PO·PD·AI·BE·FE 담당자별로 명시한다.
- 근거: Notion 직접 조회의 object_not_found 응답, 접근 가능한 Dodu 문서 검색 결과, Jira accessible resources의 dodu-mobile 응답, 현재 저장소 구조와 Sprint 3 운영 상태.
- 대안: 확인되지 않은 3d64 페이지를 계속 기준으로 유지하는 방법은 잘못된 차단과 문서 오인을 만들 수 있어 배제했다.
- 영향: 이후 작업은 저장소 원문과 접근 가능한 Notion 문서, dodu-mobile Jira를 사용한다. 원격 기준 문서가 별도로 제공되면 해당 링크와 승인 상태를 다시 확인한다.
- 검증: AGENTS.md를 UTF-8로 재작성하고 링크·Jira 사이트·담당자·검증·커밋·동기화 규칙을 확인했다. git diff --check를 실행했다.
- 검증: AGENTS.md를 UTF-8로 재작성하고 문서·Jira·검증 규칙을 확인했다. git diff --check를 실행했다.

## DEV-20261018-12 — 참가자 코드 IP 임시 차단의 DB 연결

- 날짜: 2026-10-18
- 관련 범위: SCRUM-70 참가자 코드 실패 누적·임시 차단 API 연결
- 결정: 참가자 코드 실패를 IP별 PostgreSQL 행으로 저장하고, 10분 창에서 10회 실패 시 15분 차단한다. 차단 중 요청은 기존 만료시각을 연장하지 않으며, 성공한 진입은 해당 IP의 실패 행을 초기화한다. 차단 응답은 `429`, `TOO_MANY_ATTEMPTS`, `Retry-After`로 통일한다.
- 근거: SCRUM-70 승인 기준과 `docs/security/PARTICIPANT_ACCESS_CONTRACT.md`의 IP 차단 규칙. 예외·실패 상태를 인증 미완료와 섞지 않고 사진 재시도에는 적용하지 않는다.
- 영향: Flyway V4 `access_failure_rate_limit`와 `POST /api/access/enter`의 실제 DB 연결이 추가됐다. 원시 접근 코드와 자격정보는 저장하지 않는다.
- 남은 작업: 신뢰 프록시의 실제 IP 전달, 공유 저장소 보존·정리 상한, 봇 검증 제공자와 공격/오차단 QA는 운영 결정 및 별도 검증으로 남긴다.
- 검증: Docker/Testcontainers PostgreSQL 기반 rate limiter·Flyway·HTTP 429 통합 테스트 통과. 전체 백엔드 회귀 테스트는 다음 커밋 전 재실행한다.
## DEV-20261018-13 — 사진 결과 저장의 정상 접수 증거 조건

- 날짜: 2026-10-18
- 관련 범위: SCRUM-84 판정 결과 API의 지연 결과 저장
- 결정: 결과 callback은 요청 도착시각이 아니라 DB의 `valid_received_at`만 근거로 허용한다. 해당 시각이 `scheduled_at <= valid_received_at < scheduled_at + 30분`을 만족하고 attempt가 `Evaluating`이며 참가자가 철회되지 않은 경우에만 결과를 원자적으로 반영한다. 따라서 마감 후에도 정상 접수된 동일 attempt의 결과는 허용하고, 유효 접수 증거가 없는 결과는 409로 거절한다.
- 근거: D04·D08·D10·D11 및 SCRUM-84 완료 기준. 알림·callback 도착 자체는 사진 접수 증거가 아니다.
- 영향: 새 사진 제출 창은 기존처럼 닫힌 상태를 유지하며, 결과 저장 경계만 서버 기록 기반으로 강화한다.
- 검증: Docker/Testcontainers PostgreSQL MockMvc에서 마감 후 정상 접수 결과와 `valid_received_at` 없는 결과를 각각 확인했다.
## DEV-20261018-77 — 참가자 진입 mock 분기

- 배경: 참가자 코드 입력·오류·접근 종료 mock 분기와 포커스 복귀를 통합 브랜치에 포함한다.
- 근거: SCRUM-77 완료 기준이며 실제 인증 API는 SCRUM-24, 전체 온보딩은 SCRUM-78 범위다.
- 검증: 통합 브랜치에서 frontend lint/build를 다시 실행한다.
## DEV-20261018-78 — 온보딩·권한 복구 mock 분기

- 범위: 서비스 소개, PWA 안내, 권한 복구 흐름을 참가자 진입 뒤 UI mock으로 통합한다.
- 근거: SCRUM-78 완료 기준. 브라우저 권한과 실제 세션 처리는 후속 API 연동 범위다.
- 검증: 통합 브랜치에서 frontend lint/build를 재실행한다.
## DEV-20261018-79 — 약속 작성·관리 UI 통합

- 범위: 약속 작성 단계, 확인·상세·수정·취소 상태와 날짜·시간 경계 mock을 통합한다.
- 근거: SCRUM-79 완료 기준. 서버 저장 API와 실제 시간 판정은 SCRUM-23 연동 범위다.
- 검증: 통합 브랜치에서 frontend lint/build와 기존 promise preview 테스트를 재실행한다.
## DEV-20261021-01 — 병합 후 Flyway 마이그레이션 버전 충돌 해소

- 배경: `dev`의 참가자 접근 지원 마이그레이션과 기존 사진 제출 마이그레이션이 모두 V2라 통합 브랜치에서 Flyway가 중단됐다.
- 선택: 이미 존재하는 V2 참가자 접근 지원은 유지하고 사진 제출 마이그레이션 파일을 V5로 승격한다.
- 영향: 신규 통합 DB에서 V1→V5가 순서대로 적용되며 기존 스키마 의미는 유지된다.
- 검증: Docker 기반 `npm run verify`를 수정 후 재실행한다.
## DEV-20261021-02 Promise/photo lifecycle time and official-end gate

- Background: promise mutation and photo submission paths still used wall-clock calls or did not enforce the study's official intervention end.
- Choice: inject the application `Clock` into `PromiseController`, and require the photo submission path to pass `InterventionLifecycleGate` before creating a new attempt.
- Evidence: server time is the final authority; after official intervention end only already accepted work may be processed. A new photo submission is not an accepted receipt.
- Alternatives: retain direct `Instant.now()` and enforce the end only in workers; rejected because tests and request boundaries would diverge.
- Impact: deterministic promise boundary tests are possible, and late new submissions are rejected with `INTERVENTION_CLOSED` while delayed processing remains handled by the result path.
- Verification: targeted promise/photo integration tests and full `npm run verify`.

## DEV-20261023-01 자기보고 응답 기간 24시간 재확인

- 배경: PO가 최신 운영 문서 기준 자기보고 응답 기간을 24시간으로 재확인했고, 저장소 구현에는 과거 48시간 값이 남아 있었다.
- 결정: 자기보고 `expiresAt`을 최초 안내 가능 시각(`availableAt`)부터 24시간 뒤로 계산한다. `availableAt`은 포함하고 `expiresAt`은 제외한다.
- 근거: 사용자가 제공한 최신 기준 확인과 `OPEN_DECISIONS.md`의 D02·D07·D12, 수용 체크리스트의 24시간 만료 조건.
- 영향: 기존 48시간 정책 코드는 폐기하며, 공식 종료 후 개별 자기보고 접근도 동일한 24시간 경계를 따른다. 자기보고 미응답은 다음 약속 생성을 차단하지 않는다.
- 검증: `ValidationSurveyPolicyTests`에서 24시간 만료 시각과 만료 시각의 배타성을 확인한다.
