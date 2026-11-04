import { useEffect, useRef, useState } from 'react'
import type { ValidationSurvey } from '../../api/doduApi'
import { canOpenLiveSurvey, encodeLiveAnswers } from '../../api/liveSurveyApi'
import type { LiveSurveyApi } from '../../api/liveSurveyApi'
import { SurveyApiError } from '../../api/validationSurveyApi'
import { SelfReportPage, ReportReminder } from './ReportScreens'
import ScreenState from '../../components/ScreenState'
import { cleanAnswers, completeAnswers } from './selfReportModel'
import type { ReportAnswers, ReportTarget } from './selfReportModel'

const failure = '일시적인 문제가 생겼어요. 잠시 후 다시 시도해 주세요.'

// 전체 대상 목록이 아니라 서버가 발송한 최근 자기보고 알림에서 후보를 얻는다.
export function LiveSelfReportEntry({ api, onOpen, targets = {} }: { api: LiveSurveyApi; onOpen: (id: string) => void; targets?: Record<string, ReportTarget> }) {
  const [id, setId] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  const [revision, setRevision] = useState(0)
  const [reminder, setReminder] = useState<string | null>(null)
  const claims = useRef(new Map<string, Promise<boolean>>())
  const presented = useRef(new Set<string>())
  const skipping = useRef(false)
  const [skipBusy, setSkipBusy] = useState(false)
  const [skipError, setSkipError] = useState('')
  useEffect(() => {
    let disposed = false
    async function load() {
      try {
        const ids = await api.candidates()
        for (const candidate of ids) {
          if (disposed) return
          let row: ValidationSurvey
          try { row = await api.get(candidate) } catch (error) {
            if (error instanceof SurveyApiError && error.status === 404) continue
            throw error
          }
          if (disposed) return
          if (canOpenLiveSurvey(row, api.now())) {
            setId(candidate); setFailed(false)
            if (targets[candidate] && !presented.current.has(candidate)) {
              // Share the request across StrictMode effect replay. Never retry an uncertain claim automatically.
              if (!claims.current.has(candidate)) claims.current.set(candidate, api.claimReminder(candidate))
              try {
                const show = await claims.current.get(candidate)
                if (!disposed && show && !presented.current.has(candidate)) {
                  presented.current.add(candidate); setReminder(candidate)
                }
              } catch { /* Keep the normal entry available if the one-time claim cannot be confirmed. */ }
            }
            return
          }
        }
        if (!disposed) { setId(null); setFailed(false) }
      } catch { if (!disposed) setFailed(true) }
    }
    void load()
    const refresh = () => { if (document.visibilityState === 'visible') setRevision(value => value + 1) }
    window.addEventListener('focus', refresh)
    return () => { disposed = true; window.removeEventListener('focus', refresh) }
  }, [api, revision, targets])
  async function skipReminder() {
    if (!reminder || skipping.current) return
    skipping.current = true; setSkipBusy(true); setSkipError('')
    try { await api.skip(reminder); setReminder(null); setId(null); setRevision(value => value + 1) }
    catch {
      try {
        const latest = await api.get(reminder)
        if (!canOpenLiveSurvey(latest, api.now())) { setReminder(null); setId(null); setRevision(value => value + 1); return }
      } catch { /* Do not expose transport errors or questionnaire data. */ }
      setSkipError('처리 결과를 확인하지 못했어요. 다시 확인하거나 잠시 후 시도해 주세요.')
    }
    finally { skipping.current = false; setSkipBusy(false) }
  }
  if (failed) return <section aria-label="자기보고"><p role="alert">{failure}</p>
    <button className="report-primary" type="button" onClick={() => setRevision(value => value + 1)}>다시 시도</button></section>
  if (!id) return null
  return <><section className="report-question" aria-label="자기보고">
    <h2 className="survey-title">지난 약속은 어떠셨나요?</h2>
    <button type="button" className="report-primary" onClick={() => onOpen(id)}>지금 작성하기</button>
  </section>{reminder && targets[reminder] && <ReportReminder target={targets[reminder]} busy={skipBusy} error={skipError}
    onWrite={() => { const selected = reminder; setReminder(null); onOpen(selected) }}
    onSkip={() => { void skipReminder() }} onDismiss={() => { if (!skipping.current) setReminder(null) }} />}</>
}

