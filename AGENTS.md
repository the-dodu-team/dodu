# Dodu 작업 지침

## 적용 범위

이 문서는 Dodu v2.5 저장소의 개발·문서·검증·Jira 작업에 적용한다. CareBell이나 다른 프로젝트의 기술 스택·정책·기능을 Dodu에 자동 적용하지 않는다.

저장소 기준 경로는 C:\Users\GM.pc\Desktop\dodu이다. 제품의 플랫폼 Baseline은 웹앱/PWA이며, 실제 프런트엔드·백엔드·DB·알림·AI·배포 구조는 저장소를 먼저 확인한다.

## 작업 시작 시 확인할 자료

작업마다 다음 자료를 읽고 현재 상태와 정책을 확인한다.

1. docs/dodu/IMPLEMENTATION_CONTEXT.md
2. docs/dodu/OPEN_DECISIONS.md
3. 관련 원문
   - docs/dodu/sources/PRD_v2.5.md
   - docs/dodu/sources/SERVICE_POLICY_v2.5.md
   - docs/dodu/sources/FLOW_LOG_MAPPING_v2.5.md
4. 검증 작업이면 docs/dodu/ACCEPTANCE_CHECKLIST.md
5. Jira 추적이면 docs/jira/IMPLEMENTATION_STATUS.md
6. 루트 README.md, package.json, frontend/package.json, backend/pom.xml, Docker·배포 설정

이전 인수인계에 있던 Notion 페이지 ID 3d64c293-890d-80cc-940b-dc3cef7aa32c는 유효한 기준 페이지로 확인되지 않았다. 이 ID를 찾지 못한 것을 작업 차단 사유로 사용하지 않으며, 해당 페이지를 기준 문서라고 주장하지 않는다.

현재 연결 가능한 Notion Dodu 문서는 다음과 같다. 이 문서들은 원문 Baseline의 보조 확인 자료로 사용하며, 저장소 원문과 승인 상태를 우선한다.

