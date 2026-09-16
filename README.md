# Dodu

스스로 정한 개인작업의 시작 약속을 실제 행동으로 연결하도록 돕는 웹앱/PWA 프로젝트입니다.
Dodu v2.5 Baseline을 바탕으로 시작 시점 개입과 작업환경 인증 행동의 연결 가능성을 검증합니다.
**사진 인증 완료는 실제 작업 시작의 증거가 아닙니다.** 시스템 인증과 자기보고를 분리합니다.

## 현재 상태

현재는 Sprint 1 개발환경과 연결 확인 화면까지 준비되어 있습니다.

| 준비된 기능 | 내용 |
|---|---|
| 개발 실행 | 한 명령으로 Java·React 개발 서버 실행 및 종료 |
| 연결 확인 | `GET /api/health` 및 준비 화면의 API 연결 확인 |
| 통합 검증 | 프런트엔드 lint·TypeScript 검사·빌드, Java 테스트·패키징 |
| 통합 빌드 | React 정적 파일을 Spring Boot JAR에 포함 |

참가자 식별, DB, 약속 생성·수정·취소, 알림, 사진 인증·AI 판정, 인증 이력,
자기보고, 데이터 수집·삭제와 PWA 설치·오프라인·푸시는 아직 구현하지 않았습니다.
실서비스 배포 및 지원 기기·브라우저 QA도 완료하지 않았습니다.

## 기술 구성

| 구분 | 구성 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1.1, Maven Wrapper |
| 프런트엔드 | React 19, TypeScript 6, Vite 8, Oxlint |
| 저장소 관리 | npm workspace (`frontend`) 및 루트 lockfile |
| 개발 프로세스 | concurrently |

정확한 Node 의존성은 [package-lock.json](package-lock.json), Java 의존성은
[backend/pom.xml](backend/pom.xml)을 따릅니다. DB·알림 제공자·AI 모델·배포 환경은 미선정입니다.

## 사전 준비

- Node.js **22.12 이상, 23 미만** 및 npm. 구성 검증에 사용한 버전은 Node 22.19.0 / npm 10.9.3입니다.
- JDK **21**.
- Windows에서는 PowerShell이 필요합니다. 현재 실행 검증 환경은 Windows입니다.
- 최초 설치·빌드에는 npm 및 Maven 의존성을 다운로드할 네트워크 연결이 필요합니다.

### JDK 설정

설치한 JDK 21의 루트 경로를 `JAVA_HOME`으로 설정합니다. `bin` 폴더를 지정하지 않습니다.
루트 실행 명령은 `JAVA_HOME`을 우선 사용하며, 없으면 `.tools/jdk` 내부 JDK를 찾습니다.

전역 Java 설치를 사용하지 않으려면 [Microsoft 공식 JDK 21 Windows x64 ZIP](https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.zip)을
프로젝트의 `.tools/jdk`에 해제합니다. 다음 구조여야 합니다.

```text
.tools/jdk/<JDK 폴더>/bin/java.exe
```

`.tools`는 Git에 포함되지 않으므로 새로 받은 프로젝트에서도 JDK를 별도로 준비해야 합니다.
Maven은 `backend/mvnw` 또는 `backend/mvnw.cmd`를 사용하므로 전역 Maven 설치는 필요하지 않습니다.
루트 명령은 프로젝트 내부 `.tools/m2`를 Maven 의존성 캐시로 사용합니다.
Maven Wrapper 자체 다운로드 캐시는 사용자 홈에도 생성될 수 있습니다.

## 빠른 시작

프로젝트 루트에서 실행합니다.

```powershell
npm ci
npm run dev
```

- 화면: http://127.0.0.1:5173
- API: http://127.0.0.1:8080/api/health
- 화면의 **백엔드 연결 확인** 버튼으로 Vite 프록시를 통한 API 연결을 확인합니다.

`Ctrl+C`로 두 서버를 함께 종료합니다. 한쪽 프로세스가 종료되면 다른 쪽도 종료합니다.
8080 또는 5173 포트가 이미 사용 중이면 시작 전에 오류를 표시합니다.
React 변경은 Vite의 화면 갱신을 사용합니다. Java 변경 후에는 개발 명령을 재시작합니다.

## 명령어

| 명령 | 동작 |
|---|---|
| `npm ci` | 루트 lockfile 기준 의존성 설치 |
| `npm run dev` | 백엔드·프런트엔드 동시 실행 |
| `npm run dev:backend` | 백엔드만 실행 |
| `npm run dev:frontend` | 프런트엔드만 실행 |
| `npm run verify` | lint → TypeScript/React 빌드 → Maven clean verify |
| `npm run build` | TypeScript/React 빌드 → Maven clean package 및 Java 테스트 |
| `npm start` | 이미 빌드한 통합 JAR 실행 |

검증·빌드는 한 단계가 실패하면 이후 단계로 진행하지 않습니다.
`npm run build`에는 lint가 포함되지 않으므로 변경 검증에는 `npm run verify`를 사용합니다.
자동 CI는 아직 구성하지 않았습니다.

## 단일 JAR 실행

실행 중인 통합 JAR을 종료한 뒤 빌드합니다.

```powershell
npm run build
npm start
```

