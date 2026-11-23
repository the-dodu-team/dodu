# AI worker 연결 계약

## 2026-11-23 제출 사진 재조회 제거·AI 서버 제약(SCRUM-85)

- 즉시 판정 경로는 사진 제출 요청이 방금 저장소에 올린 사진을 메모리에서 그대로 AI에 보낸다(저장소 재조회 없음). 30초 워커·재시도는 이전처럼 저장소에서 읽는다. 대기열에 싣는 사진 상한 `DODU_PHOTO_EVAL_INLINE_PHOTO_MAX_BYTES`(기본 2MB, 넘으면 저장소 조회).
- AI 담당 공유 제약(2026-11-23): 요청 본문 4MB 이하(플랫폼 4.5MB, 4.3MB에서 413 실측), 긴 변 1,600px 권장, 서버 미국 동부(iad1), 단건 1~2초(서버 처리 약 0.5초), 5분 이상 유휴 후 첫 요청 약 8초, 8건 동시는 순서대로 처리(가장 느린 응답 약 6초), 20장 배치 약 10초, 타임아웃 30초 이상 권장.
- 현재 불일치(후속): AI 호출 타임아웃 20초, FE 압축 상한 5MB, 백엔드 수신 상한 6MB. 4MB를 넘는 사진은 413 → 기술 실패로 끝난다.

## 2026-11-22 사진 제출 직후 판정 시작(SCRUM-85)

- 이전: AI 호출은 30초 주기 복구 워커에서만 일어났다. 제출 시점에 따라 판정 시작까지 0~30초를 기다렸다(Render 측정 평균 16초, p95 31초).
- 이제: 사진 제출 트랜잭션이 커밋되면 `PhotoEvaluationImmediateDispatcher`가 전용 실행기(기본 스레드 2·대기열 20)로 그 작업을 바로 처리한다. 롤백된 제출은 처리하지 않는다.
- 같은 작업을 두 번 판정하지 않도록 `READY → PROCESSING` 조건부 UPDATE로 한쪽만 차지한다. AI 호출은 트랜잭션 밖이며, 결과는 차지한 회차(`attempt_count`)가 그대로일 때만 저장한다(점유 60초가 지나 다른 쪽이 다시 잡았다면 늦은 결과는 버림).
- 실행기가 가득 찼거나, 서버 재시작·처리 실패가 있으면 작업이 DB에 남아 30초 워커가 처리한다. 재시도 간격(30초·30초·2분)은 즉시 경로도 건너뛰지 않는다.
- 설정: `DODU_PHOTO_EVAL_IMMEDIATE_ENABLED`(기본 true, false면 이전처럼 워커만), `DODU_PHOTO_EVAL_THREADS`(2), `DODU_PHOTO_EVAL_QUEUE_CAPACITY`(20).
- 판정 시각(`decided_at`)은 이전처럼 처리 시작(차지) 시각이다.

## 2026-11-18 약속 도구 비교 추가

- 이전에는 `laptop`·`book`이면 약속 도구와 관계없이 `Completed`였다. 책 약속에 노트북 화면 사진도 인증 완료됐다(검증 시트 AI 책 거절·비대상 판정 실패).
- 이제 worker가 돌려준 label을 약속 도구와 비교한다(`PhotoToolMatchPolicy`). 대응: `COMPUTER`(과거 값 `laptop` 포함) ↔ `laptop`, `BOOK` ↔ `book`.
- 결과 API는 기본으로 닫혀 있다(`DODU_PHOTO_RESULT_API_ENABLED=false`). 참가자 세션 인증이라 운영에서 켜지 않으며, 다시 쓰려면 worker 전용 인증으로 바꾼 뒤 켠다.
- 결과 API(`POST .../photo-attempts/{id}/result`)를 켠 경우 `COMPLETED`를 넣을 때는 `detectedLabel`이 필수다. 없으면 400 `DETECTED_LABEL_REQUIRED`, 다르면 `Rejected`/`AI_TOOL_MISMATCH`, 약속 도구 값을 알 수 없으면 `TechFailed`/`PROMISE_TOOL_UNKNOWN`으로 저장한다.
- label 없는 완료 결과는 판단할 수 없어 재시도(`AI_LABEL_MISSING`)한다. 약속 도구 값을 알 수 없으면 기술 실패(`PROMISE_TOOL_UNKNOWN`)다.
- 남은 범위: 태블릿·전자책을 `laptop`으로 분류하는 모델 문제는 서버 비교로 막을 수 없다(컴퓨터 약속에서는 여전히 승인). AI 담당 확인 필요.

## 2026-11-12 시작 오류 수정

- 후속 원인: 웹 JSON 자동설정과 별개로 AI 파서는 Jackson 2 `ObjectMapper`를 사용한다. 운영에서 해당 빈이 없으므로 필수 주입으로 두면 시작에 실패한다.
- 후속 수정: 등록된 Jackson 2 빈이 있으면 사용하고, 없으면 AI 파서 전용 인스턴스를 생성한다. 웹 응답용 JSON 설정은 변경하지 않는다. 테스트에서 임의 빈을 등록하던 조건을 제거하고 실제 애플리케이션·PostgreSQL 시작 검증을 추가한다.
- 후속 검증 결과: Docker PostgreSQL 17·Flyway를 포함한 AI 활성화 애플리케이션 시작 및 관련 테스트 총 8건 통과. 외부 AI 서버 호출 성공을 의미하지 않는다.

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
| `laptop` + 컴퓨터 약속(`COMPUTER`) | `Completed` |
| `book` + 책 약속(`BOOK`) | `Completed` |
| `laptop`·`book`이 약속 도구와 다름 | `Rejected` / `AI_TOOL_MISMATCH` (T+30 전이면 재제출 가능) |
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
