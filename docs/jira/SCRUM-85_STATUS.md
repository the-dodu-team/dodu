# SCRUM-85 구현 기록

- Jira: [SCRUM-85](https://dodu-mobile.atlassian.net/browse/SCRUM-85)
- 담당: mono(강민)
- 상태: 정책 증분 구현. 내부 retry/backoff 연결과 통합 검증 전.

`PhotoRecoveryPolicy`는 T+30 이후에도 T 이전에 정상 접수된 동일 attempt만 `Evaluating` 상태에서 내부 복구할 수 있도록 한다. 새 사진 제출이나 완료·거절·기술실패·철회 attempt의 재처리는 허용하지 않는다.
