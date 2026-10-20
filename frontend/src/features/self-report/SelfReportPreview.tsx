import { useRef, useState } from 'react'
import type { ReactNode, RefObject } from 'react'
import type { FinalPromiseRecord } from '../promise/promiseHistory'
import { HistoryPage, ReportReminder, SelfReportPage } from './ReportScreens'
import { completeAnswers, reportKey, reportWindow } from './selfReportModel'
import type { ReportAnswers, ReportTarget } from './selfReportModel'
import { useSelfReportMock } from './useSelfReportMock'

const example: ReportTarget = {
  date: '2026-09-18',
  startTime: '14:30',
  taskName: '포트폴리오 정리',
  tool: 'computer',
  status: 'verified',
}

const initialRecords: FinalPromiseRecord[] = [
  { date: '2026-09-19', status: 'verified' },
  // 자기보고 대상의 인증 결과와 기록 목록의 결과를 동일하게 유지한다.
  { date: example.date, status: example.status },
  { date: '2026-09-17', status: 'verified' },
  { date: '2026-09-16', status: 'verified' },
  { date: '2026-09-15', status: 'verified' },
]

export type PreviewHomeControls = {
  home: () => void
  create: () => void
}

type SelfReportPreviewProps = {
  renderHome: (
    records: FinalPromiseRecord[],
    controls: RefObject<PreviewHomeControls | null>,
  ) => ReactNode
}

export default function SelfReportPreview({ renderHome }: SelfReportPreviewProps) {
  const [screen, setScreen] = useState<'home' | 'history' | 'empty'>('home')
  const [clock, setClock] = useState(reportWindow(example).promptedAt + 60_000)
  const homeControls = useRef<PreviewHomeControls | null>(null)
  const report = useSelfReportMock([example], clock, screen === 'home')
  const entry = report.store[reportKey(example)]

  function showHome(action: 'home' | 'create' = 'home') {
    setScreen('home')
    homeControls.current?.[action]()
  }

  function closeReport() {
    report.dismiss()
    showHome()
  }

  function skipReport(target: ReportTarget) {
    report.skip(target)
    showHome()
  }

  function submitReport(answers: ReportAnswers) {
    if (!report.activeTarget || !completeAnswers(answers)) return
    report.submit(answers)
    showHome()
  }

  const hideHome = Boolean(report.activeTarget) || screen !== 'home'

  return (
    <>
      <aside
        className="report-dev"
        aria-label="개발용 자기보고 확인"
        inert={Boolean(report.reminderTarget)}
      >
        <strong>개발용 mock · 서버 저장 없음</strong>
        <div>
          <button type="button" onClick={() => {
            closeReport()
            report.reenter()
          }}>홈 재진입</button>
          <button type="button" onClick={() => {
            report.dismiss()
            setScreen('history')
          }}>기록 목록</button>
          <button type="button" onClick={() => {
            report.dismiss()
            setScreen('empty')
          }}>기록 없음</button>
          <button type="button" onClick={() => report.open(example)}>최초 안내 진입</button>
          <button type="button" onClick={() => setClock(reportWindow(example).expiresAt)}>24시간 만료</button>
          <button type="button" onClick={() => window.location.reload()}>mock 초기화</button>
        </div>
        <output>
          자기보고: {report.status(example)} · 실제 시작: {entry?.answers.started ?? 'unknown'} ·
          인증 결과: {example.status} · 재안내: {entry?.reprompted ? '1회' : '0회'}
        </output>
      </aside>

      {/* 홈을 제거하지 않고 숨겨 새 약속·인증 기록 상태를 유지한다. */}
      <div hidden={hideHome} inert={hideHome || Boolean(report.reminderTarget)}>
        {renderHome(initialRecords, homeControls)}
      </div>

      {report.activeTarget ? (
        <SelfReportPage
          key={reportKey(report.activeTarget)}
          target={report.activeTarget}
          onClose={closeReport}
          onSkip={() => {
            if (report.activeTarget) skipReport(report.activeTarget)
          }}
          onSubmit={submitReport}
        />
      ) : screen !== 'home' ? (
        <HistoryPage
          records={screen === 'empty' ? [] : initialRecords}
          onBack={() => showHome()}
          onCreate={() => showHome('create')}
        />
      ) : null}

      {report.reminderTarget && (
        <ReportReminder
          target={report.reminderTarget}
          onWrite={() => {
            if (report.reminderTarget) report.open(report.reminderTarget)
          }}
          onSkip={() => {
            if (report.reminderTarget) skipReport(report.reminderTarget)
          }}
          onDismiss={closeReport}
        />
      )}
    </>
  )
}
