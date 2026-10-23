import { useEffect, useReducer, useState } from 'react'
import {
  canAnswer, latestTargets, reportKey, reportReducer, reportStatus,
} from './selfReportModel'
import type { ReportAnswers, ReportStore, ReportTarget } from './selfReportModel'

export function useSelfReportMock(targets: ReportTarget[], now: number, atHome: boolean) {
  const [store, dispatch] = useReducer(reportReducer, {} as ReportStore)
  const [selected, setSelected] = useState<ReportTarget | null>(null)
  const [reminder, setReminder] = useState<ReportTarget | null>(null)
  const [visit, reenter] = useReducer((value: number) => value + 1, 0)
  const sorted = latestTargets(targets)
  const signature = JSON.stringify(sorted)

  useEffect(() => {
    const focus = () => reenter()
    const visibility = () => { if (document.visibilityState === 'visible') reenter() }
    window.addEventListener('focus', focus)
    document.addEventListener('visibilitychange', visibility)
    return () => {
      window.removeEventListener('focus', focus)
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [])

  useEffect(() => {
    if (!atHome ||
      (selected && canAnswer(selected, store[reportKey(selected)], now)) ||
      (reminder && canAnswer(reminder, store[reportKey(reminder)], now))) return
    const candidates = JSON.parse(signature) as ReportTarget[]
    // 여러 설문을 연속으로 쌓아 보여주지 않고 가장 최근의 대상만 확인한다.
    const latest = candidates[0]
    if (!latest) return
    const entry = store[reportKey(latest)]
    if (!canAnswer(latest, entry, now) || entry?.reprompted) return
    dispatch({ type: 'seen', target: latest, now })
    setReminder(latest)
    // 최초 Push는 서버 연결 범위다. 이 mock은 홈 진입 시 재안내만 처리한다.
    // 시간 경과만으로 화면을 가로채지 않고 진입·재진입 시점에 확인한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [atHome, visit, signature])

  const activeTarget = selected && canAnswer(selected, store[reportKey(selected)], now) ? selected : null
  const reminderTarget = reminder && canAnswer(reminder, store[reportKey(reminder)], now) ? reminder : null

  function skip(target: ReportTarget) {
    dispatch({ type: 'skip', target, now })
    setSelected(null)
    setReminder(null)
  }
  function submit(answers: ReportAnswers) {
    if (!activeTarget) return
    dispatch({ type: 'submit', target: activeTarget, now, answers })
    setSelected(null)
  }
  return {
    store, activeTarget, reminderTarget,
    open: (target: ReportTarget) => {
      if (!canAnswer(target, store[reportKey(target)], now)) return
      setReminder(null)
      setSelected(target)
    },
    dismiss: () => { setSelected(null); setReminder(null) },
    skip, submit, reenter,
    status: (target: ReportTarget) => reportStatus(target, store[reportKey(target)], now),
  }
}
