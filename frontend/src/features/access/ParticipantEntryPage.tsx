import { useState } from 'react'

type ParticipantEntryPageProps = {
  onSubmit: (code: string) => void
  isSubmitting?: boolean
  errorMessage?: string
  onClearError?: () => void
}

export default function ParticipantEntryPage({
  onSubmit,
  isSubmitting = false,
  errorMessage,
  onClearError,
}: ParticipantEntryPageProps) {
  const [code, setCode] = useState('')
  const canSubmit = code.trim().length > 0 && !isSubmitting

  return (
    <div className="entry-page">
      <header className="entry-header">
        <span className="app-header-logo">DODU</span>
      </header>

      <main className="entry-main">
        <form
          className="entry-form"
          aria-labelledby="entry-title"
          aria-busy={isSubmitting}
          onSubmit={(event) => {
            event.preventDefault()
            if (canSubmit) onSubmit(code.trim())
          }}
        >
          <div className="entry-content">
            <h1 id="entry-title" className="entry-title">
              참가자 코드를 입력해 주세요
            </h1>

            <p className="entry-description">
              행동검증 참가 신청 후 받은 코드를 입력하면 시작할 수 있어요.
            </p>

            <div className="entry-field">
              <div
                className={
                  errorMessage
                    ? 'entry-input-box entry-input-box--error'
                    : 'entry-input-box'
                }
              >
                <label htmlFor="participant-code">
                  참가자 식별 코드
                </label>

                <input
                  id="participant-code"
                  name="participantCode"
                  type="text"
                  value={code}
                  placeholder="코드를 입력하세요 (예: PT-0231)"
                  autoCapitalize="characters"
                  autoCorrect="off"
                  spellCheck={false}
                  autoComplete="off"
                  required
                  readOnly={isSubmitting}
                  aria-invalid={Boolean(errorMessage)}
                  aria-describedby={
                    errorMessage ? 'participant-code-error' : undefined
                  }
                  onChange={(event) => {
                    setCode(event.target.value)
                    onClearError?.()
                  }}
                />
              </div>

              {errorMessage && (
                <p
                  id="participant-code-error"
                  className="entry-error"
                  role="alert"
                >
                  {errorMessage}
                </p>
              )}
            </div>
          </div>

          <footer className="entry-footer">
            <button
              type="submit"
              className="entry-submit"
              disabled={!canSubmit}
            >
              {isSubmitting ? '확인 중…' : '입장하기'}
            </button>
          </footer>
        </form>
      </main>
    </div>
  )
}