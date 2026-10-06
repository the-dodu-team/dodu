import type { Dispatch, SetStateAction } from 'react'
import Button from '../../components/Button'
import type { PromiseDraft } from './types'

type PromiseFormPageProps = {
  draft: PromiseDraft
  setDraft: Dispatch<SetStateAction<PromiseDraft>>
  isEditing: boolean
  onConfirm: () => void
  onBack: () => void
}

export default function PromiseFormPage({ draft, setDraft, isEditing, onConfirm, onBack }: PromiseFormPageProps) {
  return (
    <section className="promise-editor">
      <h1 className="promise-screen-title">
        {isEditing ? '약속 수정하기' : '약속 만들기'}
      </h1>
      <p className="promise-screen-description">
        오늘 약속을 작성하는 미리보기입니다.
        입력한 내용은 새로고침하면 사라집니다.
      </p>

      <form
        onSubmit={(event) => {
          event.preventDefault()

          if (!draft.taskName.trim() || !draft.startTime) return

          onConfirm()
        }}
      >
        <div className="text-field">
          <label htmlFor="promise-task-name">작업명</label>
          <input
            id="promise-task-name"
            value={draft.taskName}
            onChange={(event) => {
              const taskName = event.target.value
              setDraft((previous) => ({ ...previous, taskName }))
            }}
            placeholder="예: 포트폴리오 정리"
            required
          />
        </div>

        <div className="text-field">
          <label htmlFor="promise-start-time">예정 시각</label>
          <input
            id="promise-start-time"
            type="time"
            value={draft.startTime}
            onChange={(event) => {
              const startTime = event.target.value
              setDraft((previous) => ({ ...previous, startTime }))
            }}
            aria-describedby="promise-time-hint"
            required
          />
          <p id="promise-time-hint" className="text-field-hint">
            한국 표준시(KST) 기준입니다.
          </p>
        </div>

        <fieldset
          className="promise-tool-options"
          aria-describedby="promise-tool-hint"
        >
          <legend>인증 도구</legend>

          <label>
            <input
              type="radio"
              name="promise-tool"
              value="computer"
              checked={draft.tool === 'computer'}
              onChange={() => {
                setDraft((previous) => ({
                  ...previous,
                  tool: 'computer',
                }))
              }}
            />
            컴퓨터
          </label>

          <label>
            <input
              type="radio"
              name="promise-tool"
              value="book"
              checked={draft.tool === 'book'}
              onChange={() => {
                setDraft((previous) => ({
                  ...previous,
                  tool: 'book',
                }))
              }}
            />
            실제 책
          </label>

          <p id="promise-tool-hint" className="text-field-hint">
            이 미리보기에서는 컴퓨터 또는 실제 책을 선택합니다.
          </p>
        </fieldset>

        <div className="promise-actions">
          <button
            type="submit"
            className="button"
            disabled={!draft.taskName.trim() || !draft.startTime}
          >
            내용 확인
          </button>
          <Button onClick={() => onBack()}>
            홈으로 돌아가기
          </Button>
        </div>
      </form>
    </section>
  )
}
