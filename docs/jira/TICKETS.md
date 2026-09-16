# DODU Jira 티켓 명세

2026-09-16 작성. **Jira 등록 및 검증 완료**입니다. 로컬 ID와 실제 Jira 키를 함께 기록합니다.
기존 에픽 4개·스토리 10개를 확인하고 실행 작업 DODU-15~DODU-37을 추가했습니다. 기존 티켓 본문·담당·상태는 유지하고 관련 링크를 추가했습니다.

| 로컬 ID | 제목 | 담당 | 구분 | 예상 인일 |
|---|---|---|---|---|
| S1-FS01 | [FS] 팀 실행환경 인수 및 원격 저장소·CI 기반 구성 | mono | Sprint 1 후보 | 1~1.5 |
| S1-BE01 | [BE] 공통 API·시간·오류 계약 초안 작성 | 문은서 | Sprint 1 후보 | 1~1.5 |
| S1-FE01 | [FE] 공통 화면 구조와 입력·안내 컴포넌트 구성 | Elle Yoo | Sprint 1 후보 | 1.5~2 |
| S1-FS02 | [FS] 프런트엔드 API 호출 기반과 mock fixture 구성 | mono | Sprint 1 후보 | 1~1.5 |
| S1-BE02 | [BE] 데이터 저장 기술 선택 및 최소 마이그레이션 기반 | 문은서 | Sprint 1 후보 | 1.5~2 |
| S1-FE02 | [FE] 서비스 소개·권한 목적·데이터 안내 화면 작성 | Elle Yoo | Sprint 1 후보 | 1.5~2 |
| S1-BE03 | [BE] 참가자 접근·철회·삭제의 보안 계약 설계 | 문은서 | Sprint 1 후보 | 1.5~2 |
| S1-FE03 | [FE] 약속 작성·내용 확인 프로토타입 구현 | Elle Yoo | Sprint 1 후보 | 2~2.5 |
| S1-FS03 | [FS] 알림·카메라·AI 연동 가능성 기술 검토 | mono | Sprint 1 후보 | 1.5~2 |
| S1-BE04 | [BE] 시간·상태·로그 경계 검증 시나리오와 테스트 기반 | 문은서 | Sprint 1 후보 | 1~1.5 |
| S1-FE04 | [FE] 프로토타입 사용성·접근성 확인 및 UT 자료 준비 | Elle Yoo | Sprint 1 후보 | 1~1.5 |
| S1-FS04 | [FS] Sprint 1 통합 검증·리뷰·인수 기록 | mono | Sprint 1 후보 | 1~1.5 |
| DEC01 | [결정] 예약 날짜·종료·정확한 시간 경계 검토 | 문은서 | 결정 검토 | 0.5~1 |
| DEC02 | [결정] 인증 오류·비동기 결과·철회·로그 계약 검토 | 문은서 | 결정 검토 | 0.5~1 |
| DEC03 | [결정] 자기보고 문항·자정·노출·대상 검토 | mono | 결정 검토 | 0.5~1 |
| DEC04 | [결정] 반복 지표·지원 환경·AI QA·Go 기준 검토 | mono | 결정 검토 | 0.5~1 |
| NEXT01 | [BE] 참가자 식별·재발급·접근 제어 구현 | 문은서 | 후속 백로그 | 재추정 |
| NEXT02 | [BE] 약속 생성·수정·취소·상태 복원 API 구현 | 문은서 | 후속 백로그 | 재추정 |
| NEXT03 | [FE] 참가자·약속 API 연결 및 서버 오류 표시 | Elle Yoo | 후속 백로그 | 재추정 |
| NEXT04 | [FS] 알림 스케줄·사진 업로드·판정 연동 세분화 및 구현 | mono | 후속 백로그 | 재추정 |
| NEXT05 | [FE] 인증 이력·자기보고 화면과 API 연동 세분화 | Elle Yoo | 후속 백로그 | 재추정 |
| NEXT06 | [BE] 철회·보관 만료·삭제 요청 처리 구현 | 문은서 | 후속 백로그 | 재추정 |
| NEXT07 | [BE] 인증 이력·자기보고·이벤트 집계 API 세분화 | 문은서 | 후속 백로그 | 재추정 |

