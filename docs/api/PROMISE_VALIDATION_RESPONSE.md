# 약속 검증 오류 응답

## 2026-11-12 운영 신고

- 사용자 제공 Render 로그 13:32:53Z에서 PromiseValidationException / PROMISE_SCHEDULE_AFTER_STUDY_END 확인. CORS·Blueprint Sync 문제가 아니라 서버의 약속 시각 검증 거절이다.
- 기존 공통 예외 처리기가 이 예외를 일반 Exception으로 처리해 500 INTERNAL_ERROR로 반환했다.
- PromiseController에 한정된 검증 예외 처리기를 추가해 400 VALIDATION 및 허용된 오류 코드를 반환한다. 검증 조건·트랜잭션·연구 날짜는 변경하지 않는다.
- 코드 허용 목록: INVALID_TASK_TITLE, INVALID_TOOL_TYPE, DATETIME_OFFSET_REQUIRED, PROMISE_SCHEDULE_TOO_SOON, PROMISE_SCHEDULE_TOO_FAR, PROMISE_SCHEDULE_AFTER_STUDY_END. 내부 예외 원문은 응답하지 않는다.

## 운영 기간과 배포

- 오류 응답 수정만으로 종료 후 약속 생성이 허용되지는 않는다.
- 사용자는 테스트 기간 연장을 요청했다. 새 종료 일시(KST)는 아직 미확정이므로 운영 DB는 변경하지 않았다.
- 기본 기술 테스트 연구는 scripts/prepare-smoke-participant.sql에서 생성 시점 +7일 개입, +9일 접근으로 구성된다. 실제 DB 날짜를 읽은 것은 아니며 운영 기간으로 자동 연장하지 않는다.
- 날짜 변경 시 대상 연구 확인, 개입·자기보고·접근 종료·사진/원시 데이터 삭제 시점의 기존 정책 관계를 함께 확인해야 한다. 이미 만료된 세션을 자동 연장하거나 철회 참가자를 복구하지 않는다.
- Render 최신 dev 배포 후 같은 잘못된 시각 요청이 400과 해당 코드로 응답하는지 확인한다. 유효한 시각의 실제 생성은 운영 QA로 별도 확인한다.

## 검증

- PromiseControllerIntegrationTests 및 PromiseRequestValidatorTests 총 21건, 실패·오류·건너뜀 0. Docker PostgreSQL에서 실행.
- 개입 종료 초과 400 및 약속·멱등성 기록 미생성, 잘못된 작업명 400, 기존 생성·동시성·멱등성 회귀 포함.
- 전체 백엔드·FE 빌드·운영 DB 수정·Production 재현은 미실행.
