import { focusScreenHeading } from '../promise/focusScreenHeading'
import { notify } from '../../components/feedback'
import LiveSelfReport, { LiveSelfReportEntry } from '../self-report/LiveSelfReport'
import { createLiveSurveyApi } from '../../api/liveSurveyApi'
import { createBackendFetch, createDoduApi, DoduApiError } from '../../api/doduApi'
import ScreenState from '../../components/ScreenState'
import SettingsPage from '../settings/SettingsPage'
import LivePhotoAuth from '../photo-auth/LivePhotoAuth'
import PromiseAuthPreview from '../promise/PromiseAuthPreview'
import { HistoryPage } from '../self-report/ReportScreens'
import type { FinalPromiseRecord } from '../promise/promiseHistory'
import { savedPromiseHistory } from '../promise/savedPromiseHistory'
import type { SavedPromiseHistory } from '../promise/savedPromiseHistory'
import type { ReportTarget } from '../self-report/selfReportModel'
import LiveOnboardingFlow from '../onboarding/LiveOnboardingFlow'
import PermissionRecoveryGate from '../onboarding/PermissionRecoveryGate'
import PushConnection from '../notifications/PushConnection'
import { readPushConfig } from '../notifications/pushConfig'
import { createPushController } from '../notifications/pushController'
import type { PushNotice, PushState } from '../notifications/pushController'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createParticipantPromiseApi, ParticipantPromiseApiError } from '../../api/participantPromiseApi'
import type { ParticipantState, PromiseResponse } from '../../api/participantPromiseApi'
import ParticipantEntryPage from '../access/ParticipantEntryPage'
import AccessClosedPage from '../home/AccessClosedPage'
import AppHeader from '../../components/AppHeader'
import TodayPromiseCard from '../../components/TodayPromiseCard'
import WeeklyPromiseCard from '../../components/WeeklyPromiseCard'
import PromiseTaskNamePage from '../promise/PromiseTaskNamePage'
import PromiseTimePage from '../promise/PromiseTimePage'
import PromiseToolPage from '../promise/PromiseToolPage'
import PromiseConfirmPage from '../promise/PromiseConfirmPage'
import PromiseManagePage from '../promise/PromiseManagePage'
import { formatPromiseDateTime } from '../promise/formatPromise'
import type { PreviewPromise, PromiseTool } from '../promise/types'
import { createLiveSession } from './liveSession'
import type { PendingWrite } from './liveSession'
import { promiseErrorMessage as errorMessage, creationBlockCode, scheduledDate } from './promiseFeedback'
import '../home/home-states.css'

type Screen = 'home' | 'name' | 'time' | 'tool' | 'confirm' | 'manage' | 'edit' | 'photo' | 'history' | 'settings'
function asDisplay(row: PromiseResponse): PreviewPromise {
  const kst = new Date(Date.parse(row.scheduledAt) + 9 * 3600000).toISOString()
  // 표시 컴포넌트의 입력 형식이다. 실제 인증 상태를 계산하거나 저장하지 않는다.
  return { taskName: row.taskTitle, date: kst.slice(0, 10), startTime: kst.slice(11, 16),
    tool: row.toolType === 'BOOK' ? 'book' : 'computer', status: 'pending' }
}

