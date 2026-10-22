import HomeStatePreview from './features/home/HomeStatePreview'
import AccessClosedPage from './features/home/AccessClosedPage'
import { interventionClosed } from './features/home/homeState'
import type { HomeSeed } from './features/home/HomeStatePreview'
import SelfReportPreview from './features/self-report/SelfReportPreview'
import type { Ref } from 'react'
import type { PreviewHomeControls } from './features/self-report/SelfReportPreview'
import { HistoryPage, SelfReportPage, ReportReminder } from './features/self-report/ReportScreens'
import { asReportTarget, reportKey } from './features/self-report/selfReportModel'
import type { ReportTarget } from './features/self-report/selfReportModel'
import { useSelfReportMock } from './features/self-report/useSelfReportMock'
import PhotoAuthMockFlow from './features/photo-auth/PhotoAuthMockFlow'
import PhotoAuthPreview from './features/photo-auth/PhotoAuthPreview'
import { usePhotoAuthMock } from './features/photo-auth/usePhotoAuthMock'
import { photoPromiseStatus, promiseKey } from './features/photo-auth/photoAuthModel'
import { getFinalPromiseRecord } from './features/promise/promiseHistory'
import type { FinalPromiseRecord } from './features/promise/promiseHistory'
import { useEffect, useImperativeHandle, useRef, useState } from 'react'
import AppHeader from './components/AppHeader'
import DoduHome from './DoduHome'

import PromiseTaskNamePage from './features/promise/PromiseTaskNamePage'
import PromiseTimePage from './features/promise/PromiseTimePage'
import PromiseToolPage from './features/promise/PromiseToolPage'
import PromiseConfirmPage from './features/promise/PromiseConfirmPage'
import PromiseManagePage from './features/promise/PromiseManagePage'
import { validateTaskName } from './features/promise/validateTaskName'
import { getInterventionEndError } from './features/promise/validateInterventionEnd'
import type {
  PromiseDraft,
  PreviewPromise,
  PromiseTool,
} from './features/promise/types'

import ParticipantEntryPage from './features/access/ParticipantEntryPage'
import OnboardingMockFlow from './features/onboarding/OnboardingMockFlow'

const MINUTE = 60_000
const HOUR = 60 * MINUTE

type ManagedPreviewPromise = PreviewPromise & {
  timeEditCount: number
  isRecreated: boolean
}

type CancelledPreviewPromise = Omit<ManagedPreviewPromise, 'status'> & {
  status: 'cancelled'
  cancelledAt: string
}

function getScheduledAt(promise: PreviewPromise) {
  return `${promise.date}T${promise.startTime}:00+09:00`
}

function getKstParts(timestamp: number) {
  const kst = new Date(timestamp + 9 * HOUR).toISOString()

  return {
    date: kst.slice(0, 10),
    startTime: kst.slice(11, 16),
  }
}

function formatDateLabel(date: string) {
  const [, month, day] = date.split('-')
  return `${Number(month)}월 ${Number(day)}일`
}

function getEditBlockedReason(
  promise: ManagedPreviewPromise,
  now: number,
) {
  const startsAt = Date.parse(getScheduledAt(promise))

  if (!Number.isFinite(startsAt)) {
    return '시작 시간을 확인할 수 없어 수정할 수 없어요.'
  }

  if (promise.status === 'verified') {
    return '인증이 완료된 약속은 수정할 수 없어요.'
  }

  if (now >= startsAt) {
    return '시작 시간이 지나 수정할 수 없어요.'
  }

  // 재생성한 약속은 수정 횟수가 남아 있어도 수정할 수 없다.
  if (promise.isRecreated) {
    return '취소 후 다시 만든 약속은 시작 시간을 수정할 수 없어요.'
  }

  if (promise.timeEditCount >= 1) {
    return '시작 시간을 이미 1번 수정했어요.'
  }

  return ''
}

