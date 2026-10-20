import { useEffect, useRef, useState } from 'react'
import type { FinalPromiseRecord } from '../promise/promiseHistory'
import { completeAnswers, emptyAnswers, recordDate, reportLabel } from './selfReportModel'
import type { ReportAnswers, ReportTarget } from './selfReportModel'
import './self-report.css'

export function HistoryPage({ records, onBack, onCreate }: {
  records: FinalPromiseRecord[]; onBack: () => void; onCreate: () => void
}) {
  const title = useRef<HTMLHeadingElement>(null)
  useEffect(() => { title.current?.focus() }, [])
  const rows = [...new Map(records
    .filter(record => record.status === 'verified' || record.status === 'unverified')
    .map(record => [record.date, record])).values()]
    .sort((a, b) => b.date.localeCompare(a.date))
  return <main className="report-page">
    <header className="report-header">
      <button type="button" aria-label="뒤로" onClick={onBack}>←</button>
      <h1 ref={title} tabIndex={-1}>인증 기록</h1>
    </header>
    {rows.length ? <>
      <p className="history-label">최근 기록</p>
      <ul className="history-list">{rows.map(record => <li key={record.date}>
        <span>{recordDate(record.date)}</span>
        <span className={`history-chip ${record.status}`}>{record.status === 'verified' ? '인증 완료' : '인증 미완료'}</span>
      </li>)}</ul>
      {/* 최근 범위·페이지 방식은 미정이다. 현재 메모리의 기록 전체를 표시한다. */}
      <button className="history-more" type="button" disabled>더 보기 <span aria-hidden="true">›</span></button>
    </> : <div className="history-empty">
      <p>아직 인증 기록이 없어요</p>
      <p className="history-empty-description">첫 시작 약속을 만들고 인증하면<br />여기에 기록이 쌓여요</p>
      <button className="report-primary" type="button" onClick={onCreate}>약속 만들기</button>
    </div>}
  </main>
}

const orderOptions = [
  ['before', '작업을 먼저 시작했고, 인증도 했어요'],
  ['after', '인증한 뒤에 작업을 시작했어요'],
  ['without-auth', '작업은 시작했지만 인증은 못 했어요'],
  ['unknown', '기억나지 않아요'],
] as const
const delayOptions = [
  ['immediate', '바로 시작했어요'], ['within-5', '5분 이내'],
  ['within-15', '15분 이내'], ['over-15', '15분 초과'],
] as const
const reasonOptions = [
  ['condition', '피로·컨디션 때문에'], ['schedule', '다른 일정이 생겨서'],
  ['motivation', '하기 싫거나 흥미가 떨어져서'], ['distraction', '인증 후 다른 일을 해서'],
  ['other', '기타'],
] as const
function Choices<T extends string>({ name, question, options, value, onChange }: {
  name: string; question: string; options: readonly (readonly [T, string])[]
  value: T | null; onChange: (value: T) => void
}) {
  return <fieldset className="report-question">
    <legend>{question}</legend>
    <div className="report-options">{options.map(([key, label]) =>
      <label key={key}><input type="radio" name={name} value={key} checked={value === key}
        onChange={() => onChange(key)} /><span>{label}</span></label>)}</div>
  </fieldset>
}
export function SelfReportPage({ target, onClose, onSkip, onSubmit }: {
  target: ReportTarget; onClose: () => void; onSkip: () => void
  onSubmit: (answers: ReportAnswers) => void
}) {
  const [answers, setAnswers] = useState<ReportAnswers>({ ...emptyAnswers })
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [])
  return <main className="report-page survey-page">
    <header className="report-header"><button type="button" aria-label="닫기" onClick={onClose}>×</button></header>
    <h1 className="survey-title" ref={heading} tabIndex={-1}>지난 약속은 어떠셨나요?</h1>
    <p className="survey-target">{reportLabel(target)}</p>
    <form onSubmit={event => { event.preventDefault(); if (completeAnswers(answers)) onSubmit(answers) }}>
      <fieldset className="report-question">
        <legend>실제 작업을 시작했나요?</legend>
        <div className="report-yesno">
          {(['yes', 'no'] as const).map(value => <label key={value}>
            <input type="radio" name="started" checked={answers.started === value}
              onChange={() => setAnswers({ ...emptyAnswers, started: value })} />
            <span>{value === 'yes' ? '예' : '아니오'}</span>
          </label>)}
        </div>
      </fieldset>
      {answers.started === 'yes' && <Choices name="order" question="인증과 작업 중 무엇을 먼저 했나요?"
        options={orderOptions} value={answers.order}
        onChange={order => setAnswers(previous => ({ ...previous, order, delay: null }))} />}
      {answers.started === 'yes' && answers.order === 'after' &&
        <Choices name="delay" question="인증 후 얼마 만에 작업을 시작했나요?" options={delayOptions}
          value={answers.delay} onChange={delay => setAnswers(previous => ({ ...previous, delay }))} />}
      {answers.started === 'no' && <Choices name="reason" question="시작하지 못한 이유는 무엇인가요?"
        options={reasonOptions} value={answers.reason}
        onChange={reason => setAnswers(previous => ({ ...previous, reason }))} />}
      <footer className="report-footer">
        <button className="report-primary" type="submit" disabled={!completeAnswers(answers)}>제출</button>
        <button className="report-text" type="button" onClick={onSkip}>건너뛰기</button>
      </footer>
    </form>
  </main>
}

export function ReportReminder({ target, onWrite, onSkip, onDismiss }: {
  target: ReportTarget; onWrite: () => void; onSkip: () => void; onDismiss: () => void
}) {
  const dialog = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const oldOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current?.focus()
    return () => { document.body.style.overflow = oldOverflow; previous?.focus() }
  }, [])
  return <div className="report-dim">
    <div ref={dialog} className="report-sheet" role="dialog" aria-modal="true"
      aria-labelledby="report-reminder-title" tabIndex={-1}
      onKeyDown={event => {
        if (event.key === 'Escape') { event.preventDefault(); onDismiss() }
        if (event.key !== 'Tab') return
        const buttons = dialog.current?.querySelectorAll<HTMLButtonElement>('button')
        if (!buttons?.length) return
        const first = buttons[0], last = buttons[buttons.length - 1]
        if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) {
          event.preventDefault(); last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault(); first.focus()
        }
      }}>
      <h2 id="report-reminder-title">지난 약속은 어떠셨나요?</h2>
      <p className="survey-target">{reportLabel(target, false)}</p>
      <p className="report-sheet-description">몇 가지 질문에 답해 주세요.<br />
        건너뛰면 이 자기보고에는 다시 응답할 수 없어요.<br />
        다음 약속 만들기에는 영향이 없어요.</p>
      <button className="report-primary" type="button" onClick={onWrite}>지금 작성하기</button>
      <button className="report-text" type="button" onClick={onSkip}>건너뛰기</button>
    </div>
  </div>
}