export default function LiveParticipantApp() {
  const [session] = useState(() => createLiveSession(sessionStorage, localStorage))
  const [backendFetch] = useState(() => createBackendFetch(import.meta.env.VITE_DODU_API_BASE_URL ?? ''))
  const [api] = useState(() => createParticipantPromiseApi({ getToken: session.token, fetcher: backendFetch }))
  const [surveyApi] = useState(() => createLiveSurveyApi(session.token, backendFetch))
  const [surveyId, setSurveyId] = useState<string | null>(() => new URLSearchParams(window.location.search).get('self-report'))
  const [participant, setParticipant] = useState<ParticipantState | null>(null)
  const [active, setActive] = useState<PromiseResponse | null>(null)
  const [history, setHistory] = useState<FinalPromiseRecord[]>([])
  const [savedHistory, setSavedHistory] = useState<SavedPromiseHistory[]>([])
  const [reportTargets, setReportTargets] = useState<Record<string, ReportTarget>>({})
  const [historyLoaded, setHistoryLoaded] = useState(false)
  const currentToken = session.token()
  const dataApi = useMemo(() => createDoduApi(currentToken ?? '', import.meta.env.VITE_DODU_API_BASE_URL ?? ''), [currentToken])
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [onboardingDismissed, setOnboardingDismissed] = useState(false)
  const [deviceChangeRequired, setDeviceChangeRequired] = useState(false)
  const [closed, setClosed] = useState(false)
  const [pushState, setPushState] = useState<PushState>('disabled')
  const [pushNotice, setPushNotice] = useState<PushNotice | null>(null)
  const [push] = useState(() => createPushController({
    ...readPushConfig(import.meta.env), session: session.token,
    permission: () => typeof Notification === 'undefined' ? 'unsupported' : Notification.permission,
    driver: async () => (await import('../notifications/firebasePush')).firebasePushDriver(),
    subscribe: (owner, token) => createDoduApi(owner, import.meta.env.VITE_DODU_API_BASE_URL ?? '').subscribeNotifications(token),
    state: setPushState, receive: notice => {
      setPushNotice(notice)
      if (notice.data?.type === 'SESSION_REPLACED') void refresh()
    },
  }))
  const [screen, setScreen] = useState<Screen>('home')
  const [taskName, setTaskName] = useState('')
  const [scheduledAt, setScheduledAt] = useState('')
  const [tool, setTool] = useState<PromiseTool>('computer')
  const [error, setError] = useState('')
  const [pending, setPending] = useState<PendingWrite | null>(() => session.pending())
  const [serverClock, setServerClock] = useState<{ value: number; measured: number } | null>(null)
  const [, tick] = useState(0)
  const content = useRef<HTMLDivElement>(null)
  const lastFocusedScreen = useRef<string | null>(null)
  const running = useRef(false)
  const epoch = useRef(0)
  const mounted = useRef(false)
  const reader = useRef<AbortController | null>(null)
  const blockedDates = useRef(new Set<string>())
  const lastReadAt = useRef(0)
  const errorOrigin = useRef<'read' | 'write' | null>(null)
  const now = serverClock ? serverClock.value + performance.now() - serverClock.measured : null

  useEffect(() => {
    let disposed = false
    void push.pause().then(() => { if (!disposed && currentToken && loaded && !closed) push.resume() })
    const sync = () => { if (!disposed && document.visibilityState === 'visible') void push.sync() }
    const timer = setInterval(sync, 60000)
    window.addEventListener('focus', sync)
    document.addEventListener('visibilitychange', sync)
    return () => {
      disposed = true; clearInterval(timer); window.removeEventListener('focus', sync)
      document.removeEventListener('visibilitychange', sync); void push.pause()
    }
  }, [push, currentToken, loaded, closed])

  function fail(reason: unknown, origin: 'read' | 'write' = 'write') {
    if (reason instanceof DOMException && reason.name === 'AbortError') return
    if (reason instanceof ParticipantPromiseApiError && reason.httpStatus === 401) {
      setOnboardingDismissed(false); session.clear(); setParticipant(null); setActive(null); setLoaded(false); setPending(null)
      setHistory([]); setSavedHistory([]); setReportTargets({}); setHistoryLoaded(false); blockedDates.current.clear(); lastReadAt.current = 0
    }
    if (reason instanceof ParticipantPromiseApiError && reason.code === 'STUDY_PERIOD_ENDED') setClosed(true)
    errorOrigin.current = origin
    setError(errorMessage(reason))
    if (origin === 'write') notify('error', errorMessage(reason))
  }
  function mark(value: PendingWrite | null) { session.mark(value); setPending(value) }
  async function refresh() {
    if (running.current || !session.token()) return
    if (reader.current && !reader.current.signal.aborted) return
    const version = ++epoch.current
    const ownerToken = session.token()
    const abort = new AbortController(); reader.current = abort
    setBusy(true)
    if (errorOrigin.current === 'read') { setError(''); errorOrigin.current = null }
    try {
      const me = await api.me(abort.signal)
      const promise = await api.active(abort.signal)
      if (!mounted.current || version !== epoch.current) return
      setParticipant(me.data); setActive(promise.data); setLoaded(true)
      try {
        const rows = await createDoduApi(ownerToken!, import.meta.env.VITE_DODU_API_BASE_URL ?? '').listPromiseHistory()
        if (!mounted.current || version !== epoch.current || ownerToken !== session.token()) return
        setSavedHistory(savedPromiseHistory(rows))
        setHistory(rows.flatMap(row => {
          const outcome = row.finalOutcome ?? row.lifecycleStatus
          if (!['AuthCompleted', 'AuthIncomplete'].includes(outcome)) return []
          return [{ id: row.promiseId, date: new Date(Date.parse(row.scheduledAt) + 9 * 3600000).toISOString().slice(0, 10),
            status: outcome === 'AuthCompleted' ? 'verified' as const : 'unverified' as const }]
        }))
        setHistoryLoaded(true)
        setReportTargets(Object.fromEntries(rows.flatMap(row => {
          const outcome = row.finalOutcome ?? row.lifecycleStatus
          if (!['AuthCompleted', 'AuthIncomplete'].includes(outcome)) return []
          const display = asDisplay(row)
          return [[row.promiseId, { ...display, status: outcome === 'AuthCompleted' ? 'verified' as const : 'unverified' as const }]]
        })))
      } catch { if (mounted.current && version === epoch.current) setHistoryLoaded(false) }
      if (me.serverTime) setServerClock({ value: Date.parse(me.serverTime), measured: performance.now() })
      const unresolved = session.pending()
      // 수정 응답을 잃은 경우 같은 약속의 갱신 버전·시간을 확인한다.
      if (unresolved?.kind === 'reschedule' && promise.data?.promiseId === unresolved.id &&
          promise.data.version > unresolved.version && Date.parse(promise.data.scheduledAt) === Date.parse(unresolved.scheduledAt)) mark(null)
      // 활성 목록에서 사라진 것은 취소 성공이라고 표시하지 않고 현재 목록만 갱신한다.
      if (unresolved?.kind === 'cancel' && promise.data?.promiseId !== unresolved.id) mark(null)
      lastReadAt.current = Date.now()
    } catch (reason) { if (mounted.current && version === epoch.current) fail(reason, 'read') }
    finally {
      if (reader.current === abort) reader.current = null
      if (mounted.current && version === epoch.current) setBusy(false)
    }
  }
  useEffect(() => {
    mounted.current = true
    void refresh()
    const timer = setInterval(() => tick(value => value + 1), 1000)
    return () => { mounted.current = false; epoch.current++; reader.current?.abort(); clearInterval(timer) }
    // 세션·API 인스턴스는 이 앱이 마운트된 동안 유지된다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  useEffect(() => {
    // Restoring focus while editing must not repeatedly reload or cancel the form.
    if (screen !== 'home' || surveyId) return
    const focus = () => {
      if (document.visibilityState === 'visible' && Date.now() - lastReadAt.current >= 30000) void refresh()
    }
    window.addEventListener('focus', focus)
    return () => window.removeEventListener('focus', focus)
    // refresh uses the stable session/API instances and guards overlapping reads.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screen, surveyId])

  useEffect(() => {
    if (screen === 'home' && active && now !== null && now >= Date.parse(active.scheduledAt) + 30 * 60000 &&
        Date.now() - lastReadAt.current >= 30000) void refresh()
    // Poll the server after the deadline; never finalize an accepted attempt in the browser.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [now, screen, active?.promiseId])

  async function login(code: string, confirmDeviceChange = false) {
    if (running.current) return
    running.current = true; const version = ++epoch.current
    setBusy(true); setError(''); reader.current?.abort()
    try {
      const result = await api.enter(code, session.device(), undefined, confirmDeviceChange)
      if (!mounted.current || version !== epoch.current) return
      setDeviceChangeRequired(false); setOnboardingDismissed(false); session.enter(result.data.sessionToken)
      setParticipant(result.data); setScreen('home'); setPending(null)
      // 입장할 때마다 환영 문구 표시. 첫 설정 대상에게만 표시한다.
      // notify('success', `Dodu에 오신걸 환영합니다!`)
      if (!result.data.onboardingCompleted) notify('success', 'Dodu에 오신 걸 환영합니다!')
      setActive(null); setHistory([]); setSavedHistory([]); setReportTargets({}); setHistoryLoaded(false); setLoaded(false)
      blockedDates.current.clear(); lastReadAt.current = 0
    } catch (reason) {
      if (mounted.current && version === epoch.current) {
        if (reason instanceof ParticipantPromiseApiError && reason.code === 'DEVICE_CHANGE_CONFIRMATION_REQUIRED') setDeviceChangeRequired(true)
        else fail(reason)
      }
    }
    finally {
      running.current = false
      if (mounted.current && version === epoch.current) { setBusy(false); if (session.token()) void refresh() }
    }
  }
  async function write(operation: PendingWrite): Promise<string | undefined> {
    if (running.current) return undefined
    if (pending && JSON.stringify(pending) !== JSON.stringify(operation)) {
      const message = errorMessage(new ParticipantPromiseApiError('REQUEST_IN_PROGRESS', 409))
      setError(message); notify('error', message); return message
    }
    if (operation.kind === 'create' && !pending) {
      const blocked = creationBlockCode(operation.payload, clockNow(), Boolean(active), blockedDates.current)
      if (blocked) {
        const message = errorMessage(new ParticipantPromiseApiError(blocked, 409))
        setError(message); notify('error', message); return message
      }
    }
    running.current = true; const version = ++epoch.current
    reader.current?.abort(); setBusy(true); setError('')
    let succeeded = false
    let reconcile = false
    try {
      mark(operation)
      if (operation.kind === 'create') await api.prepareCreate(operation.payload, operation.key).send()
      else if (operation.kind === 'reschedule') await api.reschedule(operation.id, operation.scheduledAt, operation.version)
      else await api.cancel(operation.id, operation.version)
      if (!mounted.current || version !== epoch.current) return undefined
      mark(null); setScreen('home'); setLoaded(false); succeeded = true
      notify('success', operation.kind === 'create' ? '약속이 저장됐어요.' : operation.kind === 'reschedule' ? '약속 시간이 변경됐어요.' : '약속이 취소됐어요.')
    } catch (reason) {
      if (!mounted.current || version !== epoch.current) return undefined
      if (reason instanceof ParticipantPromiseApiError && !reason.outcomeUnknown) mark(null)
      if (reason instanceof ParticipantPromiseApiError &&
          ['PROMISE_ACTIVE_EXISTS', 'PROMISE_VERSION_CONFLICT'].includes(reason.code)) reconcile = true
      if (reason instanceof ParticipantPromiseApiError && reason.code === 'PROMISE_DATE_ALREADY_USED') {
        const date = operation.kind === 'create' ? scheduledDate(operation.payload)
          : operation.kind === 'reschedule' ? scheduledDate({ scheduledAt: operation.scheduledAt }) : ''
        if (date) blockedDates.current.add(date)
      }
      fail(reason)
      return errorMessage(reason)
    } finally {
      running.current = false
      if (mounted.current && version === epoch.current) { setBusy(false); if ((succeeded || reconcile) && session.token()) void refresh() }
    }
  }
  const clockNow = () => serverClock ? serverClock.value + performance.now() - serverClock.measured : Date.now()
  const home = useCallback(() => {
    setError(''); setScreen('home'); setSurveyId(null)
    const url = new URL(window.location.href)
    url.searchParams.delete('self-report')
    window.history.replaceState(window.history.state, '', url)
  }, [])
  const modifyAllowed = Boolean(active && now !== null && Date.parse(active.scheduledAt) > now && !pending)
  async function logout() {
    await push.pause()
    try {
      try { await dataApi.unsubscribeNotifications() }
      catch (cause) { if (!(cause instanceof DoduApiError && cause.httpStatus === 401)) throw cause }
      await push.removeToken()
    } catch (cause) { push.resume(); throw cause }
    setPushNotice(null); setPushState('disabled')
    // Stop in-flight reads before removing the current browser's session only.
    epoch.current++; reader.current?.abort(); reader.current = null
    session.clear(); setParticipant(null); setActive(null); setPending(null)
    setHistory([]); setSavedHistory([]); setReportTargets({}); setHistoryLoaded(false); setLoaded(false); setError('')
    setOnboardingDismissed(false); setSurveyId(null); setScreen('home')
    blockedDates.current.clear(); lastReadAt.current = 0
    notify('success', '로그아웃했어요.')
  }
  // 실제 화면 전환 때만 제목으로 이동한다. 시계 갱신과 서버 재조회는 포커스를 빼앗지 않는다.
  const visibleScreen = closed ? 'closed'
    : !participant && !session.token() ? 'entry'
    : !loaded ? 'loading'
    : participant && !participant.onboardingCompleted && !onboardingDismissed ? 'onboarding'
    : screen
  useEffect(() => {
    if (busy || visibleScreen === 'loading' || lastFocusedScreen.current === visibleScreen) return
    const frame = requestAnimationFrame(() => {
      const modal = content.current?.querySelector<HTMLElement>('[role="dialog"][aria-modal="true"]')
      if (modal && modal.getClientRects().length) { modal.focus(); lastFocusedScreen.current = visibleScreen; return }
      focusScreenHeading(content.current)
      lastFocusedScreen.current = visibleScreen
    })
    return () => cancelAnimationFrame(frame)
  }, [visibleScreen, busy])
  let page
  if (closed) page = <AccessClosedPage />
  else if (!participant && !session.token()) page = <ParticipantEntryPage onSubmit={(code, confirmed) => { void login(code, confirmed) }}
    isSubmitting={busy} errorMessage={error} onClearError={() => setError('')} deviceChangeRequired={deviceChangeRequired}
    onCancelDeviceChange={() => { setDeviceChangeRequired(false); setError('') }} />
  else if (!loaded) page = <ScreenState title="DODU" loading={busy} description={busy ? '약속을 불러오고 있어요' : '약속을 불러오지 못했어요.'}
    onRetry={busy ? undefined : () => { void refresh() }} />
  else if (participant && !participant.onboardingCompleted && !onboardingDismissed) page = <LiveOnboardingFlow
    onClose={() => { void logout().catch(() => { setError('입장 화면으로 돌아가지 못했어요. 다시 시도해 주세요.'); notify('error', '입장 화면으로 돌아가지 못했어요. 다시 시도해 주세요.') }) }}
    onComplete={async () => {
      await dataApi.completeOnboarding()
      setParticipant(current => current ? { ...current, onboardingCompleted: true } : current)
      setScreen('home')
    }} />
  else if (surveyId) page = <LiveSelfReport key={surveyId} api={surveyApi} promiseId={surveyId}
    target={reportTargets[surveyId] ?? null} onHome={home} />
  else if (screen === 'settings') page = <SettingsPage onBack={home} onLogout={logout}
    notificationConnection={<PushConnection state={pushState} notice={pushNotice}
      retry={() => { void push.sync() }} dismiss={() => setPushNotice(null)} />} />
  else if (screen === 'photo' && active) page = <LivePhotoAuth api={dataApi}
    promise={{ promiseId: active.promiseId, taskName: active.taskTitle, tool: active.toolType === 'BOOK' ? 'book' : 'computer', scheduledAt: active.scheduledAt }}
    getNow={clockNow} onClose={() => { home(); void refresh() }} />
  else if (screen === 'history') page = historyLoaded
    ? <HistoryPage records={history} savedRecords={savedHistory} canCreate={!active && !pending} onBack={home} onCreate={() => setScreen('name')} />
    : <ScreenState title="약속 기록" description="기록을 불러오지 못했어요." onBack={home} onRetry={() => { void refresh() }} />
  else if (screen === 'name') page = <PromiseTaskNamePage initialValue={taskName} onBack={home} onNext={value => { setTaskName(value); setScreen('time') }} />
  else if (screen === 'time') page = <PromiseTimePage getNow={clockNow} initialValue={scheduledAt}
    onBack={value => { setError(''); setScheduledAt(value); setScreen('name') }}
    onNext={value => { setError(''); setScheduledAt(value); setScreen('tool') }} />
  else if (screen === 'tool') page = <PromiseToolPage initialValue={tool} onBack={() => setScreen('time')} onNext={value => { setTool(value); setScreen('confirm') }} />
  else if (screen === 'confirm') page = <PromiseConfirmPage taskName={taskName} scheduledAt={scheduledAt} tool={tool} errorMessage={pending ? '' : error} onBack={() => { setError(''); setScreen('tool') }} onSave={() => {
    void write(pending?.kind === 'create' ? pending : { kind: 'create', key: crypto.randomUUID(), payload: { taskTitle: taskName, toolType: tool === 'book' ? 'BOOK' : 'COMPUTER', scheduledAt } })
  }} />
  else if (screen === 'edit' && active) page = <PromiseTimePage getNow={clockNow} initialValue={active.scheduledAt} mode="edit" saveError={error} onBack={() => setScreen('manage')} onNext={value => { void write({ kind: 'reschedule', id: active.promiseId, version: active.version, scheduledAt: value }) }} />
  else if (screen === 'manage' && active) page = <PromiseManagePage promise={asDisplay(active)} onBack={home} onEditTime={() => setScreen('edit')}
    editBlockedReason={modifyAllowed ? '' : '시작 시간이 지나 수정·취소할 수 없어요.'}
    cancelBlockedReason={modifyAllowed ? '' : '시작 시간이 지나 수정·취소할 수 없어요.'}
    onCancelPromise={() => write({ kind: 'cancel', id: active.promiseId, version: active.version })} />
  else page = <div className="home-page"><AppHeader onSettings={() => setScreen('settings')} /><main className="live-home-main" aria-label="홈"><h1 className="visually-hidden">홈</h1>
    {pushNotice && <PushConnection state="ready" notice={pushNotice} retry={() => {}} dismiss={() => setPushNotice(null)} />}
    <TodayPromiseCard promise={active ? { taskName: active.taskTitle, isToday: new Date(Date.parse(active.scheduledAt) + 9 * 3600000).toISOString().slice(0, 10) === new Date(clockNow() + 9 * 3600000).toISOString().slice(0, 10), startTimeLabel: formatPromiseDateTime(Date.parse(active.scheduledAt)), toolLabel: active.toolType === 'BOOK' ? '책' : '컴퓨터' } : null}
      canCreate={!pending} canEdit={modifyAllowed} onEdit={() => setScreen('manage')} onCreate={() => { setTaskName(''); setScheduledAt(''); setTool('computer'); setScreen('name') }} />
    <LiveSelfReportEntry api={surveyApi} onOpen={setSurveyId} targets={reportTargets} />
    <WeeklyPromiseCard todayKey={new Date(clockNow() + 9 * 3600000).toISOString().slice(0, 10)}
      records={history} historyState={historyLoaded ? 'ready' : 'unavailable'} onViewAll={() => setScreen('history')} />
  </main>{active && <footer className="live-home-cta"><PromiseAuthPreview promise={asDisplay(active)} isLive onVerify={() => setScreen('photo')} /></footer>}</div>
  return <>
    <div ref={content} inert={busy} aria-busy={busy}>{participant?.onboardingCompleted && loaded && !closed
      ? <PermissionRecoveryGate key={currentToken}>{page}</PermissionRecoveryGate> : page}</div>
  </>
}
