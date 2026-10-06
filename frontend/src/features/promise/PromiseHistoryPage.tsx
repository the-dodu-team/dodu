import Button from '../../components/Button'
import { formatStartTime } from './formatPromise'
import type { PreviewPromise } from './types'

type PromiseHistoryPageProps = {
  promise: PreviewPromise | null
  onBack: () => void
}

export default function PromiseHistoryPage({ promise, onBack }: PromiseHistoryPageProps) {
  return (
    <section className="promise-editor">
      <h1 className="promise-screen-title">약속 기록 미리보기</h1>
      <p className="promise-screen-description">
        이번에 입력한 약속만 표시합니다.
        서버에 저장된 이력이나 실제 인증 결과는 아닙니다.
      </p>

      {promise ? (
        <dl className="promise-summary">
          <dt>날짜</dt>
          <dd>{promise.date} · KST</dd>

          <dt>작업명</dt>
          <dd>{promise.taskName}</dd>

          <dt>예정 시각</dt>
          <dd>{formatStartTime(promise.startTime)} · KST</dd>

          <dt>인증 도구</dt>
          <dd>{promise.tool === 'computer' ? '컴퓨터' : '실제 책'}</dd>

          <dt>상태</dt>
          <dd>
            {promise.status === 'verified'
              ? '인증 완료 · 미리보기'
              : '약속 예정 · 미리보기'}
          </dd>
        </dl>
      ) : (
        <p className="promise-screen-description">
          아직 표시할 약속이 없어요.
        </p>
      )}

      <div className="promise-actions">
        <Button onClick={() => onBack()}>
          홈으로 돌아가기
        </Button>
      </div>
    </section>
  )
}
