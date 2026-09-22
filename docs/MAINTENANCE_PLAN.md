# 개발·운영 준비 순서

2026-09-22 조회 기준입니다. 아래 항목은 권장 후속 작업이며 설정 완료 기록이 아닙니다.
ERD 문서화는 이 작업들과 독립적으로 진행할 수 있습니다.

| 시점 | 작업 | 현재 상태 / 완료 기준 | 주도 제안 |
|---|---|---|---|
| 다음 개발 PR 전 | 실제 빌드 CI (DODU-40) | 기존 Auto Assign·Proof HTML만 있음. Node 22·JDK 21, npm ci 및 npm run verify를 깨끗한 runner에서 실행하고 실패가 PR에 표시되어야 함 | mono, 문은서 검토 |
| CI 확인 후 | dev/main 보호 | Ruleset 조회 결과 없음, dev 보호 조회 404. PR·리뷰 1명·대화 해결·강제 push/삭제 차단을 설정하고 실제 CI 이름을 필수 검사에 등록 | mono |
| CodeRabbit 설정 리뷰 후 | PR #4 병합과 설정 확인 | 앱 설치 및 실제 리뷰 완료 확인. 한국어 설정은 PR #4가 아직 OPEN이므로 기본 브랜치 미반영 | mono, 문은서 승인 |
| 주기적 유지보수 | 의존성·취약점 점검 | npm·Maven·Actions 업데이트 후보와 보안 알림을 주 1회 확인하고 작은 PR로 검증. 자동 병합은 하지 않음 | FE Elle Yoo / BE 문은서 / CI mono |
| DB 도입 시 | 마이그레이션·백업 복원 | DODU-19에서 저장 기술 결정, 신규 DB·기존 DB 적용과 복원 시나리오 검증 | 문은서 |
| 외부 알림·사진 도입 시 | 재시도·삭제 작업 관찰 | 실패율·지연·보관 초과를 관찰하고 개인정보 없는 오류 코드로 추적. 30일/90일 정책과 삭제 요청 검증 | mono + 문은서 |
| 실서비스 배포 전 | 배포·롤백·장애 대응 | 호스팅과 비밀값 저장, health 검사, 담당자·복구 절차·백업 보존 정책 확정 | mono + 문은서 |

Jenkins는 현재 추가하지 않는 것을 권장합니다. 별도 서버의 보안 패치·플러그인·백업·모니터링을 관리할
운영 부담이 생기고, 지금 필요한 PR 빌드 검증은 GitHub Actions로 구성할 수 있습니다.
Jenkins가 필요한 사내망·기존 운영 인프라·특수 빌드 요구가 생기면 다시 검토합니다.

공개 저장소의 표준 GitHub-hosted runner 실행은 무료입니다. larger runner와 저장 공간에는 별도 조건이 있으므로
초기 CI는 표준 Ubuntu runner, 실행 시간 제한, 중복 실행 취소, 최소 권한으로 구성하고 불필요한 artifact/cache 업로드를 피합니다.
유료 호스팅·대형 runner·추가 리뷰 제품은 이번 제안에 포함하지 않습니다.

근거: [GitHub Actions 요금](https://docs.github.com/en/billing/concepts/product-billing/github-actions),
[Jenkins 운영 관리](https://www.jenkins.io/doc/book/system-administration/).
