# AI worker 연결 계약

## 현재 확인된 worker

- 구현: DINOv2-base 기반 FastAPI worker
- 입력 분류: `laptop`, `book`, `other`
- 실제 확인: `/health` 200, `/predict` 단건 200, `/predict/batch` 3장 200
- 현재 worker 응답의 모델 식별값: `model: "dinov2-base-prototype"`

## 요청

- 메서드: `POST /predict/batch`
- multipart 필드: `files`
- Dodu 호출 단위: 사진 1장
- 인증 헤더: `X-API-Key`
- 비밀값: `DODU_AI_API_KEY` 환경변수만 사용하며 저장소·로그·문서에 기록하지 않는다.

## 응답 예시

```json
{
  "model": "dinov2-base-prototype",
  "image_count": 1,
  "processing_ms": 309.32,
  "results": [
    {"filename": "photo.jpg", "prediction": "laptop"}
  ]
}
```

## Dodu 상태 매핑

| worker 결과 | Dodu 결과 |
|---|---|
| `laptop` | `Completed` |
| `book` | `Completed` |
| `other` | `Rejected` / `AI_OTHER` |
| HTTP 408·429·5xx | 재시도 대상 |
| 기타 HTTP 오류 | 기술 실패 |
| 응답 형식 오류·지원하지 않는 label | 재시도 대상 |

모델 버전은 worker의 `model_version`을 우선하고, 현재 worker처럼 `model`로 반환하는 경우 해당 값을 저장한다.

## 남은 연결 조건

- 운영 Supabase bucket과 환경변수 등록
- 운영 AI endpoint와 환경변수 등록
- 운영 worker 장애·재시도·철회 후 결과 QA
- AI 담당자의 모델 교체·운영 장애 대응 확인
