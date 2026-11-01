import { focusScreenHeading } from '../promise/focusScreenHeading'
import LiveOnboardingFlow from '../onboarding/LiveOnboardingFlow'
import { useEffect, useRef, useState } from 'react'
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
import '../home/home-states.css'

type Screen = 'home' | 'name' | 'time' | 'tool' | 'confirm' | 'manage' | 'edit'
function asDisplay(row: PromiseResponse): PreviewPromise {
  const kst = new Date(Date.parse(row.scheduledAt) + 9 * 3600000).toISOString()
  // 표시 컴포넌트의 입력 형식이다. 실제 인증 상태를 계산하거나 저장하지 않는다.
  return { taskName: row.taskTitle, date: kst.slice(0, 10), startTime: kst.slice(11, 16),
    tool: row.toolType === 'BOOK' ? 'book' : 'computer', status: 'pending' }
}
function errorMessage(error: unknown) {
  if (error instanceof ParticipantPromiseApiError) {
    if (error.code === 'ACCESS_CODE_INVALID') return '코드를 다시 확인해 주세요. 유효하지 않거나 만료된 코드예요.'
    if (error.code === 'PROMISE_NOT_MODIFIABLE') return '시작 시간이 지나 수정·취소할 수 없어요.'
    if (error.code === 'PROMISE_RESCHEDULE_LIMIT_REACHED') return '시작 시간을 이미 1번 수정했어요.'
    if (error.code === 'PROMISE_SCHEDULE_TOO_FAR') return '지금부터 72시간 이내로 정해 주세요.'
  }
  return '일시적인 문제가 생겼어요. 잠시 후 다시 시도해 주세요.'
}

