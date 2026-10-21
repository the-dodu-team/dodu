import { useRef, useState } from 'react'
import arrowLeftIcon from '../../assets/icons/arrow-left.svg'
import clockIcon from '../../assets/icons/clock-filled-gray.svg'
import cameraIcon from '../../assets/icons/camera-filled-gray.svg'
import type { PreviewPromise } from './types'

type PromiseManagePageProps = {
  promise: PreviewPromise
  editBlockedReason: string
  cancelBlockedReason: string
  onBack: () => void
  onEditTime: () => void
  onCancelPromise: () => string | undefined
}

export default function PromiseManagePage({
  promise,
  editBlockedReason,
  cancelBlockedReason,
  onBack,
  onEditTime,
  onCancelPromise,
}: PromiseManagePageProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const keepButtonRef = useRef<HTMLButtonElement>(null)
  const cancelTriggerRef = useRef<HTMLButtonElement>(null)
  const [cancelError, setCancelError] = useState('')

  const timestamp = Date.parse(
    `${promise.date}T${promise.startTime}:00+09:00`,
  )

  const timeLabel = Number.isFinite(timestamp)
    ? new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul',
        month: 'numeric',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      }).format(new Date(timestamp))
    : '시작 시간을 확인해 주세요'

  function openCancelDialog() {
    if (cancelBlockedReason) return

    setCancelError('')
    dialogRef.current?.showModal()
    keepButtonRef.current?.focus()
  }

  function closeCancelDialog() {
    dialogRef.current?.close()
  }

  function confirmCancellation() {
    const error = onCancelPromise()

    if (error) {
      setCancelError(error)
    }
  }

  return (
    <div className="promise-create-page">
      <header className="promise-create-header">
        <button
          type="button"
          className="promise-create-back"
          aria-label="홈으로 돌아가기"
          onClick={onBack}
        >
          <img src={arrowLeftIcon} alt="" width={24} height={24} />
        </button>

        <h1 id="promise-manage-title">약속 관리</h1>
      </header>

      <main
        className="promise-create-main"
        aria-labelledby="promise-manage-title"
      >
        <div className="promise-confirm-layout">
          <div className="promise-create-content">
            <div className="promise-confirm-card">
              <h2 className="promise-confirm-task">{promise.taskName}</h2>

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
                  <dd>{promise.tool === 'computer' ? '컴퓨터' : '책'}</dd>
                </div>
              </dl>
            </div>

            <div className="promise-confirm-guide">
              <p id="promise-edit-guide" aria-live="polite">
                {editBlockedReason ||
                  '시작 전까지 날짜·시간을 1번 수정할 수 있어요.'}
              </p>

              <p id="promise-cancel-guide" aria-live="polite">
                {cancelBlockedReason ||
                  '약속 취소도 시작 전까지만 할 수 있어요.'}
              </p>
            </div>
          </div>

          <footer className="promise-create-footer">
            <div className="promise-manage-actions">
              <button
                type="button"
                className="promise-confirm-previous"
                disabled={Boolean(editBlockedReason)}
                aria-describedby="promise-edit-guide"
                onClick={onEditTime}
              >
                시작 시간 수정
              </button>

              <button
                ref={cancelTriggerRef}
                type="button"
                className="promise-manage-cancel"
                disabled={Boolean(cancelBlockedReason)}
                aria-describedby="promise-cancel-guide"
                aria-haspopup="dialog"
                onClick={openCancelDialog}
              >
                약속 취소
              </button>
            </div>
          </footer>
        </div>
      </main>

      <dialog
        ref={dialogRef}
        className="promise-cancel-dialog"
        aria-labelledby="promise-cancel-title"
        aria-describedby="promise-cancel-description"
        onClose={() => {
          setCancelError('')
          cancelTriggerRef.current?.focus()
        }}
      >
        <div className="promise-cancel-dialog-content">
          <h2 id="promise-cancel-title">약속을 취소할까요?</h2>

          <p id="promise-cancel-description">
            취소한 약속은 되돌릴 수 없어요.
          </p>

          {(cancelBlockedReason || cancelError) && (
            <p className="promise-cancel-dialog-error" role="alert">
              {cancelBlockedReason || cancelError}
            </p>
          )}

          <div className="promise-cancel-dialog-actions">
            <button
              ref={keepButtonRef}
              type="button"
              className="promise-cancel-keep"
              onClick={closeCancelDialog}
            >
              유지
            </button>

            <button
              type="button"
              className="promise-cancel-confirm"
              disabled={Boolean(cancelBlockedReason)}
              onClick={confirmCancellation}
            >
              약속 취소
            </button>
          </div>
        </div>
      </dialog>
    </div>
  )
}