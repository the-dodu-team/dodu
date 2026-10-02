# 개발 결정 기록

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
