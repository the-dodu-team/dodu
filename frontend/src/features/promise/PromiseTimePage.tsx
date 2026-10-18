import { useEffect, useRef, useState } from 'react'
import arrowLeftIcon from '../../assets/icons/arrow-left.svg'

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const KST_OFFSET = 9 * HOUR

function dateKey(timestamp: number) {
  return new Date(timestamp + KST_OFFSET).toISOString().slice(0, 10)
}

function dateLabel(date: string) {
  const [, month, day] = date.split('-')
  return `${Number(month)}/${Number(day)}`
}

function getInitialParts(value: string) {
  const parsed = value ? Date.parse(value) : Number.NaN

  // 미선택 상태에서 휠이 보여줄 기본 위치.
  const timestamp = Number.isFinite(parsed)
    ? parsed
    : Math.ceil((Date.now() + 5 * MINUTE) / (5 * MINUTE)) * 5 * MINUTE

  const kst = new Date(timestamp + KST_OFFSET)
  const hour = kst.getUTCHours()

  return {
    date: dateKey(timestamp),
    period: hour >= 12 ? '오후' : '오전',
    hour: String(hour % 12 || 12),
    minute: String(kst.getUTCMinutes()).padStart(2, '0'),
    selected: Number.isFinite(parsed),
  }
}

type TimeWheelProps = {
  label: string
  options: string[]
  value: string
  onChange: (value: string) => void
}

function TimeWheel({
  label,
  options,
  value,
  onChange,
}: TimeWheelProps) {
  const wheelRef = useRef<HTMLDivElement>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const initialIndex = useRef(Math.max(0, options.indexOf(value)))

  useEffect(() => {
    const wheel = wheelRef.current
    if (wheel) wheel.scrollTop = initialIndex.current * 44

    return () => {
      if (timerRef.current !== null) clearTimeout(timerRef.current)
    }
  }, [])

  function selectOption(index: number) {
    wheelRef.current?.scrollTo({
      top: index * 44,
      behavior: 'smooth',
    })
    onChange(options[index])
  }

  return (
    <div
      ref={wheelRef}
      className="promise-time-wheel-column"
      role="group"
      aria-label={label}
      onScroll={(event) => {
        const wheel = event.currentTarget

        if (timerRef.current !== null) clearTimeout(timerRef.current)

        timerRef.current = setTimeout(() => {
          const index = Math.max(
            0,
            Math.min(options.length - 1, Math.round(wheel.scrollTop / 44)),
          )

          if (options[index] !== value) onChange(options[index])
        }, 150)
      }}
    >
      {options.map((option, index) => (
        <button
          key={option}
          type="button"
          className={
            option === value
              ? 'promise-time-wheel-option promise-time-wheel-option--selected'
              : 'promise-time-wheel-option'
          }
          aria-label={`${option}${label === '시' || label === '분' ? label : ''}`}
          aria-pressed={option === value}
          tabIndex={option === value ? 0 : -1}
          onClick={() => selectOption(index)}
          onKeyDown={(event) => {
            let nextIndex = index

            if (event.key === 'ArrowDown') {
              nextIndex = Math.min(options.length - 1, index + 1)
            } else if (event.key === 'ArrowUp') {
              nextIndex = Math.max(0, index - 1)
            } else if (event.key === 'Home') {
              nextIndex = 0
            } else if (event.key === 'End') {
              nextIndex = options.length - 1
            } else {
              return
            }

            event.preventDefault()
            selectOption(nextIndex)

            wheelRef.current
              ?.querySelectorAll<HTMLButtonElement>('button')
              [nextIndex]?.focus({ preventScroll: true })
          }}
        >
          {option}
        </button>
      ))}
    </div>
  )
}

type PromiseTimePageProps = {
  initialValue?: string
  mode?: 'create' | 'edit'
  blockedReason?: string
  saveError?: string
  onBack: (scheduledAt: string) => void
  onNext: (scheduledAt: string) => void
}

