import Button from '../../components/Button'
import { formatStartTime } from './formatPromise'
import type { PromiseDraft } from './types'

type PromiseConfirmPageProps = {
  draft: PromiseDraft
  onEdit: () => void
  onPreview: () => void
}

export default function PromiseConfirmPage({ draft, onEdit, onPreview }: PromiseConfirmPageProps) {
  return (
    <section className="promise-editor">
      <h1 className="promise-screen-title">약속 내용 확인</h1>

      <dl className="promise-summary">
        <dt>작업명</dt>
        <dd>{draft.taskName.trim()}</dd>

        <dt>예정 시각</dt>
        <dd>{formatStartTime(draft.startTime)} · KST</dd>

        <dt>인증 도구</dt>
        <dd>{draft.tool === 'computer' ? '컴퓨터' : '실제 책'}</dd>
      </dl>

      <p className="promise-screen-description">
        실제 예약은 생성되지 않습니다. 입력한 내용을 홈에서
        미리 볼 수 있습니다.
      </p>

      <div className="promise-actions">
        <Button onClick={() => onEdit()}>
          내용 수정
        </Button>
        <Button onClick={onPreview}>
          홈에 미리보기
        </Button>
      </div>
    </section>
  )
}
