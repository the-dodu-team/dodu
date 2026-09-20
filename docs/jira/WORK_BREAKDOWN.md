# 다음 작업과 팀 확인 단위

작성: 2026-09-20. **분할 초안이며 Jira에 생성하지 않았습니다.**
기준 티켓은 2026-09-16에 등록·검증한 [기존 명세](TICKETS.md)입니다.
현재 Jira는 인증되지 않아 최신 담당·상태·하위 작업을 재조회하지 못했습니다.
Notion도 401로 최신 본문·하위 문서를 확인하지 못했습니다. 등록 전에 반드시 최신 정보와 중복을 확인합니다.

## 먼저 진행할 작업

1. [PR #1](https://github.com/the-dodu-team/dodu/pull/1)의 브랜치 운영 문서를 리뷰합니다. 9월 20일 조회 시 OPEN이며 병합되지 않았습니다.
2. Elle Yoo와 문은서는 각자 개발환경을 재현하고, mono는 과금 차단 확인 및 CI 준비를 진행합니다.
3. BE는 API 계약, FE는 화면 기반을 병행합니다. mono는 두 작업을 리뷰하고 이후 공통 API 호출 예제를 제공합니다.
4. API 계약 검토 후 mock 연결과 FE 인수를 진행합니다. DB·권한 설계·소개/작성 화면은 다음 묶음으로 진행합니다.

기존 Java/React 환경과 저장소 생성 작업을 새 미완료 작업으로 반복 등록하지 않습니다.
현재 Dodu 빌드·테스트 CI는 없으며 기존 Auto Assign/Proof HTML은 이를 대신하지 않습니다.
GitHub 기본 브랜치는 dev입니다. 작업 PR → dev, 배포 PR → main을 적용합니다.
Ruleset은 요금제 제한으로 API 생성에 실패했으므로 보호가 적용되었다고 가정하지 않습니다.

## 바로 검토할 하위 작업 12개

목표는 각 작업에서 독립적으로 확인 가능한 결과물을 만드는 것입니다. 작은 작업 하나를 PR 하나로 검토하되,
문서·환경 재현 작업은 결과 기록 PR로 확인합니다. 0.5~1일 전후는 계획상 크기이며 일정 약속이 아닙니다.

| 로컬 초안 ID | 기존 상위 티켓 | 담당 | 작업 | 팀이 볼 결과물 | 예상 인일 |
|---|---|---|---|---|---:|
| SPLIT-15-1 | [DODU-15](https://dodu-app.atlassian.net/browse/DODU-15) | Elle Yoo | FE 개발환경 재현과 시작 안내 보완 | 환경 재현 기록과 필요한 README 수정 | 0.5 |
| SPLIT-15-2 | [DODU-15](https://dodu-app.atlassian.net/browse/DODU-15) | 문은서 | BE 개발환경 재현과 통합 JAR 확인 | 백엔드·통합 JAR 재현 기록 | 0.5 |
| SPLIT-15-3 | [DODU-15](https://dodu-app.atlassian.net/browse/DODU-15) | mono | 무료 사용 조건을 확인한 Dodu CI 구성 | CI 변경 PR·성공/실패 실행 링크·무료 사용 확인 기록 | 1 |
| SPLIT-15-4 | [DODU-15](https://dodu-app.atlassian.net/browse/DODU-15) | mono | 작업별 팀 확인 절차와 결과 공유 양식 정리 | 팀 확인 절차 문서와 리뷰 기록 | 0.5 |
| SPLIT-16-1 | [DODU-16](https://dodu-app.atlassian.net/browse/DODU-16) | 문은서 | 공통 API 응답·오류 계약 초안 | API 계약 문서 PR | 0.5 |
| SPLIT-16-2 | [DODU-16](https://dodu-app.atlassian.net/browse/DODU-16) | 문은서 | FE 검토용 응답 fixture 작성 | fixture 파일과 FE 확인 기록 | 0.5 |
| SPLIT-16-3 | [DODU-16](https://dodu-app.atlassian.net/browse/DODU-16) | 문은서 | 시간 표현·Clock 기술 계약 정리 | 시간 계약과 변환 예시 | 0.5 |
| SPLIT-17-1 | [DODU-17](https://dodu-app.atlassian.net/browse/DODU-17) | Elle Yoo | 공통 화면 틀과 반응형 배치 | 화면 변경 PR·두 화면 폭의 확인 자료 | 0.5 |
| SPLIT-17-2 | [DODU-17](https://dodu-app.atlassian.net/browse/DODU-17) | Elle Yoo | 버튼·입력 컴포넌트와 키보드 접근 | 컴포넌트 PR·키보드 확인 절차 | 0.75 |
| SPLIT-17-3 | [DODU-17](https://dodu-app.atlassian.net/browse/DODU-17) | Elle Yoo | 로딩·빈 결과·오류 안내 예제 | 상태별 화면 PR·재현 절차 | 0.75 |
| SPLIT-18-1 | [DODU-18](https://dodu-app.atlassian.net/browse/DODU-18) | mono | 공통 API 호출과 오류·취소 처리 | API 호출 기반 PR·검증 결과 | 0.75 |
| SPLIT-18-2 | [DODU-18](https://dodu-app.atlassian.net/browse/DODU-18) | mono | 개발 fixture 연결 예제와 FE 인수 | fixture 사용 예제 PR·FE 인수 기록 | 0.75 |

상세 범위·완료 기준·선행 작업·D-ID·기존 담당자 accountId는 [work-breakdown.json](work-breakdown.json)에 있습니다.
새 Jira 번호를 임의로 만들지 않았습니다. 기존 상위 담당자를 일괄 변경하지 않으며, 하위 작업별 담당을 지정하는 안입니다.
부모와 자식의 예상 시간을 합산하지 않습니다. 이 분할안은 기존 예상의 세부화이며 최신 상태 확인 후 재추정합니다.

- mono: CI·리뷰 절차·API 호출·fixture 인수. 기술 리뷰는 문은서.
- 문은서: 환경 재현·응답/오류 계약·fixture·시간 계약. 기술 리뷰는 mono.
- Elle Yoo(유연정): 환경 재현·화면 틀·입력 컴포넌트·상태 안내. mono가 예시와 리뷰 지원.
- 김경제·방수인·김성준: 개발/기획/디자인/QA 역할과 가용 시간을 아직 확인하지 못해 업무를 임의 배정하지 않습니다.
- GitHub Write 권한과 Jira 프로젝트 접근 권한은 별도입니다. 추가 인원은 Jira 사용자·할당 가능 여부도 확인해야 합니다.

개인 구현 작업은 한 번에 1개를 권장합니다. 리뷰는 별도로 시간을 확보하며 전체 팀 전원의 승인을 기다리지는 않습니다.
기술 검토자 1명과 필요한 인수 담당자의 확인을 받습니다. 제품 정책 승인은 별도 권한자의 기록이 필요합니다.

## 다음 묶음의 분할 후보

아래는 신규 티켓으로 아직 등록하지 않습니다. 앞 묶음의 결과와 최신 Jira를 확인한 뒤 실제 필요한 범위만 나눕니다.

| 기존 티켓 | 분할 후보 | 담당 기준 |
|---|---|---|
| DODU-19 | DB·마이그레이션 기술 선택 기록 / 최소 연결·마이그레이션·재실행 확인 | 문은서 |
| DODU-20 | 서비스·권한 목적 안내 / 보관·철회·삭제 안내 | Elle Yoo |
| DODU-21 | 식별·재발급·타인 접근 차단 설계 / 철회·삭제·접근 종료 설계 | 문은서 |
| DODU-22 | 입력·수정 프로토타입 / 확인 화면·미저장 안내 | Elle Yoo |
| DODU-23 | 알림 지원 환경 조사 / 촬영·업로드 제약 확인 / AI 입출력·비용·QA 후보 비교 | mono |
| DODU-24 | Clock·KST 변환 테스트 기반 / 상태·동시성·로그 시나리오 문서 | 문은서 |
| DODU-25 | 사용성·접근성 점검 / UT 과제·관찰 기록 양식 | Elle Yoo, 디자인·QA 역할 확인 후 협업 |
| DODU-26 | 통합 JAR·화면/API 인수 / 결과·차단 사항·다음 작업 기록 | mono |

DODU-27~30은 결정 검토 자료이며 완료되어도 D01~D13이 자동 승인되지 않습니다.
DODU-31~37은 실제 승인 기록이 필요한 후속 구현입니다. 개발 가능한 새 티켓으로 미리 풀지 않습니다.
새 담당자가 제품 책임자인지 확인되기 전 승인 권한을 부여하지 않습니다.

## 작업 하나가 끝날 때의 팀 확인 절차

1. 담당자는 PR에 Jira 키, 결과물, 테스트 결과, 재현 방법, 관련 Decisions.md 기록 ID를 남깁니다.
2. Jira에 PR/결과물 링크를 연결하고 확인 요청 상태를 표시합니다. 실제 Review 상태가 없다면 현재 진행 상태를 유지하고
   `review-needed` 라벨을 사용하는 안을 먼저 팀과 합의합니다. 새 상태나 자동화를 임의 생성하지 않습니다.
3. 지정 검토자는 [결과 확인 양식](templates/WORK_REVIEW.md)을 보고 실제 커밋을 재현하거나 문서를 검토합니다.
4. 필수 수정이 있으면 담당자가 수정하고 재확인을 받습니다. 구현 완료만으로 Done 처리하지 않습니다.
5. 코드/문서 PR이 dev에 병합되고 완료 기준·인수 확인이 충족된 뒤 Jira를 완료 처리합니다.
   PR이 필요 없는 조사 자료는 공유 가능한 최종 결과와 검토 기록으로 대체합니다.
6. 부모 티켓은 필요한 하위 작업과 원래 완료 기준을 모두 충족할 때만 완료합니다.

최종 확인 기록에는 확인자·일시·검토 커밋·판정·남은 문제를 남깁니다.
진행 보고 댓글이나 메시지를 자동 발송하는 기능은 아직 없습니다.
무료 비공개 저장소에서는 이 절차가 서버의 강제 보호를 대신하지 못하므로 팀이 수동으로 지킵니다.

## 무료 도구 선택

2026-09-20 공식 문서 확인 기준입니다. 현재 설치·자동 실행·유료 구독을 완료한 상태가 아닙니다.

| 도구 | 확인한 무료 범위 | 이번 제안 |
|---|---|---|
| GitHub Actions | Free 조직의 비공개 저장소에 월 2,000분·artifact 500MB, 한도 초과는 과금 가능 | CI 우선 후보. 조직의 초과 사용 차단 확인 후 표준 Linux runner로 구성 |
| CodeRabbit | Free는 비공개 저장소 PR 요약 중심. IDE/CLI 검토는 별도 무료 한도, 신규 조직은 14일 trial | 지속적인 무료 PR 코드 리뷰로 간주하지 않음. 설치·trial 시작은 보류 |
| Jenkins | 오픈소스 소프트웨어. 별도 실행 호스트와 운영 필요 | 기존 운영 서버·관리 담당이 없으므로 지금은 보류 |

Actions의 무료 한도는 조직 전체 사용과 함께 확인합니다. 타임아웃·동시 실행 취소만으로 0원 사용을 보장하지 않습니다.
과금 차단을 확인하지 못하면 자동 CI 활성화를 보류하고 로컬 verify 결과를 PR에 첨부합니다.
유료 runner·유료 AI API·외부 호스팅·결제수단 등록은 이번 작업에 포함하지 않습니다.

근거:
- [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [GitHub budgets](https://docs.github.com/en/billing/concepts/budgets-and-alerts)
- [CodeRabbit plans](https://docs.coderabbit.ai/management/plans)
- [Jenkins governance](https://www.jenkins.io/project/governance/)
- [Jenkins 설치·운영](https://www.jenkins.io/doc/book/installing/)
- [PRD v2.5](../dodu/sources/PRD_v2.5.md) §19~21
- [서비스 운영 정책](../dodu/sources/SERVICE_POLICY_v2.5.md) §0
- [미결정 사항](../dodu/OPEN_DECISIONS.md), [수용 기준](../dodu/ACCEPTANCE_CHECKLIST.md)

## Jira 반영 전 확인

- 기존 부모·하위 작업·담당·상태를 재조회하고 완료된 범위를 제외합니다.
- 프로젝트에서 Subtask 유형과 부모 설정을 지원하는지 확인합니다. 지원하지 않으면 임의로 에픽 하위에 복제하지 말고 연결 Task 방식으로 재설계합니다.
- 분할 ID로 중복 검사 후 생성하며 실제 키를 JSON에 기록합니다.
- 기존 선행 의존성을 보존하고 하위 작업 간 순환이 없는지 확인합니다.
- 날짜·Sprint·Story Point·제품 승인 상태는 임의 지정하지 않습니다.
- 생성 결과와 담당자·부모·링크를 재조회한 뒤에만 등록 완료로 표시합니다.