function getCancelBlockedReason(
  promise: ManagedPreviewPromise,
  now: number,
) {
  const startsAt = Date.parse(getScheduledAt(promise))

  if (!Number.isFinite(startsAt)) {
    return '시작 시간을 확인할 수 없어 취소할 수 없어요.'
  }

  if (promise.status === 'verified') {
    return '인증이 완료된 약속은 취소할 수 없어요.'
  }

  if (now >= startsAt) {
    return '시작 시간이 지나 취소할 수 없어요.'
  }

  // 재생성 여부와 수정 횟수는 시작 전 취소를 막지 않는다.
  return ''
}

export default function App() {
  // 개발용 종료 경계 확인값이다. 실제 API 연결 시 운영 데이터의 종료시각으로 대체한다.
  const mockEnd = import.meta.env.DEV
    ? new URLSearchParams(window.location.search).get('mockInterventionEndsAt')
    : null
  const interventionEndsAt = mockEnd === null ? undefined : Date.parse(mockEnd)
  const preview = import.meta.env.DEV
    ? new URLSearchParams(window.location.search).get('preview')
    : null

  if (preview === 'home') return <HomeStatePreview renderHome={seed => <HomePrototype {...seed} />} />
  if (preview === 'access-closed') return <AccessClosedPage />

  if (preview === 'self-report') {
    return <SelfReportPreview renderHome={(records, controls) => (
      <HomePrototype initialPastResults={records} previewControls={controls}
        interventionEndsAt={interventionEndsAt} />
    )} />
  }

  if (preview === 'photo-auth') return <PhotoAuthPreview />

  if (preview === 'entry') {
    return <ParticipantEntryPreview />
  }

  if (
    preview === 'onboarding' ||
    preview === 'onboarding-install' ||
    preview === 'onboarding-notification' ||
    preview === 'onboarding-camera' ||
    preview === 'onboarding-camera-complete'
  ) {
    const initialStep = preview === 'onboarding-install' ? 'install'
      : preview === 'onboarding-notification' ? 'notification'
        : preview === 'onboarding-camera' ? 'camera'
          : preview === 'onboarding-camera-complete' ? 'complete' : 'intro'

    return (
      <OnboardingMockFlow
        key={preview}
        initialStep={initialStep}
        onClose={() => { window.location.search = '?preview=entry' }}
        onComplete={() => { window.location.search = '' }}
      />
    )
  }

  if (preview === 'promise-name' || preview === 'promise-time') {
    return (
      <HomePrototype
        key={preview}
        initialScreen={preview === 'promise-name' ? 'write' : 'time'}
        interventionEndsAt={interventionEndsAt}
      />
    )
  }

  return <HomePrototype interventionEndsAt={interventionEndsAt} />
}

// 개발 환경의 화면 분기 확인용이다. 실제 인증이나 세션 저장은 실행하지 않는다.
function ParticipantEntryPreview() {
  const [screen, setScreen] = useState<'entry' | 'closed'>('entry')
  const [errorMessage, setErrorMessage] = useState('')
  const closedTitleRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    if (screen === 'closed') {
      closedTitleRef.current?.focus()
    } else {
      document.getElementById('participant-code')?.focus()
    }
  }, [screen])

  function handleSubmit(code: string) {
    // 실제 빌드에서는 테스트 코드로 진입할 수 없게 한다.
    if (!import.meta.env.DEV) return

    setErrorMessage('')

    // 참가자 코드와 토큰 원문은 로그나 저장소에 남기지 않는다.
    switch (code.trim()) {
      case 'DEMO-NEW':
        window.location.search = '?preview=onboarding'
        return
      case 'DEMO-RETURN':
        window.location.search = ''
        return
      case 'DEMO-CLOSED':
        setScreen('closed')
        return
      default:
        setErrorMessage(
          '코드를 다시 확인해 주세요. 유효하지 않거나 만료된 코드예요.',
        )
    }
  }

  // 기존 임시 종료 문구 대신 S13 핸드오프 화면을 사용한다.
  if (screen === 'closed') return <AccessClosedPage />

  return (
    <ParticipantEntryPage
      errorMessage={errorMessage}
      onClearError={() => setErrorMessage('')}
      onSubmit={handleSubmit}
    />
  )
}

