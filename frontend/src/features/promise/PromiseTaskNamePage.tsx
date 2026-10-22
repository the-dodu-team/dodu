import { useRef, useState } from 'react'
import arrowLeftIcon from '../../assets/icons/arrow-left.svg'
import closeCircleIcon from '../../assets/icons/close-circle.svg'
import {
  hasForbiddenTaskNameCharacters,
  validateTaskName,
} from './validateTaskName'

type PromiseTaskNamePageProps = {
  initialValue?: string
  onBack: () => void
  onNext: (taskName: string) => void
}

export default function PromiseTaskNamePage({
  initialValue = '',
  onBack,
  onNext,
}: PromiseTaskNamePageProps) {
  const [taskName, setTaskName] = useState(initialValue)
  const [isComposing, setIsComposing] = useState(false)
  const [inputError, setInputError] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  // 화면 미리보기용 문자 수 계산.
  // 서버 연결 전에 프론트와 백엔드의 문자 계산 기준을 함께 확정한다.
  const validation = validateTaskName(taskName)
  const characterCount = validation.characterCount

  // 처음부터 빈 입력칸에는 오류를 표시하지 않는다.
  const errorMessage =
    inputError || (taskName.length > 0 ? validation.errorMessage : '')

  // 한글 조합 중에도 입력값이 유효하면 버튼을 활성화한다.
  const canContinue = validation.isValid && !inputError

  function showInvalidInputError() {
    setInputError('작업명에는 줄바꿈이나 제어문자를 넣을 수 없어요.')
  }

  return (
    <div className="promise-create-page">
      <header className="promise-create-header">
        <button
          type="button"
          className="promise-create-back"
          aria-label="약속 만들기 나가기"
          onClick={onBack}
        >
          <img src={arrowLeftIcon} alt="" width={24} height={24} />
        </button>

        <h1 id="promise-create-title">약속 만들기</h1>
      </header>

      <main className="promise-create-main">
        <form
          className="promise-create-form"
          aria-labelledby="promise-create-title"
          onSubmit={(event) => {
            event.preventDefault()

            if (inputError) return

            // 제출 순간 입력칸의 최신 값을 다시 검사한다.
            const value = inputRef.current?.value ?? taskName
            const result = validateTaskName(value)

            if (!result.isValid) {
              setInputError(result.errorMessage)
              return
            }

            onNext(result.taskName)
          }}
        >
          <div className="promise-create-content">
            <div className="promise-name-field">
              <div
                className={[
                  'promise-name-box',
                  errorMessage ? 'promise-name-box--error' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
              >
                <label htmlFor="promise-task-name">작업명</label>

                <div className="promise-name-input-row">
                  <input
                    ref={inputRef}
                    id="promise-task-name"
                    name="taskName"
                    type="text"
                    value={taskName}
                    placeholder="예: 포트폴리오 정리"
                    autoComplete="off"
                    enterKeyHint="next"
                    aria-invalid={Boolean(errorMessage)}
                    aria-describedby={
                      errorMessage
                        ? 'promise-name-error promise-name-count'
                        : 'promise-name-count'
                    }
                    onChange={(event) => {
                      setTaskName(event.currentTarget.value)
                      setInputError('')
                    }}
                    onPaste={(event) => {
                      // 브라우저가 줄바꿈을 제거하기 전에 원문을 확인한다.
                      const pastedText = event.clipboardData.getData('text')

                      if (hasForbiddenTaskNameCharacters(pastedText)) {
                        event.preventDefault()
                        showInvalidInputError()
                      }
                    }}
                    onDrop={(event) => {
                      // 끌어다 놓은 텍스트도 동일하게 검사한다.
                      const droppedText =
                        event.dataTransfer.getData('text/plain')

                      if (hasForbiddenTaskNameCharacters(droppedText)) {
                        event.preventDefault()
                        showInvalidInputError()
                      }
                    }}
                    onCompositionStart={() => {
                      setIsComposing(true)
                    }}
                    onCompositionEnd={(event) => {
                      setIsComposing(false)
                      setTaskName(event.currentTarget.value)
                    }}
                    onKeyDown={(event) => {
                      // 한글을 확정하는 Enter로 다음 화면에 넘어가지 않게 한다.
                      if (
                        event.key === 'Enter' &&
                        (
                          isComposing ||
                          event.nativeEvent.isComposing ||
                          event.nativeEvent.keyCode === 229
                        )
                      ) {
                        event.preventDefault()
                      }
                    }}
                  />

                  {(taskName.length > 0 || inputError) && (
                    <button
                      type="button"
                      className="promise-name-clear"
                      aria-label="작업명 지우기"
                      onClick={() => {
                        setTaskName('')
                        setInputError('')
                        setIsComposing(false)
                        inputRef.current?.focus()
                      }}
                    >
                      <img
                        src={closeCircleIcon}
                        alt=""
                        width={20}
                        height={20}
                      />
                    </button>
                  )}
                </div>
              </div>

              <div className="promise-name-feedback">
                <p
                  id="promise-name-error"
                  className="promise-name-error"
                  aria-live="polite"
                >
                  {errorMessage}
                </p>

                <p
                  id="promise-name-count"
                  className="promise-name-count"
                  aria-label={`작업명 ${characterCount}자, 최대 30자`}
                >
                  {characterCount}/30
                </p>
              </div>
            </div>
          </div>

          <footer className="promise-create-footer">
            <button
              type="submit"
              className="promise-create-next"
              disabled={!canContinue}
            >
              다음으로
            </button>
          </footer>
        </form>
      </main>
    </div>
  )
}