export default function LiveParticipantApp() {
  const [session] = useState(() => createLiveSession(sessionStorage, localStorage))
  const [api] = useState(() => createParticipantPromiseApi({ getToken: session.token }))
  const [participant, setParticipant] = useState<ParticipantState | null>(null)
  const [active, setActive] = useState<PromiseResponse | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [onboardingDismissed, setOnboardingDismissed] = useState(false)
  const [closed, setClosed] = useState(false)
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
  const now = serverClock ? serverClock.value + performance.now() - serverClock.measured : null

  function fail(reason: unknown) {
    if (reason instanceof DOMException && reason.name === 'AbortError') return
    if (reason instanceof ParticipantPromiseApiError && reason.httpStatus === 401) {
      setOnboardingDismissed(false); session.clear(); setParticipant(null); setActive(null); setLoaded(false); setPending(null)
    }
    if (reason instanceof ParticipantPromiseApiError && reason.code === 'STUDY_PERIOD_ENDED') setClosed(true)
    setError(errorMessage(reason))
  }
  function mark(value: PendingWrite | null) { session.mark(value); setPending(value) }
  async function refresh() {
    if (running.current || !session.token()) return
    const version = ++epoch.current
    reader.current?.abort()
    const abort = new AbortController(); reader.current = abort
    setBusy(true); setError('')
    try {
      const me = await api.me(abort.signal)
      const promise = await api.active(abort.signal)
      if (!mounted.current || version !== epoch.current) return
      setParticipant(me.data); setActive(promise.data); setLoaded(true)
      if (me.serverTime) setServerClock({ value: Date.parse(me.serverTime), measured: performance.now() })
      const unresolved = session.pending()
      // 수정 응답을 잃은 경우 같은 약속의 갱신 버전·시간을 확인한다.
      if (unresolved?.kind === 'reschedule' && promise.data?.promiseId === unresolved.id &&
          promise.data.version > unresolved.version && Date.parse(promise.data.scheduledAt) === Date.parse(unresolved.scheduledAt)) mark(null)
      // 활성 목록에서 사라진 것은 취소 성공이라고 표시하지 않고 현재 목록만 갱신한다.
      if (unresolved?.kind === 'cancel' && promise.data?.promiseId !== unresolved.id) mark(null)
    } catch (reason) { if (mounted.current && version === epoch.current) fail(reason) }
    finally { if (mounted.current && version === epoch.current) setBusy(false) }
  }
  useEffect(() => {
    mounted.current = true
    void refresh()
    const timer = setInterval(() => tick(value => value + 1), 1000)
    const focus = () => { if (document.visibilityState === 'visible') void refresh() }
    window.addEventListener('focus', focus)
    return () => { mounted.current = false; epoch.current++; reader.current?.abort(); clearInterval(timer); window.removeEventListener('focus', focus) }
    // 세션·API 인스턴스는 이 앱이 마운트된 동안 유지된다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function login(code: string) {
    if (running.current) return
    running.current = true; const version = ++epoch.current
    setBusy(true); setError(''); reader.current?.abort()
    try {
      const result = await api.enter(code, session.device())
      if (!mounted.current || version !== epoch.current) return
      setOnboardingDismissed(false); session.enter(result.data.sessionToken)
      setParticipant(result.data); setScreen('home'); setPending(null)
    } catch (reason) { if (mounted.current && version === epoch.current) fail(reason) }
    finally {
      running.current = false
      if (mounted.current && version === epoch.current) { setBusy(false); if (session.token()) void refresh() }
    }
  }
  async function write(operation: PendingWrite): Promise<string | undefined> {
    if (running.current) return undefined
    if (pending && JSON.stringify(pending) !== JSON.stringify(operation)) return errorMessage(null)
    running.current = true; const version = ++epoch.current
    reader.current?.abort(); setBusy(true); setError('')
    let succeeded = false
    try {
      mark(operation)
      if (operation.kind === 'create') await api.prepareCreate(operation.payload, operation.key).send()
      else if (operation.kind === 'reschedule') await api.reschedule(operation.id, operation.scheduledAt, operation.version)
      else await api.cancel(operation.id, operation.version)
      if (!mounted.current || version !== epoch.current) return undefined
      mark(null); setScreen('home'); setLoaded(false); succeeded = true
    } catch (reason) {
      if (!mounted.current || version !== epoch.current) return undefined
      if (reason instanceof ParticipantPromiseApiError && !reason.outcomeUnknown) mark(null)
      fail(reason)
      return errorMessage(reason)
    } finally {
      running.current = false
      if (mounted.current && version === epoch.current) { setBusy(false); if (succeeded && session.token()) void refresh() }
    }
  }
  const clockNow = () => serverClock ? serverClock.value + performance.now() - serverClock.measured : Date.now()
  const home = () => { setError(''); setScreen('home') }
  const modifyAllowed = Boolean(active && now !== null && Date.parse(active.scheduledAt) > now && !pending)
  // 실제 화면 전환 때만 제목으로 이동한다. 시계 갱신과 서버 재조회는 포커스를 빼앗지 않는다.
  const visibleScreen = closed ? 'closed'
    : !participant && !session.token() ? 'entry'
    : !loaded ? 'loading'
    : participant && !participant.onboardingCompleted && !onboardingDismissed ? 'onboarding'
    : screen
  useEffect(() => {
    if (busy || visibleScreen === 'loading' || lastFocusedScreen.current === visibleScreen) return
    const frame = requestAnimationFrame(() => {
      focusScreenHeading(content.current)
      lastFocusedScreen.current = visibleScreen
    })
    return () => cancelAnimationFrame(frame)
  }, [visibleScreen, busy])
  let page
  if (closed) page = <AccessClosedPage />
  else if (!participant && !session.token()) page = <ParticipantEntryPage onSubmit={code => { void login(code) }} isSubmitting={busy} errorMessage={error} onClearError={() => setError('')} />
  else if (!loaded) page = <main aria-label="홈" aria-busy={busy}><h1 className="visually-hidden">홈</h1><p role="status">확인 중…</p></main>
  else if (participant && !participant.onboardingCompleted && !onboardingDismissed) page = <LiveOnboardingFlow onClose={() => { setOnboardingDismissed(true); setScreen('home') }} />
  else if (screen === 'name') page = <PromiseTaskNamePage initialValue={taskName} onBack={home} onNext={value => { setTaskName(value); setScreen('time') }} />
  else if (screen === 'time') page = <PromiseTimePage getNow={clockNow} initialValue={scheduledAt} onBack={value => { setScheduledAt(value); setScreen('name') }} onNext={value => { setScheduledAt(value); setScreen('tool') }} />
  else if (screen === 'tool') page = <PromiseToolPage initialValue={tool} onBack={() => setScreen('time')} onNext={value => { setTool(value); setScreen('confirm') }} />
  else if (screen === 'confirm') page = <PromiseConfirmPage taskName={taskName} scheduledAt={scheduledAt} tool={tool} errorMessage={error} onBack={() => setScreen('tool')} onSave={() => {
    void write(pending?.kind === 'create' ? pending : { kind: 'create', key: crypto.randomUUID(), payload: { taskTitle: taskName, toolType: tool === 'book' ? 'BOOK' : 'COMPUTER', scheduledAt } })
  }} />
  else if (screen === 'edit' && active) page = <PromiseTimePage getNow={clockNow} initialValue={active.scheduledAt} mode="edit" saveError={error} onBack={() => setScreen('manage')} onNext={value => { void write({ kind: 'reschedule', id: active.promiseId, version: active.version, scheduledAt: value }) }} />
  else if (screen === 'manage' && active) page = <PromiseManagePage promise={asDisplay(active)} onBack={home} onEditTime={() => setScreen('edit')}
    editBlockedReason={modifyAllowed ? '' : '시작 시간이 지나 수정·취소할 수 없어요.'}
    cancelBlockedReason={modifyAllowed ? '' : '시작 시간이 지나 수정·취소할 수 없어요.'}
    onCancelPromise={() => write({ kind: 'cancel', id: active.promiseId, version: active.version })} />
  else page = <div className="home-page"><AppHeader /><main className="live-home-main" aria-label="홈"><h1 className="visually-hidden">홈</h1>
    <TodayPromiseCard promise={active ? { taskName: active.taskTitle, isToday: new Date(Date.parse(active.scheduledAt) + 9 * 3600000).toISOString().slice(0, 10) === new Date(clockNow() + 9 * 3600000).toISOString().slice(0, 10), startTimeLabel: formatPromiseDateTime(Date.parse(active.scheduledAt)), toolLabel: active.toolType === 'BOOK' ? '책' : '컴퓨터' } : null}
      canCreate={!pending} canEdit={modifyAllowed} onEdit={() => setScreen('manage')} onCreate={() => { setTaskName(''); setScheduledAt(''); setTool('computer'); setScreen('name') }} />
    {/* 이력 API 연결 전에는 빈 배열을 조회된 기록으로 취급하지 않는다. */}
    <WeeklyPromiseCard todayKey={new Date(clockNow() + 9 * 3600000).toISOString().slice(0, 10)} historyState="unavailable" />
  </main></div>
  return <>
    {(pending || (error && Boolean(session.token()))) && <aside style={{ padding: 16 }} aria-label="요청 상태">
      {pending && <p role="status">저장 결과 확인이 필요합니다. 다른 저장은 잠시 중단됩니다.</p>}
      {error && <p role="alert">{error}</p>}
      {session.token() && <button type="button" disabled={busy} onClick={() => { void refresh() }}>서버 상태 다시 확인</button>}
      {pending?.kind === 'create' && <button type="button" disabled={busy} onClick={() => { void write(pending) }}>같은 저장 요청 다시 확인</button>}
    </aside>}
    <div ref={content} inert={busy} aria-busy={busy}>{page}</div>
  </>
}