export default function LiveSelfReport({ api, promiseId, target, onHome }: {
  api: LiveSurveyApi; promiseId: string; target: ReportTarget | null; onHome: () => void
}) {
  const [row, setRow] = useState<ValidationSurvey | null>(null)
  const [phase, setPhase] = useState<'loading' | 'ready' | 'saving' | 'error' | 'closed' | 'uncertain'>('loading')
  const [revision, setRevision] = useState(0)
  const [, tick] = useState(0)
  const active = useRef(false)
  const saving = useRef(false)
  const journalKey = 'dodu.survey.pending.v1.' + promiseId
  const uncertain = useRef(sessionStorage.getItem(journalKey) === 'pending')
  useEffect(() => {
    active.current = true
    const timer = window.setInterval(() => tick(value => value + 1), 1000)
    return () => { active.current = false; window.clearInterval(timer) }
  }, [])
  useEffect(() => {
    let disposed = false
    setPhase('loading')
    void api.get(promiseId).then(result => {
      if (disposed) return
      setRow(result)
      if (['Submitted', 'Skipped'].includes(result.lifecycleStatus)) { sessionStorage.removeItem(journalKey); onHome(); return }
      setPhase(uncertain.current ? 'uncertain' : canOpenLiveSurvey(result, api.now()) ? 'ready' : 'closed')
    }).catch(() => { if (!disposed) setPhase(uncertain.current ? 'uncertain' : 'error') })
    return () => { disposed = true }
  }, [api, promiseId, revision, onHome, journalKey])
  async function save(answers?: ReportAnswers) {
    if (saving.current || uncertain.current || phase !== 'ready' || !row || !canOpenLiveSurvey(row, api.now())) return
    if (answers && !completeAnswers(answers)) return
    saving.current = true
    sessionStorage.setItem(journalKey, 'pending')
    setPhase('saving')
    try {
      const result = answers
        ? await api.submit(promiseId, encodeLiveAnswers(cleanAnswers(answers)))
        : await api.skip(promiseId)
      const expected = answers ? 'Submitted' : 'Skipped'
      if (result.lifecycleStatus !== expected) throw new Error('Unexpected survey state')
      sessionStorage.removeItem(journalKey)
      if (active.current) onHome()
    } catch {
      // 응답이 불명확하면 서버 재조회만 허용하여 중복 저장을 방지한다.
      uncertain.current = true
      if (active.current) setPhase('uncertain')
    } finally { saving.current = false }
  }
  const open = row !== null && canOpenLiveSurvey(row, api.now())
  const notice = phase === 'loading' ? '확인 중…'
    : phase === 'uncertain' ? '저장 결과 확인이 필요합니다. 다른 저장은 잠시 중단됩니다.'
    : phase === 'error' ? failure : null
  if (notice) return <ScreenState title="자기보고" description={notice} loading={phase === 'loading'} onBack={onHome}
    onRetry={phase === 'error' || phase === 'uncertain' ? () => setRevision(value => value + 1) : undefined} />
  return <>
    {(phase === 'closed' || (phase === 'ready' && !open)) &&
      <ScreenState title="자기보고" description="응답 가능 기간이 지났거나 이미 처리가 끝난 약속이에요." onBack={onHome} />}
    {row && open && phase !== 'closed' &&
      <SelfReportPage target={target} busy={phase !== 'ready'} onClose={onHome}
        onSubmit={answers => { void save(answers) }} onSkip={() => { void save() }} />}
  </>
}
