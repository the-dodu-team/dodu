import { useState } from 'react'
import arrowLeftIcon from '../../assets/icons/arrow-left.svg'
import type { PromiseTool } from './types'

type PromiseToolPageProps = {
  initialValue?: PromiseTool | null
  onBack: (tool: PromiseTool | null) => void
  onNext: (tool: PromiseTool) => void
}

function ToolIllustration({ tool }: { tool: PromiseTool }) {
  return (
    <svg
      width="80"
      height="65"
      viewBox="0 0 80 65"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {tool === 'computer' ? (
        <>
          <rect x="7" y="6" width="66" height="42" rx="4" />
          <path d="M7 39H73" />
          <path d="M34 48V58M46 48V58M25 59H55" />
        </>
      ) : (
        <>
          <path d="M40 12C30 7 18 6 7 8V51C19 49 30 51 40 57" />
          <path d="M40 12C50 7 62 6 73 8V51C61 49 50 51 40 57" />
          <path d="M40 12V57" />
          <path d="M15 19L31 22M15 28L31 31M15 37L31 40" />
          <path d="M49 22L65 19M49 31L65 28M49 40L65 37" />
        </>
      )}
    </svg>
  )
}

export default function PromiseToolPage({
  initialValue = null,
  onBack,
  onNext,
}: PromiseToolPageProps) {
  const [selectedTool, setSelectedTool] =
    useState<PromiseTool | null>(initialValue)

  const options: { value: PromiseTool; label: string }[] = [
    { value: 'computer', label: '컴퓨터' },
    { value: 'book', label: '책' },
  ]

  return (
    <div className="promise-create-page">
      <header className="promise-create-header">
        <button
          type="button"
          className="promise-create-back"
          aria-label="날짜·시간 선택으로 돌아가기"
          onClick={() => onBack(selectedTool)}
        >
          <img src={arrowLeftIcon} alt="" width={24} height={24} />
        </button>

        <h1 id="promise-tool-title">약속 만들기</h1>
      </header>

      <main className="promise-create-main">
        <form
          className="promise-create-form"
          aria-labelledby="promise-tool-title"
          onSubmit={(event) => {
            event.preventDefault()
            if (selectedTool) onNext(selectedTool)
          }}
        >
          <div className="promise-create-content">
            <fieldset
              className="promise-tool-fieldset"
              aria-describedby="promise-tool-guide"
            >
              <legend>인증 도구</legend>

              <div className="promise-tool-grid">
                {options.map((option) => (
                  <label
                    key={option.value}
                    className="promise-tool-choice"
                  >
                    <input
                      className="promise-tool-radio"
                      type="radio"
                      name="authenticationTool"
                      value={option.value}
                      checked={selectedTool === option.value}
                      onChange={() => setSelectedTool(option.value)}
                    />

                    <span className="promise-tool-card">
                      <span
                        className="promise-tool-check"
                        aria-hidden="true"
                      >
                        ✓
                      </span>

                      <ToolIllustration tool={option.value} />

                      <span className="promise-tool-label">
                        {option.label}
                      </span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>

            <div id="promise-tool-guide" className="promise-tool-guide">
              <p>하나만 선택할 수 있어요.</p>
              <p>
                시작 시간이 되면 이 도구가 보이게 사진을 찍어 인증해요.
              </p>
            </div>
          </div>

          <footer className="promise-create-footer">
            <button
              type="submit"
              className="promise-create-next"
              disabled={selectedTool === null}
            >
              다음으로
            </button>
          </footer>
        </form>
      </main>
    </div>
  )
}