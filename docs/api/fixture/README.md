# API 응답 fixture (초안)

- 상태: 초안. 계약 문서와 함께 강민님의 리뷰, Elle Yoo의 화면 관점 확인을 받기 전입니다.
- 작업: SCRUM-7 (이전 DODU-16), 분할 초안 SPLIT-16-2
- 기준: [공통 API 응답·오류 계약](../COMMON_API_CONTRACT.md) 3~5장

## 용도

FE가 서버 없이 정상·입력오류·기술오류 상태를 재현하고, 계약에 맞지 않는 응답을 성공으로 표시하지 않는지 확인하는 개발·테스트용 예시입니다.

## 가상 데이터 안내

이 폴더의 값은 모두 가상입니다. ID는 `00000000-0000-4000-8000-...` 형식의 가짜 UUID이고, 작업명과 시각도 예시입니다. 이 파일을 사용했다고 해서 실제 저장·알림·인증이 일어난 것이 아니며, 운영 화면에서 사용하지 않습니다.
예시에 쓴 `POST /api/promises`는 가칭이며 확정된 API 경로가 아닙니다.

## 파일

| 파일 | HTTP 상태 | 종류 | 계약 근거 |
|---|---:|---|---|
| `success.json` | 201 | 정상 (약속 생성 예시) | 3.1, 5.1 |
| `validation-error.json` | 400 | 입력오류 | 3.2, 3.3, 5.2 |
| `technical-error.json` | 503 | 기술오류 | 3.3, 5.3 |
| `invalid-responses.json` | 사례별 | 계약에 맞지 않는 응답 | 3~4장 |

HTTP 상태는 응답 본문에 들어 있지 않아서, mock을 만들 때 위 표의 값을 함께 설정합니다.

## 사용 규칙

- 정상·오류 fixture 파일은 응답 본문과 같은 내용입니다. 계약 5장의 예시와 일치해야 하며, 계약이 바뀌면 같은 PR에서 함께 고칩니다.
- `invalid-responses.json`의 `cases[].body`가 응답 본문이고 `httpStatus`가 상태 코드입니다. `violates`는 어떤 계약 규칙을 어긴 사례인지 설명합니다. 이 사례들은 성공으로 표시하면 안 됩니다.
- `non-json-gateway-response`는 본문이 JSON이 아닌 문자열(`bodyIsRawText: true`)입니다.
- 정상 응답의 `toolType`, `lifecycleStatus` 표기는 예시이며, DB 스키마와 상태 모델이 확정되면 맞춥니다.

## 포함하지 않은 것

- 경계·상태 전이 사례: D03~D11은 2026-09-27에 확정됐지만, 예약·제출 끝점 경계(정확히 +5분, 정확히 T, T+30 등)는 BE 도메인 테스트(`InterventionWindowPolicyTests`)가 검증하므로 FE용 fixture에는 넣지 않았습니다. 마감 직전 기술오류 복구, 지연된 판정 결과, 중복 요청 처리처럼 상태 전이표와 멱등 계약이 정해져야 하는 사례도 계약이 정해진 뒤 추가합니다. 입력오류 예시는 경계값이 아닌 값(요청 시각보다 2분 뒤)을 사용합니다.
- 401·403·404·409 응답: 대상 엔드포인트가 정해진 뒤 필요하면 추가합니다.

## 검증

JSON 파싱 확인 (PowerShell):

```powershell
Get-ChildItem docs/api/fixtures/*.json | ForEach-Object {
  $null = Get-Content $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
  "$($_.Name): OK"
}
```


## 변경 이력

| 날짜 | 변경 | 작성 |
|---|---|---|
| 2026-10-07 | 초안 작성 | 문은서 |
| 2026-10-07 | 확정 결정(D01~D13) 반영에 맞춰 "포함하지 않은 것" 설명 수정 | 문은서 |