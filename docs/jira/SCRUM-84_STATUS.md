# SCRUM-84 구현 기록

- Jira: [SCRUM-84](https://dodu-mobile.atlassian.net/browse/SCRUM-84)
- 담당: mono(강민)
- 상태: 부분 구현. 통합 검증 전.

이번 증분은 `POST /api/promises/{promiseId}/photo-attempts/{attemptId}/result`를 추가했다. 활성 세션·약속 소유권·철회 상태·현재 `Evaluating` 상태를 같은 UPDATE 조건에서 확인하고, 성공·거절·기술오류를 서로 다른 최종 상태로 저장한다. 지연·역순 결과는 현재 상태 조건에서 거절한다.
