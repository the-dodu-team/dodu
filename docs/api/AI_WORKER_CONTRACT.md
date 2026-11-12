# AI worker 연결 계약

## 2026-11-12 시작 오류 수정

- `DODU_AI_ENABLED=true`에서 운영 생성자에 `@Autowired`를 지정해 Spring이 테스트용 생성자와 구분하도록 한다. 기본 생성자를 추가하거나 기존 생성자를 제거하지 않는다.
- 회귀 검증: 활성화 시 ObjectMapper 주입·선택적 사진 저장소로 컨텍스트 시작, 비활성화 시 의존성 없이 시작. 기존 요청·판정 매핑 포함 7건 통과.
- 새 Vercel worker의 실제 사진 판정 및 Render 재배포 성공은 별도 확인 필요. 이 수정은 인증값·시간 제한·업로드 크기 설정을 변경하지 않는다.

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