type PreviewScreen =
  | 'home'
  | 'write'
  | 'time'
  | 'tool'
  | 'confirm'
  | 'manage'
  | 'edit-time'
  | 'photo-auth'
  | 'history'

type HomePrototypeProps = HomeSeed & {
  initialPastResults?: FinalPromiseRecord[]
  previewControls?: Ref<PreviewHomeControls>
  initialScreen?: PreviewScreen
  interventionEndsAt?: number
}

function HomePrototype({
  initialScreen = 'home',
  initialPastResults = [],
  previewControls,
  interventionEndsAt,
  initialPromise = null,
  initialPhotoSession = null,
  accessClosed = false,
}: HomePrototypeProps) {
  const [screen, setScreen] = useState<PreviewScreen>(initialScreen)

  const [draft, setDraft] = useState<PromiseDraft>({
    taskName: '',
    startTime: '',
    tool: 'computer',
  })

  // 날짜를 포함한 KST 시각을 보관한다.
  const [scheduledAt, setScheduledAt] = useState('')
  const [selectedTool, setSelectedTool] = useState<PromiseTool | null>(null)
  const [confirmError, setConfirmError] = useState('')
  const [storedPromise, setPromise] = useState<ManagedPreviewPromise | null>(() => initialPromise
    ? { ...initialPromise, timeEditCount: 0, isRecreated: false } : null)
  const photo = usePhotoAuthMock(initialPhotoSession)
  const promise: ManagedPreviewPromise | null = storedPromise && photo.session &&
    promiseKey(storedPromise) === promiseKey(photo.session.promise)
    ? { ...storedPromise, status: photoPromiseStatus(photo.session) } : storedPromise
  const [recoveryDate, setRecoveryDate] = useState<string | null>(null)
  const [technicalRecoveryUsedDates, setTechnicalRecoveryUsedDates] = useState<Set<string>>(() => new Set())
  const technicalClosedDatesRef = useRef<Set<string>>(new Set())
  const [pastResults, setPastResults] = useState<FinalPromiseRecord[]>(() => [...initialPastResults])
  const [editError, setEditError] = useState('')
  const [homeNotice, setHomeNotice] = useState('')
  const [now, setNow] = useState(Date.now)
  const [archivedReportTargets, setArchivedReportTargets] = useState<ReportTarget[]>([])
  const currentReportTarget = asReportTarget(promise, now)
  const report = useSelfReportMock(
    currentReportTarget ? [...archivedReportTargets, currentReportTarget] : archivedReportTargets,
    now, screen === 'home' && !accessClosed,
  )

  // 취소 기록은 인증 결과와 구분하여 메모리에만 보관한다.
  const [cancelledPromises, setCancelledPromises] = useState<CancelledPreviewPromise[]>([])

  // 재생성에 사용한 날짜는 이후 취소해도 지우지 않는다.
  const recreationUsedDatesRef = useRef<Set<string>>(new Set())

  // 같은 화면에서 저장이나 취소가 중복 실행되지 않게 한다.
  const creationInProgressRef = useRef(false)
  const cancellationInProgressRef = useRef(false)

  useEffect(() => {
    const updateTime = () => setNow(Date.now())
    const timer = window.setInterval(updateTime, 1000)

    window.addEventListener('focus', updateTime)
    document.addEventListener('visibilitychange', updateTime)

    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', updateTime)
      document.removeEventListener('visibilitychange', updateTime)
    }
  }, [])

  useEffect(() => {
    if (screen === 'home' && !report.activeTarget && !report.reminderTarget && !accessClosed) {
      document.querySelector<HTMLElement>('.home-page')?.focus()
    }
  }, [screen, report.activeTarget, report.reminderTarget, accessClosed])

  const editBlockedReason = promise
    ? getEditBlockedReason(promise, now)
    : ''

  const cancelBlockedReason = promise
    ? getCancelBlockedReason(promise, now)
    : ''

  function getCancelledCount(date: string) {
    return cancelledPromises.filter(
      (cancelled) => cancelled.date === date,
    ).length
  }

  function isFinalizedDate(date: string) {
    return pastResults.some(record => record.date === date) ||
      Boolean(promise?.date === date && getFinalPromiseRecord(promise, Date.now()))
  }

  function getDateBlockedReason(date: string) {
    // 기술복구는 취소 후 재생성과 구분한다. 가능 여부는 개발용 서버 응답 가정이다.
    if (date === recoveryDate && !technicalRecoveryUsedDates.has(date)) return ''
    if (technicalClosedDatesRef.current.has(date) || (promise?.date === date && promise.status === 'techFailed')) {
      return date === getKstParts(Date.now()).date
        ? '이미 오늘의 약속 만들기 횟수를 모두 사용했어요'
        : `${formatDateLabel(date)}의 약속 만들기 횟수를 모두 사용했어요`
    }
    // 종료된 약속이 있는 날짜에는 새 약속을 중복 생성하지 않는다.
    if (isFinalizedDate(date)) {
      return date === getKstParts(Date.now()).date
        ? '이미 오늘의 약속 만들기 횟수를 모두 사용했어요'
        : `${formatDateLabel(date)}의 약속 만들기 횟수를 모두 사용했어요`
    }
    const cancelledCount = getCancelledCount(date)

    if (
      recreationUsedDatesRef.current.has(date) ||
      cancelledCount >= 2
    ) {
      return `${formatDateLabel(date)}에는 이미 약속을 한 번 다시 만들었어요. 다른 날을 선택해 주세요.`
    }

    return ''
  }

  function openCreator() {
    if (accessClosed || interventionClosed(Date.now(), interventionEndsAt)) return
    setRecoveryDate(null)
    // 현재 미리보기는 저장된 약속 한 개를 관리한다.
    if (promise && promise.status !== 'techFailed' && !getFinalPromiseRecord(promise, Date.now())) {
      setNow(Date.now())
      setScreen('manage')
      return
    }

    creationInProgressRef.current = false

    setDraft({
      taskName: '',
      startTime: '',
      tool: 'computer',
    })
    setScheduledAt('')
    setSelectedTool(null)
    setConfirmError('')
    setHomeNotice('')
    setScreen('write')
  }

  function openManager() {
    if (!promise) return

    setNow(Date.now())
    setConfirmError('')
    setEditError('')
    setScreen('manage')
  }

  function openTimeEditor() {
    if (accessClosed || interventionClosed(Date.now(), interventionEndsAt)) return
    if (!promise) return

    const current = Date.now()
    setNow(current)

    // 수정 화면에 들어갈 때 재생성 여부도 검사한다.
    if (getEditBlockedReason(promise, current)) return

    setEditError('')
    setScreen('edit-time')
  }

  function leaveEditor() {
    // 첫 입력 단계에서 나가면 작성 중인 내용만 비운다.
    // 취소 기록과 재생성 사용 기록은 유지한다.
    setDraft({
      taskName: '',
      startTime: '',
      tool: 'computer',
    })
    setScheduledAt('')
    setSelectedTool(null)
    setConfirmError('')
    setScreen('home')
  }

  // 미리보기 전용: 실제 서버 저장·예약·알림은 실행하지 않는다.
  // API 연결 시 서버 저장 성공을 확인한 뒤에만 홈으로 이동한다.
  function savePreview() {
    if (accessClosed || interventionClosed(Date.now(), interventionEndsAt)) return
    if (creationInProgressRef.current) return

    if (promise && promise.status !== 'techFailed' && !getFinalPromiseRecord(promise, Date.now())) {
      setConfirmError('이미 저장된 약속이 있어요.')
      return
    }

    // 입력 화면과 동일한 기준으로 최종 저장 전에 다시 검사한다.
    const nameValidation = validateTaskName(draft.taskName)

    if (!nameValidation.isValid) {
      setConfirmError(nameValidation.errorMessage)
      return
    }

    if (!selectedTool) {
      setConfirmError('인증 도구를 선택해 주세요.')
      return
    }

    const timestamp = Date.parse(scheduledAt)
    const current = Date.now()

    if (
      !Number.isFinite(timestamp) ||
      timestamp < current + 5 * MINUTE ||
      timestamp > current + 72 * HOUR
    ) {
      setConfirmError(
        '시간이 지났거나 선택 가능한 범위를 벗어났어요. 이전 단계에서 날짜와 시간을 다시 선택해 주세요.',
      )
      return
    }

    const endError = getInterventionEndError(timestamp, interventionEndsAt)
    if (endError) {
      setConfirmError(endError)
      return
    }

    // 선택한 예정 날짜를 KST 기준으로 계산한다.
    const { date, startTime } = getKstParts(timestamp)
    const dateBlockedReason = getDateBlockedReason(date)

    if (dateBlockedReason) {
      setConfirmError(dateBlockedReason)
      return
    }

    // 해당 날짜의 취소 기록이 있으면 사용자 재생성 약속이다.
    const isTechnicalRecovery = date === recoveryDate
    const isRecreated = !isTechnicalRecovery && getCancelledCount(date) > 0

    const nextDraft: PromiseDraft = {
      taskName: nameValidation.taskName,
      startTime,
      tool: selectedTool,
    }

    const nextPromise: ManagedPreviewPromise = {
      ...nextDraft,
      date,
      status: 'pending',
      timeEditCount: 0,
      isRecreated,
    }

    // 모든 검사를 통과한 저장만 재생성 횟수를 사용한다.
    creationInProgressRef.current = true

    if (isRecreated) {
      recreationUsedDatesRef.current.add(date)
    }

    // 새 약속으로 교체하기 전에 이전 최종 결과를 한 번만 보관한다.
    const previousReportTarget = asReportTarget(promise, current)
    if (previousReportTarget) {
      setArchivedReportTargets(previous => previous.some(target => reportKey(target) === reportKey(previousReportTarget))
        ? previous : [...previous, previousReportTarget])
    }
    const previousResult = getFinalPromiseRecord(promise, current)
    if (previousResult) {
      setPastResults(previous => previous.some(record => record.date === previousResult.date)
        ? previous : [...previous, previousResult])
    }
    if (promise?.status === 'techFailed') technicalClosedDatesRef.current.add(promise.date)
    if (isTechnicalRecovery) setTechnicalRecoveryUsedDates(previous => new Set([...previous, date]))
    setRecoveryDate(null)
    photo.dispatch({ type: 'clear' })
    setDraft(nextDraft)
    setPromise(nextPromise)
    cancellationInProgressRef.current = false
    setConfirmError('')
    setHomeNotice('')
    setScreen('home')
  }

  // 시간 수정은 기존 약속의 날짜·시간만 바꾼다.
  function saveEditedTime(value: string) {
    if (accessClosed || interventionClosed(Date.now(), interventionEndsAt)) return
    if (!promise) return

    const current = Date.now()
    setNow(current)

    // 저장 시점에도 재생성 여부와 원래 시작 시간을 다시 확인한다.
    const blockedReason = getEditBlockedReason(promise, current)

    if (blockedReason) {
      setEditError(blockedReason)
      return
    }

    const timestamp = Date.parse(value)
    const originalTimestamp = Date.parse(getScheduledAt(promise))

    if (timestamp === originalTimestamp) {
      setEditError('변경할 날짜와 시간을 선택해 주세요.')
      return
    }

    if (
      !Number.isFinite(timestamp) ||
      timestamp < current + 5 * MINUTE ||
      timestamp > current + 72 * HOUR
    ) {
      setEditError('현재 시각 +5분 ~ +72시간 사이로 다시 선택해 주세요.')
      return
    }

    const endError = getInterventionEndError(timestamp, interventionEndsAt)
    if (endError) {
      setEditError(endError)
      return
    }

    const { date, startTime } = getKstParts(timestamp)

    // 다른 날짜에서 수정해 들어오는 경우에도 해당 날짜의 제한을 확인한다.
    // 같은 날짜 안에서 시간만 바꾸는 것은 새로운 생성이 아니다.
    if (date !== promise.date) {
      const dateBlockedReason = getDateBlockedReason(date)

      if (dateBlockedReason) {
        setEditError(dateBlockedReason)
        return
      }
    }

    setPromise((previous) => {
      if (!previous) return previous

      // 중복 저장이나 상태 변경으로 수정 제한을 우회하지 않게 한다.
      if (
        previous !== promise ||
        getEditBlockedReason(previous, current)
      ) {
        return previous
      }

      return {
        ...previous,
        date,
        startTime,
        timeEditCount: previous.timeEditCount + 1,
      }
    })

    setEditError('')
    setScreen('manage')
  }

  function cancelPreviewPromise(): string | undefined {
    if (!promise) {
      return '취소할 약속이 없어요.'
    }

    if (cancellationInProgressRef.current) {
      return '이미 취소 처리 중이에요.'
    }

    const current = Date.now()
    setNow(current)

    // 모달을 열어둔 사이 시작 시간이 지났는지도 다시 확인한다.
    const blockedReason = getCancelBlockedReason(promise, current)

    if (blockedReason) {
      return blockedReason
    }

    cancellationInProgressRef.current = true

    // 취소 당시의 최종 예정 날짜와 재생성 여부를 기록한다.
    setCancelledPromises((previous) => [
      ...previous,
      {
        ...promise,
        status: 'cancelled',
        cancelledAt: new Date(current).toISOString(),
      },
    ])

    // 재생성 사용 날짜는 취소해도 삭제하지 않는다.
    setPromise(null)
    setDraft({
      taskName: '',
      startTime: '',
      tool: 'computer',
    })
    setScheduledAt('')
    setSelectedTool(null)
    setConfirmError('')
    setEditError('')
    setHomeNotice('약속을 취소했어요.')
    setScreen('home')

    return undefined
  }

  /* 기존 즉시 인증 mock은 사진 확인·판정 흐름으로 대체한다.
  // 화면 확인용 상태 변경이다. 실제 사진 인증이나 서버 저장은 실행하지 않는다.
  function verifyPreviewPromise() {
    const current = Date.now()

    setPromise((previous) => {
      if (!previous || previous.status !== 'pending') return previous

      const startsAt = Date.parse(getScheduledAt(previous))

      if (
        !Number.isFinite(startsAt) ||
        current < startsAt ||
        current >= startsAt + 30 * MINUTE
      ) {
        return previous
      }

      return { ...previous, status: 'verified' }
    })
  }

  */
  function verifyPreviewPromise() {
    if (!import.meta.env.DEV || !promise || accessClosed) return
    if (interventionClosed(Date.now(), interventionEndsAt) &&
      !['evaluating', 'verified', 'unverified', 'techFailed'].includes(promise.status)) return
    photo.dispatch({ type: 'open', promise })
    setScreen('photo-auth')
  }

  function openTechnicalRecovery() {
    if (accessClosed || interventionClosed(Date.now(), interventionEndsAt)) return
    if (!promise || photo.session?.stage !== 'tech-failed-recoverable') return
    if (technicalRecoveryUsedDates.has(promise.date)) {
      return
    }
    openCreator()
    setRecoveryDate(promise.date)
  }

  // 미리보기 화면도 같은 홈 인스턴스를 유지하며 이벤트에서 이동한다.
  useImperativeHandle(previewControls, () => ({
    home: () => setScreen('home'),
    create: openCreator,
  }))

  if (accessClosed) return <AccessClosedPage />

  if (report.activeTarget) {
    return <SelfReportPage key={reportKey(report.activeTarget)} target={report.activeTarget}
      onClose={report.dismiss} onSkip={() => report.skip(report.activeTarget!)} onSubmit={report.submit} />
  }
  if (screen === 'history') {
    const currentResult = getFinalPromiseRecord(promise, now)
    return <HistoryPage records={currentResult ? [...pastResults, currentResult] : pastResults}
      onBack={() => setScreen('home')} onCreate={openCreator} />
  }

  if (screen === 'photo-auth' && photo.session) {
    return <PhotoAuthMockFlow session={photo.session} dispatch={photo.dispatch}
      interventionEndsAt={interventionEndsAt}
      recoveryUsed={technicalRecoveryUsedDates.has(photo.session.promise.date)}
      onClose={() => setScreen('home')} onRecover={openTechnicalRecovery} />
  }

  if (screen === 'write') {
    return (
      <PromiseTaskNamePage
        initialValue={draft.taskName}
        onBack={leaveEditor}
        onNext={(taskName) => {
          setDraft((previous) => ({
            ...previous,
            taskName,
          }))
          setConfirmError('')
          setScreen('time')
        }}
      />
    )
  }

  // 뒤로 이동해도 작업명과 날짜·시간 선택값을 유지한다.
  if (screen === 'time') {
    return (
      <PromiseTimePage
        key="create-time"
        initialValue={scheduledAt}
        getDateBlockedReason={getDateBlockedReason}
        isDateUnavailable={isFinalizedDate}
        interventionEndsAt={interventionEndsAt}
        onBack={(value) => {
          setScheduledAt(value)
          setScreen('write')
        }}
        onNext={(value) => {
          setScheduledAt(value)
          setConfirmError('')
          setScreen('tool')
        }}
      />
    )
  }

  if (screen === 'tool') {
    return (
      <PromiseToolPage
        initialValue={selectedTool}
        onBack={(tool) => {
          setSelectedTool(tool)
          setScreen('time')
        }}
        onNext={(tool) => {
          setSelectedTool(tool)
          setDraft((previous) => ({
            ...previous,
            tool,
          }))
          setConfirmError('')
          setScreen('confirm')
        }}
      />
    )
  }

  if (screen === 'confirm') {
    const timestamp = Date.parse(scheduledAt)

    // 저장 전 확인 화면에도 재생성 여부에 맞는 안내를 전달한다.
    const isRecreated =
      Number.isFinite(timestamp) &&
      getCancelledCount(getKstParts(timestamp).date) > 0

    return (
      <PromiseConfirmPage
        taskName={draft.taskName}
        scheduledAt={scheduledAt}
        tool={selectedTool ?? draft.tool}
        isRecreated={isRecreated}
        errorMessage={confirmError}
        onBack={() => {
          setConfirmError('')
          setScreen('tool')
        }}
        onSave={savePreview}
      />
    )
  }

  if (screen === 'manage' && promise) {
    return (
      <PromiseManagePage
        promise={promise}
        editBlockedReason={editBlockedReason}
        cancelBlockedReason={cancelBlockedReason}
        onBack={() => setScreen('home')}
        onEditTime={openTimeEditor}
        onCancelPromise={cancelPreviewPromise}
      />
    )
  }

  if (screen === 'edit-time' && promise) {
    return (
      <PromiseTimePage
        key={`edit-time-${getScheduledAt(promise)}`}
        initialValue={getScheduledAt(promise)}
        mode="edit"
        interventionEndsAt={interventionEndsAt}
        blockedReason={editBlockedReason}
        saveError={editError}
        onBack={() => {
          // 저장하지 않고 나가면 기존 시간과 수정 횟수를 유지한다.
          setEditError('')
          setScreen('manage')
        }}
        onNext={saveEditedTime}
      />
    )
  }

  return (
    <>
    <main className="home-page" aria-label="홈" tabIndex={-1} inert={Boolean(report.reminderTarget)}>
      <AppHeader />

      {screen === 'home' && (
        <>
          {homeNotice && (
            <p role="status">{homeNotice}</p>
          )}

          <DoduHome
            promise={promise}
            pastResults={pastResults}
            interventionEndsAt={interventionEndsAt}
            onCreate={openCreator}
            onEdit={openManager}
            onViewAll={() => setScreen('history')}
            onVerify={verifyPreviewPromise}
          />
        </>
      )}
    </main>
    {report.reminderTarget && <ReportReminder target={report.reminderTarget}
      onWrite={() => report.open(report.reminderTarget!)}
      onSkip={() => report.skip(report.reminderTarget!)} onDismiss={report.dismiss} />}
    </>
  )
}