- [MVP PRD v2.5 Baseline](https://app.notion.com/p/3e7801154662802c9fe4d8ff1faf267e)
- [서비스 운영 정책 v2.5](https://app.notion.com/p/3e780115466280188446daadeb64a950)
- [User Flow / State Flow / 로그-검증 매핑](https://app.notion.com/p/3e780115466280298f5af827857c2fe5)
- [D01~D13 최종 결정문](https://app.notion.com/p/3e7801154662808abfb8d72c2ff89aeb)

Notion 페이지가 접근되지 않으면 읽었다고 말하지 않는다. 접근 가능한 다른 자료와 저장소 문서만으로 계속할 수 있는 작업은 계속하고, 결정이 필요한 범위를 기록한다.

## 제품 불변 조건

- 시스템 인증 완료와 실제 작업 시작 자기보고는 별도로 저장하고 표시한다.
- 인증 완료를 실제 작업 시작의 증거로 취급하지 않는다.
- 미응답을 미시작으로 계산하지 않는다.
- 알림 발송은 수신·열람의 증거가 아니다.
- 기술 실패·취소·철회는 인증 미완료와 분리한다.
- 재알림·화면 재진입은 인증 시간이나 세션 만료를 연장하지 않는다.
- 마감 전 서버에 정상 접수된 유효 사진은 마감 후에도 판정 결과를 기다린다.
- 사진 인증 횟수 제한을 사용자 정책으로 추가하지 않는다.
- 설문 미응답이 다음 약속 생성을 차단하지 않게 한다.
- 서버 시간을 시간·상태 판정의 최종 기준으로 사용한다.
- 중복 요청, 동시 수정, 지연된 AI 결과, 철회 후 결과가 최종 상태를 잘못 덮어쓰지 않게 한다.
- 민감한 사진·참가자 접근정보·설문 원문을 로그·예외·테스트 출력에 남기지 않는다.
- 과거 v2.3.1/v2.4.1의 포인트·등급·streak·배지·랭킹 기능을 되살리지 않는다.

## 팀 역할과 Jira 기준

Jira 프로젝트는 SCRUM, 보드는 1, 활성 Sprint는 SCRUM Sprint 3(ID 3)이다. 현재 Jira 사이트는 반드시 https://dodu-mobile.atlassian.net 을 사용한다. dodu-app.atlassian.net은 폐기된 잘못된 연결이므로 사용하지 않는다.

Jira MCP를 사용할 때는 mcp__codex_apps__atlassian_* 경로와 현재 사이트의 cloud ID를 사용한다. 현재 확인된 cloud ID는 66527eb6-6f88-4f8d-9920-1ea52608f213이다. 다른 Atlassian 연결이 dodu-app을 반환하면 쓰기 작업을 중단하고 로컬 문서에 차단 사유를 기록한다.

역할별 기본 담당자는 다음과 같다.

| 역할 | 담당자 | Jira 표시명 |
|---|---|---|
| PO·기획·에픽·정책 승인 조정 | 방수인 | 방수인 |
| PD·디자인·Figma 화면 | 김성준 | 김성준 |
| AI·사진 모델·학습·모델 QA | 김경제 | 김경제 |
| BE 정책·데이터·알림·보관 | 문은서 | 문은서 |
| BE 핵심 구현·통합·사진 인증 연결 | 강민 | mono |
| FE·PWA·접근성·화면 구현 | Elle Yoo | Elle Yoo |

미할당 티켓은 제목·본문·선행 작업의 역할을 확인해 위 기준으로 배정한다. 에픽은 PO, 디자인은 PD, AI 모델 업무는 AI 담당자에게 배정한다. 이미 지정된 담당자를 근거 없이 바꾸지 않는다.

Sprint 3에 편입할지는 현재 구현·검증에 직접 필요한지, Sprint 3 목표와 연결되는지 확인한 뒤 결정한다. 샘플·연동 시험용 티켓은 제품 작업으로 임의 배정하거나 Sprint에 넣지 않는다.

## 브랜치와 커밋

- 기본 개발·통합 브랜치는 dev.
- 배포 브랜치는 main.
- 일반 작업은 최신 dev에서 작업 브랜치를 만들고 PR 대상은 dev로 한다.
- 배포 PR만 main을 대상으로 한다.
- 작업 브랜치명은 feat/<JIRA-KEY>-<topic>, fix/<JIRA-KEY>-<topic>, docs/<JIRA-KEY>-<topic>, chore/<JIRA-KEY>-<topic> 형식을 우선한다.
- 하나의 커밋은 하나의 검증 가능한 목적을 갖는다.
- 커밋 전 루트 Decisions.md에 배경·선택·근거·대안·영향·검증을 기록하고 같은 커밋에 포함한다.
- Co-authored-by 등 공동 작성자 표기를 추가하지 않는다.
- .tools/, node_modules/, target/, dist/, 로그·비밀값·캐시는 커밋하지 않는다.
- 사용자 기존 변경은 보존하고, 관련 없는 변경을 함께 커밋하지 않는다.

커밋 메시지는 Conventional Commits 형식을 사용한다.

    <type>(<scope>): <summary>

feat, fix, docs, refactor, test, chore, build, ci 중 하나를 사용하고 필요하면 Jira 키를 scope에 넣는다.

## 구현 원칙

- Controller에는 HTTP 입출력과 인증·권한 경계만 둔다. 정책·상태 전이는 서비스·도메인 계층에서 처리한다.
- DTO와 Entity를 분리한다. 민감한 Entity 필드를 API 응답에 직접 노출하지 않는다.
- 단순 DTO는 Java record를 우선한다.
- 시간 경계는 교체 가능한 Clock과 서버 시간으로 검증한다.
- 예약·인증·설문·알림 상태의 동시성은 DB 제약조건과 트랜잭션으로 보호한다.
- 중복 요청은 멱등성 키와 충돌 검증을 함께 고려한다.
- 지연된 비동기 AI 결과는 현재 attempt·소유권·철회 상태를 확인한 뒤 저장한다.
- 원시 사진 바이트·접근 코드·설문 원문을 로그에 남기지 않는다.
- 오류 응답은 400 입력 오류, 401 인증 없음, 403 권한 없음, 404 자원 없음, 409 상태 충돌을 일관되게 사용한다.
- 확정되지 않은 OPEN_DECISIONS를 구현 테스트의 정답으로 고정하지 않는다.
- 원문 docs/dodu/sources/는 변경하지 않는다. 정책 변경은 별도 결정 기록과 파생 문서에 반영한다.

## 저장소 구조와 명령

- 백엔드: backend/ — Spring Boot, Java, Maven Wrapper
- 프런트엔드: frontend/ — React, TypeScript, Vite
- DB: PostgreSQL, Flyway, JDBC
- Docker 설정: backend/docker-compose.yml

실제 버전은 backend/pom.xml과 각 package.json에서 확인한다. 확인하지 않은 라이브러리·버전을 사실처럼 작성하지 않는다.

대표 검증 명령:

    cd backend
    .\mvnw.cmd test
    .\mvnw.cmd package

    cd ..\frontend
    npm run build

    cd ..
    git diff --check
    npm run verify

Docker/Testcontainers를 사용하는 통합 테스트는 Docker Desktop 엔진과 WSL 상태를 먼저 확인한다. Docker가 실행되지 않으면 통합 테스트를 성공했다고 보고하지 않는다. 실행하지 않은 테스트를 성공으로 기록하지 않는다.

## 문서·검증 기록

구현이 완료된 범위는 즉시 다음을 갱신한다.

1. 관련 API·설계·운영 Markdown
2. docs/jira/IMPLEMENTATION_STATUS.md
3. 필요하면 범위별 docs/jira/SCRUM-<KEY>_STATUS.md
4. 루트 Decisions.md

검증 보고에는 변경 파일, 근거 문서와 절, 확인한 동작, 실행한 명령과 결과, 실행하지 못한 검증과 사유, 남은 OPEN 결정 ID를 포함한다.

## Jira 동기화 규칙

구현 범위마다 대응 Jira 티켓을 확인한다.

- 티켓이 없으면 SCRUM 프로젝트의 Sprint 3에 생성·배정한다.
- 완료 조건과 검증 결과를 모두 충족한 경우에만 Jira를 완료 처리한다.
- 코드 커밋과 Jira 상태 변경은 실제 범위를 일치시킨다.
- 담당자가 강민(mono)인 티켓만 강민의 작업 완료 범위로 완료 처리한다.
- 다른 담당자의 티켓은 구현하지 않았다는 이유로 완료 처리하지 않는다.
- Jira 연결·권한·사이트 오류로 생성, Sprint 배정, 담당자 지정, 상태 변경을 할 수 없으면 완료로 표시하지 않는다. 대신 로컬 Markdown에 티켓, 완료 범위, 검증 결과, 남은 작업, 차단 사유를 기록한다.
- Jira 코멘트에는 커밋 해시, 구현 범위, 검증 결과, 남은 차단 사항만 기록한다. 민감한 데이터는 포함하지 않는다.

## 현재 구현 기준

현재까지 저장소에서 확인된 주요 구현은 다음과 같다.

- 참가자 접근 자격 해시·코드 생성·세션 교체·POST /api/access/enter
- 약속 생성·조회·수정·취소의 서버 시간 및 KST 날짜 경계 검증
- 자기보고 대상 판정·조회·제출의 48시간 경계와 소유권 검증
- 사진 제출 메타데이터 저장과 auth_attempt 연결을 위한 Flyway V2
- 사진 판정 결과의 현재 attempt·소유권·철회 보호
- 마감 후 정상 접수 attempt의 내부 기술복구 정책
- 공식 종료 후 신규 개입과 접수 attempt 처리의 lifecycle gate

아직 구현·검증 완료로 간주하지 않는 범위:

- Docker 기반 전체 통합 테스트와 실제 PostgreSQL 마이그레이션 검증
- 알림 구독·T/T+10/T+20 스케줄러의 운영 연동
- 외부 사진 저장소·AI 판정 worker의 실제 연동
- 인증 이력 전체 API와 프런트엔드 화면 연결
- Figma 기준 전체 화면·로딩·빈 상태·오류 상태 보정
- 동시 수정·멱등성·지연 결과·철회 후 결과의 전체 통합 시나리오

이 목록은 현재 코드와 검증 결과를 다시 확인해 갱신한다. 정책 코드만 존재한다고 API·DB·운영 연동이 완료된 것으로 보고하지 않는다.

## 변경 원칙

- 기존 주석과 사용자 변경을 보존한다.
- 변경 범위는 최소화한다.
- 기존 코드를 삭제할 때는 기능상 필요한 경우에만 문법에 맞는 주석 또는 별도 결정 기록으로 이전 의도를 보존한다.
- 문서의 링크·명령·파일 구조가 실제 저장소와 일치하는지 확인한다.
- 작업이 오래 걸리더라도 사용자에게 중간 보고를 생략하라는 요청이 있으면, 반드시 필요한 질문·권한·외부 차단만 문의하고 최종 결과를 일괄 보고한다.
