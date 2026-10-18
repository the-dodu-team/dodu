# 구현 및 티켓 동기화 기록

이 파일은 구현 완료 범위와 Jira 추적 상태를 함께 기록한다. 구현이 완료된 항목은 관련 티켓의 완료 조건과 검증 결과를 남긴다. 원격 Jira 상태를 변경할 수 없는 경우에는 완료로 표시하지 않고 사유를 기록한다.

## 2026-10-16

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
- Remaining: Docker integration test and live database verification.

### Notifications and photo authentication

- Status: policy layer confirmed; integration API remains open.
- Confirmed: T/T+10/T+20/T+30 boundaries, one concurrent Evaluating attempt, and late valid submission result preservation in existing policy tests.
- Remaining: notification delivery jobs and photo upload/evaluation API integration.

### Authentication history and self-report

- Status: eligibility and 48-hour response boundary implemented.
- Added: backend/src/main/java/com/dodu/survey/ValidationSurveyPolicy.java
- Verified: only AuthCompleted/AuthIncomplete are eligible; availableAt is inclusive and expiresAt is exclusive.
- Remaining: survey persistence/read API and history screen integration.

### Figma fidelity

- Status: not started.
- Remaining: compare Figma handoff frames and adjust frontend UI.

### Ticket synchronization

- Local tracking: NEXT04/NEXT05/NEXT07 and related DODU records.
- Remote Jira: the current connected session has not been verified against the target `dodu-mobile` site; ticket creation, Sprint 3 assignment, and completion updates remain pending until that site context is active.
- Remote tickets are not marked complete.

## SCRUM-77 UI mock 완료 (2026-10-18)
Jira Done. [QA 기록](SCRUM-77_QA_20261018.md) 참조. 실제 인증은 SCRUM-24, 전체 온보딩은 SCRUM-78.