export default function PromiseTimePage({
  initialValue = '',
  mode = 'create',
  blockedReason = '',
  saveError = '',
  onBack,
  onNext,
}: PromiseTimePageProps) {
  const [initial] = useState(() => getInitialParts(initialValue))
  const [date, setDate] = useState(initial.date)
  const [period, setPeriod] = useState(initial.period)
  const [hour, setHour] = useState(initial.hour)
  const [minute, setMinute] = useState(initial.minute)
  const [selected, setSelected] = useState(initial.selected)
  const [now, setNow] = useState(Date.now)

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const isEditing = mode === 'edit'
  const today = dateKey(now)
  const dates = Array.from({ length: 4 }, (_, index) =>
    dateKey(now + index * DAY),
  )

  const hour24 = Number(hour) % 12 + (period === '오후' ? 12 : 0)
  const scheduledAt =
    `${date}T${String(hour24).padStart(2, '0')}:${minute}:00+09:00`
  const timestamp = Date.parse(scheduledAt)
  const originalTimestamp = Date.parse(initialValue)
  const hasChanged = timestamp !== originalTimestamp

  const errorMessage = !selected
    ? ''
    : !Number.isFinite(timestamp)
      ? '날짜와 시간을 다시 선택해 주세요.'
      : timestamp < now + 5 * MINUTE
        ? '지금부터 5분 이후로 정해 주세요.'
        : timestamp > now + 72 * HOUR
          ? '지금부터 72시간 이내로 정해 주세요.'
          : ''

  const canContinue =
    selected &&
    Number.isFinite(timestamp) &&
    errorMessage === '' &&
    !blockedReason &&
    (!isEditing || hasChanged)

  const displayValue = selected
    ? `${date === today ? '오늘' : dateLabel(date)} · ${period} ${hour}:${minute}`
    : ''

  const originalTimeLabel = Number.isFinite(originalTimestamp)
    ? new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul',
        month: 'numeric',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      }).format(new Date(originalTimestamp))
    : ''

  return (
    <div className="promise-create-page">
      <header className="promise-create-header">
        <button
          type="button"
          className="promise-create-back"
          aria-label={
            isEditing ? '약속 관리로 돌아가기' : '작업명 입력으로 돌아가기'
          }
          onClick={() => onBack(selected ? scheduledAt : '')}
        >
          <img src={arrowLeftIcon} alt="" width={24} height={24} />
        </button>

        <h1 id="promise-time-title">
          {isEditing ? '시작 시간 수정' : '약속 만들기'}
        </h1>
      </header>

      <main className="promise-create-main">
        <form
          className="promise-create-form"
          aria-labelledby="promise-time-title"
          onSubmit={(event) => {
            event.preventDefault()

            // 제출 순간에도 시간 범위를 다시 확인한다.
            const current = Date.now()
            setNow(current)

            if (
              selected &&
              !blockedReason &&
              (!isEditing || hasChanged) &&
              Number.isFinite(timestamp) &&
              timestamp >= current + 5 * MINUTE &&
              timestamp <= current + 72 * HOUR
            ) {
              onNext(scheduledAt)
            }
          }}
        >
          <div className="promise-create-content">
            {isEditing && (
              <div className="promise-name-box promise-time-original">
                <label htmlFor="promise-original-time">기존 시작 시간</label>

                <input
                  id="promise-original-time"
                  className="promise-time-display"
                  value={originalTimeLabel}
                  readOnly
                />
              </div>
            )}

            <div className="promise-name-field">
              <div
                className={
                  errorMessage
                    ? 'promise-name-box promise-name-box--error'
                    : 'promise-name-box'
                }
              >
                <label htmlFor="promise-selected-time">
                  {isEditing ? '변경할 시작 시간' : '시작 시간'}
                </label>

                <input
                  id="promise-selected-time"
                  className="promise-time-display"
                  value={displayValue}
                  placeholder="날짜와 시간을 선택해 주세요"
                  readOnly
                  aria-invalid={Boolean(errorMessage)}
                  aria-describedby="promise-time-error promise-time-guide"
                />
              </div>

              <p
                id="promise-time-error"
                className="promise-name-error"
                aria-live="polite"
              >
                {errorMessage}
              </p>
            </div>

            <div
              className="promise-date-chips"
              role="group"
              aria-label="시작 날짜"
            >
              {dates.map((item) => (
                <button
                  key={item}
                  type="button"
                  className={
                    item === date
                      ? 'promise-date-chip promise-date-chip--selected'
                      : 'promise-date-chip'
                  }
                  aria-pressed={item === date}
                  onClick={() => {
                    setDate(item)
                    setSelected(true)
                  }}
                >
                  {item === today ? '오늘 ' : ''}
                  {dateLabel(item)}
                </button>
              ))}
            </div>

            <div className="promise-time-wheel">
              <TimeWheel
                label="오전·오후"
                options={['오전', '오후']}
                value={period}
                onChange={(value) => {
                  setPeriod(value)
                  setSelected(true)
                }}
              />

              <TimeWheel
                label="시"
                options={Array.from({ length: 12 }, (_, index) =>
                  String(index + 1),
                )}
                value={hour}
                onChange={(value) => {
                  setHour(value)
                  setSelected(true)
                }}
              />

              <TimeWheel
                label="분"
                options={Array.from({ length: 12 }, (_, index) =>
                  String(index * 5).padStart(2, '0'),
                )}
                value={minute}
                onChange={(value) => {
                  setMinute(value)
                  setSelected(true)
                }}
              />
            </div>

            <div id="promise-time-guide" className="promise-time-guide">
              <p>현재 시각 +5분 ~ +72시간 사이로 정할 수 있어요.</p>
              <p>
                {isEditing
                  ? '시작 전까지 날짜·시간을 1번 수정할 수 있어요.'
                  : '약속은 한 번에 하나만 만들 수 있어요.'}
              </p>
            </div>

            {(blockedReason || saveError) && (
              <p className="promise-confirm-error" role="alert">
                {blockedReason || saveError}
              </p>
            )}
          </div>

          <footer className="promise-create-footer">
            <div className={isEditing ? 'promise-confirm-actions' : undefined}>
              {isEditing && (
                <button
                  type="button"
                  className="promise-confirm-previous"
                  onClick={() => onBack(initialValue)}
                >
                  취소
                </button>
              )}

              <button
                type="submit"
                className="promise-create-next"
                disabled={!canContinue}
              >
                {isEditing ? '저장' : '다음으로'}
              </button>
            </div>
          </footer>
        </form>
      </main>
    </div>
  )
}