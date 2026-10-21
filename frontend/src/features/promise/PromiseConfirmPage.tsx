import arrowLeftIcon from '../../assets/icons/arrow-left.svg'
import clockIcon from '../../assets/icons/clock-filled-gray.svg'
import cameraIcon from '../../assets/icons/camera-filled-gray.svg'
import type { PromiseTool } from './types'

type PromiseConfirmPageProps = {
  taskName: string
  scheduledAt: string
  tool: PromiseTool
  isRecreated?: boolean
  errorMessage?: string
  onBack: () => void
  onSave: () => void
}

export default function PromiseConfirmPage({
  taskName,
  scheduledAt,
  tool,
  isRecreated = false,
  errorMessage = '',
  onBack,
  onSave,
}: PromiseConfirmPageProps) {
  const timestamp = Date.parse(scheduledAt)
  const hasValidTime = Number.isFinite(timestamp)

  const timeLabel = hasValidTime
    ? new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul',
        month: 'numeric',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      }).format(new Date(timestamp))
    : '날짜와 시간을 다시 선택해 주세요'

  return (
    <div className="promise-create-page">
      <header className="promise-create-header">
        <button
          type="button"
          className="promise-create-back"
          aria-label="인증 도구 선택으로 돌아가기"
          onClick={onBack}
        >
          <img src={arrowLeftIcon} alt="" width={24} height={24} />
        </button>

        <h1 id="promise-confirm-title">약속 만들기</h1>
      </header>

      <main
        className="promise-create-main"
        aria-labelledby="promise-confirm-title"
      >
        <div className="promise-confirm-layout">
          <div className="promise-create-content">
            <h2 className="promise-confirm-heading">약속 확인</h2>

            <div className="promise-confirm-card">
              <h3 className="promise-confirm-task">{taskName}</h3>

              <dl className="promise-confirm-details">
                <div>
                  <dt>
                    <img src={clockIcon} alt="" width={16} height={16} />
                    시작 시간
                  </dt>
                  <dd>{timeLabel}</dd>
                </div>

                <div>
                  <dt>
                    <img src={cameraIcon} alt="" width={16} height={16} />
                    인증 도구
                  </dt>
                  <dd>{tool === 'computer' ? '컴퓨터' : '책'}</dd>
                </div>
              </dl>
            </div>

            <div className="promise-confirm-guide">
              <p>
                {isRecreated
                  ? '취소 후 다시 만드는 약속은 시작 시간을 수정할 수 없어요.'
                  : '저장 후 시작 전까지 날짜·시간을 1번 수정할 수 있어요.'}
              </p>
              <p>약속 취소도 시작 전까지만 할 수 있어요.</p>
            </div>

            <p className="promise-confirm-error" role="alert">
              {errorMessage}
            </p>
          </div>

          <footer className="promise-create-footer">
            <div className="promise-confirm-actions">
              <button
                type="button"
                className="promise-confirm-previous"
                onClick={onBack}
              >
                이전
              </button>

              <button
                type="button"
                className="promise-create-next"
                disabled={!taskName.trim() || !hasValidTime}
                onClick={onSave}
              >
                약속 저장
              </button>
            </div>
          </footer>
        </div>
      </main>
    </div>
  )
}