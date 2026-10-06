# CodeRabbit PR 리뷰

2026-09-22 사용자 요청에 따라 the-dodu-team/dodu를 Public으로 전환했습니다.
공개 전 로컬에서 조회 가능한 전체 13개 커밋을 주요 개인키·토큰 패턴으로 검사했고 일치 항목은 없었습니다.
이 검사는 모든 종류의 민감정보 부재를 보장하는 전수 보안 감사는 아닙니다.

CodeRabbit 설정은 루트 `.coderabbit.yaml`에 있습니다. 한국어 리뷰, dev와 main 대상 PR,
Draft 제외, 프로젝트 정책과 OPEN 결정 존중을 설정합니다. 사람의 리뷰와 CI를 대체하지 않습니다.

## 설치 및 확인

1. [GitHub App 설치](https://github.com/apps/coderabbitai/installations/new)에서 the-dodu-team을 선택합니다.
2. Only select repositories에서 dodu만 선택하고 Install을 누릅니다.
3. CodeRabbit 로그인 또는 저장소 활성화를 요청하면 GitHub 계정으로 진행합니다.
4. 공개 저장소 무료 리뷰를 사용하고 유료 구독·사용량 기반 과금은 활성화하지 않습니다.
5. 설정 PR 병합 후 새 PR에서 CodeRabbit 리뷰가 나타나는지 확인합니다.

현재 앱 설치와 실제 리뷰 실행은 확인 전입니다. 설정 파일 추가만으로 앱 설치가 완료되지 않습니다.
공개 저장소 무료 리뷰 근거: [공식 요금표](https://www.coderabbit.ai/pricing).
비공개로 되돌리면 무료 대상 여부를 다시 확인해야 하며, 기존 공개 복제본은 회수되지 않습니다.

되돌리려면 GitHub 조직의 Installed GitHub Apps에서 dodu 접근을 해제합니다.
자동 리뷰만 중단하려면 reviews.auto_review.enabled를 false로 변경합니다.
