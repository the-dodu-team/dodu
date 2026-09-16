# Dodu v2.5 Codex 전달 패키지

## 설치
1. ZIP을 해제한다.
2. `docs/dodu/`를 Dodu 저장소의 `docs/dodu/`로 복사한다.
3. `AGENTS.md`를 저장소 루트에 둔다. 기존 파일이 있으면 덮어쓰지 말고 Dodu 관련 지침을 병합한다. 기존 `docs/dodu` 파일도 동일하게 먼저 비교한다.
4. VS Code에서 해당 저장소를 열고 아래 요청을 Codex에 전달한다.

```text
AGENTS.md와 docs/dodu/IMPLEMENTATION_CONTEXT.md, OPEN_DECISIONS.md,
sources 안의 v2.5 원문 세 문서를 읽어주세요.
먼저 실제 저장소 구조·설치 버전·기존 구현을 조사하고,
확정 정책 / 현재 구현 / 미결정 사항을 구분해 차이와 Sprint 1 작업 순서를 정리해주세요.
OPEN_DECISIONS의 제안을 확정 정책으로 구현하지 마세요.
이번 요청은 문서와 저장소 분석까지이며, 코드 변경은 하지 마세요.
```

구현 작업을 지시할 때는 다음처럼 범위를 구체화한다.

```text
AGENTS.md와 관련 원문을 기준으로 [기능명]을 구현해주세요.
기존 주석과 코드 변경 규칙을 지키고, 미결정 정책과 무관한 부분부터 진행해주세요.
정책 결정이 필요한 부분은 결정 ID와 영향을 표시해주세요.
완료 후 실제 수행한 검증과 남은 한계를 알려주세요.
```

## 구성
| 파일 | 역할 |
|---|---|
| AGENTS.md | Codex 작업 지침과 문서 읽기 순서 |
| docs/dodu/IMPLEMENTATION_CONTEXT.md | 확정 정책 요약·기술설계 제안·역할별 작업 |
| docs/dodu/OPEN_DECISIONS.md | 승인되지 않은 쟁점 및 보완 제안 |
| docs/dodu/ACCEPTANCE_CHECKLIST.md | 구현 후 확인할 시나리오 |
| docs/dodu/sources/*.md | 사용자 첨부 원문 3개 |
| docs/dodu/SOURCE_MANIFEST.json | 원본 파일명·해시·복사 검증 정보 |

원문은 바이트 그대로 복사했다. 첨부에 포함된 바깥 코드 펜스도 보존되어 있으므로 Markdown 미리보기에서 전체가 코드처럼 보일 수 있으나 텍스트 읽기에는 문제가 없다. 원문 속 Mermaid는 수정하지 않았다.

이 패키지는 앱 코드·의존성·실행 환경을 포함하지 않는다. 실제 저장소나 AI·PWA 성능을 검증했다는 의미가 아니다. 분석 제안은 팀의 정책 승인과 구분된다.