## S1-FS01 · [DODU-15](https://dodu-app.atlassian.net/browse/DODU-15) [FS] 팀 실행환경 인수 및 원격 저장소·CI 기반 구성

- 구분: Sprint 1 후보
- 담당 제안: mono / 리뷰: 문은서
- 예상 작업량: 1~1.5 인일
- 선행 로컬 티켓: 없음
- 관련 OPEN: 없음
- Jira 등록: [DODU-15](https://dodu-app.atlassian.net/browse/DODU-15) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: 없음
- 관련 기존 스토리: 공통 기반

### 작업 범위

- 기존 Java/React·통합 명령을 팀원이 재현한다. 기존 환경을 다시 만드는 작업은 제외한다.
- 팀 원격 저장소 위치를 확인하고 CI에서 npm ci와 npm run verify를 실행하도록 구성한다. 원격 저장소가 없으면 연결을 선행 조건으로 기록한다.

### 완료 기준

- Elle Yoo와 문은서의 실행 결과·막힌 지점을 기록한다.
- 깨끗한 환경의 CI 성공과 검증 실패 시 실패 상태를 확인한다.
- 캐시·산출물·비밀값을 추적하지 않는다. 호스팅·배포는 범위 밖이다.

### 근거

- README.md
- docs/DEVELOPMENT_CONVENTIONS.md: 저장소와 의존성·검증

## S1-BE01 · [DODU-16](https://dodu-app.atlassian.net/browse/DODU-16) [BE] 공통 API·시간·오류 계약 초안 작성

- 구분: Sprint 1 후보
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 1~1.5 인일
- 선행 로컬 티켓: 없음
- 관련 OPEN: D05, D08, D11
- Jira 등록: [DODU-16](https://dodu-app.atlassian.net/browse/DODU-16) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: 없음
- 관련 기존 스토리: 공통 기반

### 작업 범위

- 응답·오류·날짜 직렬화와 FE용 예시를 정의한다.
- Clock 주입, 서버 기준 시각 및 KST 일자 변환의 기술 방향을 정리한다.
- OPEN 경계 비교·판정 전이는 계약의 미정 항목으로 표시한다.

### 완료 기준

- FE가 사용할 정상·입력오류·기술오류 fixture를 제공한다.
- 오프셋 없는 시각을 API 계약에 사용하지 않는다.
- D05/D08/D11의 제안을 확정한 코드·테스트가 없다. mono와 계약 리뷰를 완료한다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §0·§8
- docs/dodu/sources/FLOW_LOG_MAPPING_v2.5.md: §3~4

## S1-FE01 · [DODU-17](https://dodu-app.atlassian.net/browse/DODU-17) [FE] 공통 화면 구조와 입력·안내 컴포넌트 구성

- 구분: Sprint 1 후보
- 담당 제안: Elle Yoo / 리뷰: mono
- 예상 작업량: 1.5~2 인일
- 선행 로컬 티켓: 없음
- 관련 OPEN: 없음
- Jira 등록: [DODU-17](https://dodu-app.atlassian.net/browse/DODU-17) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: 없음
- 관련 기존 스토리: 공통 기반

### 작업 범위

- 현재 준비 화면을 기준으로 헤더·본문·버튼·입력·오류 안내 컴포넌트를 만든다.
- mono가 컴포넌트 분리 예시와 PR 리뷰를 지원한다. 새 UI 라이브러리는 필요성을 검토한 뒤 도입한다.

### 완료 기준

- 모바일·데스크톱 폭에서 가로 넘침 없이 표시된다.
- 키보드 포커스·label·disabled·상태 안내를 확인한다.
- 정상·로딩·빈 상태·오류 예시를 리뷰할 수 있다. 제품 예약·인증은 실행하지 않는다.

### 근거

- docs/dodu/sources/PRD_v2.5.md: §19
- docs/DEVELOPMENT_CONVENTIONS.md: React와 TypeScript 구조

## S1-FS02 · [DODU-18](https://dodu-app.atlassian.net/browse/DODU-18) [FS] 프런트엔드 API 호출 기반과 mock fixture 구성

- 구분: Sprint 1 후보
- 담당 제안: mono / 리뷰: 문은서
- 예상 작업량: 1~1.5 인일
- 선행 로컬 티켓: S1-BE01
- 관련 OPEN: 없음
- Jira 등록: [DODU-18](https://dodu-app.atlassian.net/browse/DODU-18) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-16
- 관련 기존 스토리: 공통 기반

### 작업 범위

- 합의된 공통 계약에 맞춰 타입·응답 검증·요청 취소·오류 처리를 만든다.
- health API의 실연결과 개발용 fixture 예시를 제공해 FE가 독립적으로 작업하도록 한다.

### 완료 기준

- 잘못된 응답·네트워크 실패를 성공으로 표시하지 않는다.
- 개발 fixture가 실제 저장·알림·인증으로 오인되지 않는다.
- Elle Yoo가 예시를 사용해 정상·오류 상태를 재현할 수 있다.

### 근거

- docs/DEVELOPMENT_CONVENTIONS.md: React와 TypeScript 구조·API 계약

## S1-BE02 · [DODU-19](https://dodu-app.atlassian.net/browse/DODU-19) [BE] 데이터 저장 기술 선택 및 최소 마이그레이션 기반

- 구분: Sprint 1 후보
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 1.5~2 인일
- 선행 로컬 티켓: S1-BE01
- 관련 OPEN: D06, D08, D10
- Jira 등록: [DODU-19](https://dodu-app.atlassian.net/browse/DODU-19) / 담당·에픽·링크 검증 완료
- 에픽: DODU-6
- 선행 Jira 키: DODU-16
- 관련 기존 스토리: 공통 기반

### 작업 범위

- DB·마이그레이션 후보와 선택 근거를 기술 결정 문서에 남긴다.
- 선택한 저장소의 로컬 연결·초기 마이그레이션·테스트 격리를 구성한다.
- 업무 스키마는 초안으로 작성하고 OPEN 상태·날짜 제약은 확정하지 않는다.

### 완료 기준

- 깨끗한 DB에서 마이그레이션이 동작하고 재실행 결과를 확인한다.
- 연결정보를 코드·Git에 넣지 않는다.
- 약속·시도·이벤트·설문 연결 초안에 미결정 필드와 D-ID가 표시된다.

### 근거

- docs/dodu/IMPLEMENTATION_CONTEXT.md: 기술설계 제안
- docs/dodu/sources/PRD_v2.5.md: §17·§19

## S1-FE02 · [DODU-20](https://dodu-app.atlassian.net/browse/DODU-20) [FE] 서비스 소개·권한 목적·데이터 안내 화면 작성

- 구분: Sprint 1 후보
- 담당 제안: Elle Yoo / 리뷰: mono
- 예상 작업량: 1.5~2 인일
- 선행 로컬 티켓: S1-FE01
- 관련 OPEN: D03, D13
- Jira 등록: [DODU-20](https://dodu-app.atlassian.net/browse/DODU-20) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-17
- 관련 기존 스토리: DODU-2

### 작업 범위

- 서비스 목적, 인증과 실제 시작의 차이, 알림·카메라 목적, 보관기간·철회·삭제·선택 설문을 안내한다.
- 참가자 등록·권한 요청·동의 수집은 연결하지 않는 프로토타입으로 만든다.

### 완료 기준

- 사진 인증을 실제 시작 증명으로 표현하지 않는다.
- 철회와 과거 데이터 삭제 요청을 구분한다.
- 지원 기기·공식 종료시각·문의 경로를 임의로 확정하지 않는다. 운영 정보 미정 영역을 리뷰 문서에 표시한다.

### 근거

- docs/dodu/sources/PRD_v2.5.md: F-08
- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §7·§10~15

## S1-BE03 · [DODU-21](https://dodu-app.atlassian.net/browse/DODU-21) [BE] 참가자 접근·철회·삭제의 보안 계약 설계

- 구분: Sprint 1 후보
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 1.5~2 인일
- 선행 로컬 티켓: S1-BE01, S1-BE02
- 관련 OPEN: D03, D05, D12
- Jira 등록: [DODU-21](https://dodu-app.atlassian.net/browse/DODU-21) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-16, DODU-19
- 관련 기존 스토리: DODU-2

### 작업 범위

- 폐쇄형 참가자 식별, 접근정보 재발급·무효화, 타인 접근 차단을 설계한다.
- 철회 중단과 과거 데이터 삭제의 처리 경계를 작성한다. 기능 구현은 별도 티켓으로 진행한다.

### 완료 기준

- 타인 식별자 대입·재발급 전 토큰·철회 후 접근에 대한 검증 시나리오가 있다.
- 공식 종료 후 접근과 최종 상태·설문 적격성은 D03/D05/D12 대기로 남긴다.
- 정식 회원가입·소셜 로그인을 추가하지 않는다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §11~13

## S1-FE03 · [DODU-22](https://dodu-app.atlassian.net/browse/DODU-22) [FE] 약속 작성·내용 확인 프로토타입 구현

- 구분: Sprint 1 후보
- 담당 제안: Elle Yoo / 리뷰: mono
- 예상 작업량: 2~2.5 인일
- 선행 로컬 티켓: S1-FE01, S1-FS02
- 관련 OPEN: D06, D11
- Jira 등록: [DODU-22](https://dodu-app.atlassian.net/browse/DODU-22) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-17, DODU-18
- 관련 기존 스토리: DODU-3

### 작업 범위

- 작업명·예정 시각·컴퓨터/책 선택 및 확인 화면을 작성한다.
- 메모리 내 작성 상태만 사용한다. 예약 저장 API·활성 약속 생성·시간 허용 경계 검증은 연결하지 않는다.

### 완료 기준

- 화면 이동 후 입력을 수정하고 확인할 수 있다.
- KST 표시 및 컴퓨터/실제 책 범위 안내가 있다.
- 제출 시 저장 완료·예약 완료를 표시하지 않는다. 새로고침 보존·알림은 제공하지 않는다.
- mono와 입력 상태·접근성 리뷰를 완료한다.

### 근거

- docs/dodu/sources/PRD_v2.5.md: F-01
- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §1·§4

## S1-FS03 · [DODU-23](https://dodu-app.atlassian.net/browse/DODU-23) [FS] 알림·카메라·AI 연동 가능성 기술 검토

- 구분: Sprint 1 후보
- 담당 제안: mono / 리뷰: 문은서
- 예상 작업량: 1.5~2 인일
- 선행 로컬 티켓: 없음
- 관련 OPEN: D04, D10, D13
- Jira 등록: [DODU-23](https://dodu-app.atlassian.net/browse/DODU-23) / 담당·에픽·링크 검증 완료
- 에픽: DODU-4
- 선행 Jira 키: 없음
- 관련 기존 스토리: DODU-8, DODU-9

### 작업 범위

- 웹/PWA의 알림·촬영·업로드 제약과 필요한 운영 환경을 조사하고 가능한 기기에서 최소 확인한다.
- AI 입력·출력·timeout·비용·모델 버전 관리 후보를 비교한다.
- 실제 참가자 데이터 수집·운영 제공자 계약·유료 배포는 진행하지 않는다.

### 완료 기준

- 시험 환경과 실제 실행 여부를 구분한 결과표가 있다.
- AI 정확도·threshold·지원 환경을 시험 없이 확정하지 않는다.
- 모델 QA 샘플과 판단 기준 초안, 남은 D13 결정이 정리된다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §6·§7·§14

## S1-BE04 · [DODU-24](https://dodu-app.atlassian.net/browse/DODU-24) [BE] 시간·상태·로그 경계 검증 시나리오와 테스트 기반

- 구분: Sprint 1 후보
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 1~1.5 인일
- 선행 로컬 티켓: S1-BE01
- 관련 OPEN: D04, D05, D06, D08, D10, D11
- Jira 등록: [DODU-24](https://dodu-app.atlassian.net/browse/DODU-24) / 담당·에픽·링크 검증 완료
- 에픽: DODU-4
- 선행 Jira 키: DODU-16
- 관련 기존 스토리: DODU-11, DODU-14

### 작업 범위

- Clock을 교체할 수 있는 테스트 기반과 KST 변환 예시를 구성한다.
- 동시 요청·수정 전 알림·마감 전 접수/후 판정·철회 후 결과 시나리오를 계약 수준으로 정리한다.

### 완료 기준

- 확정된 불변 조건과 미정 기대 결과가 구분된다.
- OPEN 비교 연산·오류 종료 결과를 구현 테스트의 정답으로 만들지 않는다.
- 후속 테스트를 담당자와 D-ID로 추적할 수 있다.

### 근거

- docs/dodu/ACCEPTANCE_CHECKLIST.md
- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §5·§8·§13

## S1-FE04 · [DODU-25](https://dodu-app.atlassian.net/browse/DODU-25) [FE] 프로토타입 사용성·접근성 확인 및 UT 자료 준비

- 구분: Sprint 1 후보
- 담당 제안: Elle Yoo / 리뷰: mono
- 예상 작업량: 1~1.5 인일
- 선행 로컬 티켓: S1-FE02, S1-FE03
- 관련 OPEN: 없음
- Jira 등록: [DODU-25](https://dodu-app.atlassian.net/browse/DODU-25) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-20, DODU-22
- 관련 기존 스토리: 공통 기반

### 작업 범위

- 서비스 이해·약속 작성·도구 선택·내용 확인 시나리오를 작성한다.
- 키보드·좁은 화면·오류 안내를 점검하고 재현 절차를 기록한다.

### 완료 기준

- 관찰 과제·진행 순서·기록 양식과 화면이 연결된다.
- 디자인 검토와 실제 참가자 UT 수행을 구분한다.
- 화면 확인 결과로 실제 시작 증가·행동 효과를 주장하지 않는다.

### 근거

- docs/dodu/sources/PRD_v2.5.md: §12.1·§20

## S1-FS04 · [DODU-26](https://dodu-app.atlassian.net/browse/DODU-26) [FS] Sprint 1 통합 검증·리뷰·인수 기록

- 구분: Sprint 1 후보
- 담당 제안: mono / 리뷰: 문은서
- 예상 작업량: 1~1.5 인일
- 선행 로컬 티켓: S1-FS01, S1-FS02, S1-FS03, S1-BE02, S1-BE03, S1-BE04, S1-FE04
- 관련 OPEN: 없음
- Jira 등록: [DODU-26](https://dodu-app.atlassian.net/browse/DODU-26) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-15, DODU-18, DODU-23, DODU-19, DODU-21, DODU-24, DODU-25
- 관련 기존 스토리: 공통 기반

### 작업 범위

- 팀 PR 리뷰와 최소 연결 확인을 수행하고 npm run verify·통합 JAR 동작을 확인한다.
- 구현·설계·프로토타입·미실행 항목을 구분해 인수 기록을 작성한다.

### 완료 기준

- 통합 실행·종료, 단일 JAR의 화면·정적 자원·health 응답을 확인한다.
- OPEN 관련 제품 동작이 임의 구현되지 않았음을 검토한다.
- 다음 Sprint의 차단 요인과 담당자가 정리된다. 전체 MVP 출시 완료로 처리하지 않는다.

### 근거

- README.md
- docs/dodu/IMPLEMENTATION_CONTEXT.md: 제품과 Sprint 범위

## DEC01 · [DODU-27](https://dodu-app.atlassian.net/browse/DODU-27) [결정] 예약 날짜·종료·정확한 시간 경계 검토

- 구분: 결정 검토
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 0.5~1 인일
- 선행 로컬 티켓: 없음
- 관련 OPEN: D03, D06, D11
- Jira 등록: [DODU-27](https://dodu-app.atlassian.net/browse/DODU-27) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: 없음
- 관련 기존 스토리: DODU-3, DODU-7

### 작업 범위

- 자정 이동·취소·기술 실패 후 재예약·정확히 공식 종료/T+30·마지막 설문 접근 사례와 선택지를 작성한다.
- 개발 담당자는 기술 영향을 정리하며 제품 책임자 승인 전 결과를 확정하지 않는다.

### 완료 기준

- D-ID별 선택지·영향·승인 요청사항이 있다.
- 이 티켓의 검토 완료만으로 D-ID를 APPROVED로 바꾸지 않는다. 승인 근거는 별도 기록한다.

### 근거

- docs/dodu/OPEN_DECISIONS.md: D03·D06·D11

## DEC02 · [DODU-28](https://dodu-app.atlassian.net/browse/DODU-28) [결정] 인증 오류·비동기 결과·철회·로그 계약 검토

- 구분: 결정 검토
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 0.5~1 인일
- 선행 로컬 티켓: 없음
- 관련 OPEN: D04, D05, D08, D10
- Jira 등록: [DODU-28](https://dodu-app.atlassian.net/browse/DODU-28) / 담당·에픽·링크 검증 완료
- 에픽: DODU-4
- 선행 Jira 키: 없음
- 관련 기존 스토리: DODU-9, DODU-10, DODU-11, DODU-14

### 작업 범위

- 기술오류 마감 복구·판정 중 재알림·동시 제출·역순 결과·철회 결과·시도/이벤트 키를 정리한다.

### 완료 기준

- 정상 접수 사진 결과 대기·철회 후 수집 중단 등 확정 원칙을 보존한다.
- 선택지·담당·승인 요청을 기록하고 시간 연장·중복 처리 방식을 임의 확정하지 않는다.

### 근거

- docs/dodu/OPEN_DECISIONS.md: D04·D05·D08·D10

## DEC03 · [DODU-29](https://dodu-app.atlassian.net/browse/DODU-29) [결정] 자기보고 문항·자정·노출·대상 검토

- 구분: 결정 검토
- 담당 제안: mono / 리뷰: 문은서
- 예상 작업량: 0.5~1 인일
- 선행 로컬 티켓: 없음
- 관련 OPEN: D01, D02, D07, D12
- Jira 등록: [DODU-29](https://dodu-app.atlassian.net/browse/DODU-29) / 담당·에픽·링크 검증 완료
- 에픽: DODU-5
- 선행 Jira 키: 없음
- 관련 기존 스토리: DODU-13

### 작업 범위

- Elle Yoo는 문항·노출 시나리오 검토에 참여하고 mono가 선택지와 구현 영향을 정리한다.
- 제품·디자인 책임자의 실제 시작 정의, 기준일, 적격성 및 만료 승인이 필요하다.

### 완료 기준

- 인증 전 시작·미인증 시작·Prompted 만료·여러 설문·취소/기술 실패 사례를 포함한다.
- 미응답을 미시작으로 해석하거나 다음 약속을 차단하지 않는다.
- 승인 전 문항과 기준일은 확정 계약으로 사용하지 않는다.

### 근거

- docs/dodu/OPEN_DECISIONS.md: D01·D02·D07·D12

## DEC04 · [DODU-30](https://dodu-app.atlassian.net/browse/DODU-30) [결정] 반복 지표·지원 환경·AI QA·Go 기준 검토

- 구분: 결정 검토
- 담당 제안: mono / 리뷰: 문은서
- 예상 작업량: 0.5~1 인일
- 선행 로컬 티켓: S1-FS03
- 관련 OPEN: D09, D13
- Jira 등록: [DODU-30](https://dodu-app.atlassian.net/browse/DODU-30) / 담당·에픽·링크 검증 완료
- 에픽: DODU-6
- 선행 Jira 키: DODU-23
- 관련 기존 스토리: DODU-14

### 작업 범위

- 생성일/예정 시작일 지표, 최소 관찰량·판단 보류 기준, 지원 환경·AI QA의 필요한 결정을 정리한다.
- 행동검증 운영안과 제품 책임자·AI 검토 담당이 없는 경우 미확보로 기록한다.

### 완료 기준

- 기술 시험 결과와 제품 효과 판단 기준을 분리한다.
- 임의 정확도·Go 임계값·지원 기기 목록을 승인된 정책으로 쓰지 않는다.

### 근거

- docs/dodu/OPEN_DECISIONS.md: D09·D13

## NEXT01 · [DODU-31](https://dodu-app.atlassian.net/browse/DODU-31) [BE] 참가자 식별·재발급·접근 제어 구현

- 구분: 후속 백로그
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 정책·기술 계약 확정 후 세분화·추정
- 선행 로컬 티켓: S1-BE02, S1-BE03, DEC01, DEC02
- 관련 OPEN: D03, D05
- Jira 등록: [DODU-31](https://dodu-app.atlassian.net/browse/DODU-31) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-19, DODU-21, DODU-27, DODU-28
- 관련 기존 스토리: DODU-2

### 작업 범위

- 승인된 접근 종료·철회 계약으로 폐쇄형 식별을 구현한다. 제품 책임자 승인 후 착수한다.

### 완료 기준

- 타인 데이터 접근을 차단한다.
- 재발급 시 이전 접근정보가 무효화된다.
- 철회 후 접근·수집·개입을 차단한다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §12~13

## NEXT02 · [DODU-32](https://dodu-app.atlassian.net/browse/DODU-32) [BE] 약속 생성·수정·취소·상태 복원 API 구현

- 구분: 후속 백로그
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 정책·기술 계약 확정 후 세분화·추정
- 선행 로컬 티켓: NEXT01, DEC01, DEC02
- 관련 OPEN: D03, D05, D06, D11
- Jira 등록: [DODU-32](https://dodu-app.atlassian.net/browse/DODU-32) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-31, DODU-27, DODU-28
- 관련 기존 스토리: DODU-3, DODU-7

### 작업 범위

- 승인된 경계·날짜 사용 이력 계약으로 예약 및 상태 저장을 구현한다.

### 완료 기준

- 활성 1개·동일 예정 시작일 1개를 동시 요청에서도 유지한다.
- 시작 전 수정 1회와 조건 재검증·취소 후 횟수 비복구를 적용한다.
- 재진입으로 종료시각을 초기화하지 않는다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §1~2·§8

## NEXT03 · [DODU-33](https://dodu-app.atlassian.net/browse/DODU-33) [FE] 참가자·약속 API 연결 및 서버 오류 표시

- 구분: 후속 백로그
- 담당 제안: Elle Yoo / 리뷰: mono
- 예상 작업량: 정책·기술 계약 확정 후 세분화·추정
- 선행 로컬 티켓: S1-FE03, NEXT01, NEXT02
- 관련 OPEN: D03, D05, D06, D11
- Jira 등록: [DODU-33](https://dodu-app.atlassian.net/browse/DODU-33) / 담당·에픽·링크 검증 완료
- 에픽: DODU-1
- 선행 Jira 키: DODU-22, DODU-31, DODU-32
- 관련 기존 스토리: DODU-2, DODU-3, DODU-7

### 작업 범위

- mono와 함께 mock을 실제 참가자·예약 API에 연결한다.

### 완료 기준

- 서버 저장 성공을 확인한 뒤에만 예약 완료를 표시한다.
- 서버 충돌·권한·네트워크 오류를 구분한다.
- 재진입 시 서버 상태를 복원하며 로컬에서 시간을 연장하지 않는다.

### 근거

- docs/dodu/sources/PRD_v2.5.md: F-01·F-06·F-08

## NEXT04 · [DODU-34](https://dodu-app.atlassian.net/browse/DODU-34) [FS] 알림 스케줄·사진 업로드·판정 연동 세분화 및 구현

- 구분: 후속 백로그
- 담당 제안: mono / 리뷰: 문은서
- 예상 작업량: 정책·기술 계약 확정 후 세분화·추정
- 선행 로컬 티켓: NEXT02, DEC02, DEC04
- 관련 OPEN: D04, D05, D08, D10, D11, D13
- Jira 등록: [DODU-34](https://dodu-app.atlassian.net/browse/DODU-34) / 담당·에픽·링크 검증 완료
- 에픽: DODU-4
- 선행 Jira 키: DODU-32, DODU-28, DODU-30
- 관련 기존 스토리: DODU-8, DODU-9, DODU-10, DODU-11

### 작업 범위

- 착수 전 알림·업로드·AI adapter별 작은 구현 티켓으로 분할한다. 현재는 범위 추적용 상위 작업이다.
- BE는 서버 상태·트랜잭션, FE는 승인된 촬영 UI를 별도 분할 티켓에서 담당한다.

### 완료 기준

- T/T+10/T+20 일정, 성공 후 재알림 중단과 수정 전 작업 무효화를 검증한다.
- 정상 접수 유효 사진은 마감 후 결과를 기다린다.
- 거절·기술오류·권한 거부를 분리하고 사용자 판정 횟수 제한을 추가하지 않는다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §3~8

## NEXT05 · [DODU-35](https://dodu-app.atlassian.net/browse/DODU-35) [FE] 인증 이력·자기보고 화면과 API 연동 세분화

- 구분: 후속 백로그
- 담당 제안: Elle Yoo / 리뷰: mono
- 예상 작업량: 정책·기술 계약 확정 후 세분화·추정
- 선행 로컬 티켓: NEXT03, DEC03, DEC04, NEXT07
- 관련 OPEN: D01, D02, D03, D07, D09, D12
- Jira 등록: [DODU-35](https://dodu-app.atlassian.net/browse/DODU-35) / 담당·에픽·링크 검증 완료
- 에픽: DODU-5
- 선행 Jira 키: DODU-33, DODU-29, DODU-30, DODU-37
- 관련 기존 스토리: DODU-12, DODU-13

### 작업 범위

- 문은서가 API 계약을 제공한 뒤 이력·설문 화면별 작은 작업으로 나눠 착수한다.

### 완료 기준

- 인증 완료/미완료와 실제 시작 응답을 분리한다.
- 설문 미응답이 새 약속 생성을 막지 않는다.
- 승인된 자정·11시·48시간 계약에 맞춰 표시하고 사용자 실패로 표현하지 않는다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §9~10

## NEXT06 · [DODU-36](https://dodu-app.atlassian.net/browse/DODU-36) [BE] 철회·보관 만료·삭제 요청 처리 구현

- 구분: 후속 백로그
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 정책·기술 계약 확정 후 세분화·추정
- 선행 로컬 티켓: NEXT01, DEC02, DEC03
- 관련 OPEN: D03, D05, D08, D12
- Jira 등록: [DODU-36](https://dodu-app.atlassian.net/browse/DODU-36) / 담당·에픽·링크 검증 완료
- 에픽: DODU-6
- 선행 Jira 키: DODU-31, DODU-28, DODU-29
- 관련 기존 스토리: DODU-2, DODU-14

### 작업 범위

- 실제 사용되는 사진·이벤트·설문·식별 연결을 대상으로 삭제와 철회 처리를 세분화한다.

### 완료 기준

- 사진 종료 후 최대 30일, 연결 원시 데이터 최대 90일 보관을 적용한다.
- 삭제 요청 시 연결 원시 데이터를 삭제하고 철회 이후 결과를 반영하지 않는다.
- 철회만 한 경우와 과거 데이터 삭제 요청을 구분한다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §11~13

## NEXT07 · [DODU-37](https://dodu-app.atlassian.net/browse/DODU-37) [BE] 인증 이력·자기보고·이벤트 집계 API 세분화

- 구분: 후속 백로그
- 담당 제안: 문은서 / 리뷰: mono
- 예상 작업량: 정책·기술 계약 확정 후 세분화·추정
- 선행 로컬 티켓: NEXT02, DEC01, DEC02, DEC03, DEC04
- 관련 OPEN: D01, D02, D03, D05, D07, D08, D09, D12
- Jira 등록: [DODU-37](https://dodu-app.atlassian.net/browse/DODU-37) / 담당·에픽·링크 검증 완료
- 에픽: DODU-6
- 선행 Jira 키: DODU-32, DODU-27, DODU-28, DODU-29, DODU-30
- 관련 기존 스토리: DODU-12, DODU-13, DODU-14

### 작업 범위

- 착수 전 이력 조회·설문 노출/제출/만료·이벤트 집계로 구현 티켓을 분할한다.

### 완료 기준

- 기술 실패·취소·철회를 인증 미완료에 합산하지 않는다.
- 설문 제출로 인증 결과를 변경하지 않는다.
- 이벤트 중복·분모·날짜 기준은 승인된 계약을 따른다.

### 근거

- docs/dodu/sources/SERVICE_POLICY_v2.5.md: §9~10·§13
- docs/dodu/sources/FLOW_LOG_MAPPING_v2.5.md: §3~7
