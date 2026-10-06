# 개발 결정 기록

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