산출물: `backend/target/dodu-backend-0.0.1-SNAPSHOT.jar`.
React 파일은 JAR의 `static` 리소스에 포함됩니다. 화면과 API 모두 http://127.0.0.1:8080에서 제공하며
Vite 서버는 필요하지 않습니다. 변경을 반영하려면 다시 빌드합니다.
루트 build/verify는 React를 먼저 빌드한 뒤 Maven `bundle-frontend` 프로필을 적용합니다.
Maven 프로필만 직접 실행하면 최신 React 빌드를 보장하지 않습니다.

`npm start`는 로컬 실행 편의를 위한 명령입니다. 배포 대상에서는 JDK 21의 `java -jar <JAR 경로>`로
실행할 수 있습니다. 실제 배포·TLS·도메인·운영 설정은 후속 작업입니다.
루트 개발·통합 실행은 `127.0.0.1`에 바인딩합니다.
빌드는 `backend/target`을 정리하므로 해당 JAR이 실행 중이면 먼저 종료합니다.

## 디렉터리

```text
.
├── backend/                 # Java 앱, 테스트, Maven Wrapper
├── frontend/                # React 앱, Vite·TypeScript 설정
├── scripts/                 # 통합 워크플로 및 개별 실행 스크립트
├── docs/
│   ├── DEVELOPMENT_CONVENTIONS.md
│   └── dodu/                # 정책·미결정 사항·원문·수용 기준
├── AGENTS.md                # 자동화 에이전트 작업 지침
├── README-CODEX.md          # 초기 v2.5 문서 패키지 안내
├── package.json             # 통합 명령 및 npm workspace
└── package-lock.json        # Node 의존성 기준
```

`.tools/`, `node_modules/`, `backend/target/`, `frontend/dist/`는 로컬 캐시·산출물이며 Git에서 제외합니다.
프런트엔드의 기존 Vite 예제 파일은 보존되어 있으며 현재 진입 화면은 `DoduHome.tsx`입니다.

## 개발 지침과 정책

작업 전 [개발 컨벤션](docs/DEVELOPMENT_CONVENTIONS.md)과 [AGENTS.md](AGENTS.md)를 읽습니다.
제품 기준은 다음 문서에서 확인합니다.

1. [구현 컨텍스트](docs/dodu/IMPLEMENTATION_CONTEXT.md): 원문 요약과 기술설계 제안.
2. [미결정 사항](docs/dodu/OPEN_DECISIONS.md): D01~D13, 현재 모두 OPEN.
3. [PRD v2.5](docs/dodu/sources/PRD_v2.5.md): 목적·범위·가설.
4. [서비스 운영 정책 v2.5](docs/dodu/sources/SERVICE_POLICY_v2.5.md): 시간·횟수·권한·보관 조건.
5. [Flow·로그 매핑 v2.5](docs/dodu/sources/FLOW_LOG_MAPPING_v2.5.md): 흐름·관찰 사실·지표와 이벤트 제안.
6. [수용 기준](docs/dodu/ACCEPTANCE_CHECKLIST.md): 기능 구현 후 검증할 시나리오.

OPEN 항목의 제안은 승인된 제품 정책이 아닙니다. 영향을 받는 기능은 보류하고 독립적인 작업을 진행합니다.
예약 날짜·시간 경계(D06/D11), 인증 비동기 처리(D04/D05/D08/D10), 설문(D01/D02/D03/D07/D12),
분석·지원 환경·QA(D09/D13)는 관련 결정을 확인한 뒤 진행합니다.
기술 스택 선택이나 개발 컨벤션 작성은 OPEN 제품 정책의 승인을 의미하지 않습니다.

[사용자 지정 Notion 페이지](https://app.notion.com/p/3d64c293890d80cc940bdc3cef7aa32c)와 하위 항목은
Dodu 작업 시작 시, 업데이트 통지를 받은 때 다시 확인합니다. 상시 자동 감시는 구성되지 않았습니다.
접근하지 못한 내용은 확인된 근거로 사용하지 않습니다.

## 문제 해결

| 증상 | 확인할 내용 |
|---|---|
| JDK 관련 오류 | `JAVA_HOME` 및 `.tools/jdk/<폴더>/bin/java.exe` 확인, JDK 21 사용 |
| 포트 사용 중 | 기존 8080·5173 서버 종료 후 재실행 |
| API 연결 실패 | 백엔드 시작 완료 여부 확인, `/api/health` 직접 요청 |
| 의존성 설치 실패 | Node 버전, 네트워크, 루트 lockfile 확인 후 `npm ci` |
| 통합 JAR 없음 | 먼저 `npm run build` 실행 |
| 통합 화면에 변경 미반영 | 서버 종료 → `npm run build` → `npm start` |

## 기여

커밋·브랜치·PR 규칙은 [개발 컨벤션](docs/DEVELOPMENT_CONVENTIONS.md#git과-pr)을 따릅니다.
매 커밋의 결정 배경과 근거는 [Decisions.md](Decisions.md)에 기록하여 함께 커밋합니다.
문서 변경은 링크·명령·정책 근거를 확인하고, 코드 변경은 영향 범위에 맞게 검증합니다.
앱 테스트가 통과해도 지원 기기 QA·AI QA나 제품 행동 효과 검증을 대신하지 않습니다.
