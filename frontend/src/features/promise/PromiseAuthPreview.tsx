import { interventionClosed } from '../home/homeState'
import { formatAuthWindow } from './promiseHistory'
import { useEffect, useState } from 'react'
import Button from '../../components/Button'
import cameraIcon from '../../assets/icons/camera-filled.svg'
import type { PreviewPromise } from './types'

type PromiseAuthPreviewProps = {
  promise: PreviewPromise
  onVerify: () => void
  interventionEndsAt?: number
}

const AUTH_DURATION_MS = 30 * 60 * 1000

// 화면 확인용이며 실제 사진 인증이나 서버 저장은 실행하지 않는다.
export default function PromiseAuthPreview({
  promise,
  onVerify,
  interventionEndsAt,
}: PromiseAuthPreviewProps) {
  const [now, setNow] = useState(() => Date.now())

  const startsAt = Date.parse(
    `${promise.date}T${promise.startTime}:00+09:00`,
  )
  const endsAt = startsAt + AUTH_DURATION_MS
  const hasValidTime = Number.isFinite(startsAt)
  const hasReachedStart = hasValidTime && now >= startsAt
  const hasExpired = hasValidTime && now >= endsAt
  const isVerified = promise.status === 'verified'

  // 최종 인증 미완료 상태는 현재 시각과 관계없이 종료 상태로 표시한다.
  const isUnverified = promise.status === 'unverified'
  const showAuthEnded = isUnverified || hasExpired

  const canContinue =
    !interventionClosed(now, interventionEndsAt) ||
    ['evaluating', 'verified', 'unverified', 'techFailed'].includes(
      promise.status,
    )

  const canVerify =
    canContinue &&
    import.meta.env.DEV &&
    hasReachedStart &&
    (
      (!hasExpired && promise.status === 'pending') ||
      ['evaluating', 'techError', 'techFailed', 'verified', 'unverified'].includes(
        promise.status,
      )
    )

  useEffect(() => {
    const updateTime = () => setNow(Date.now())
    const intervalId = window.setInterval(updateTime, 1000)

    window.addEventListener('focus', updateTime)
    document.addEventListener('visibilitychange', updateTime)

    return () => {
      window.clearInterval(intervalId)
      window.removeEventListener('focus', updateTime)
      document.removeEventListener('visibilitychange', updateTime)
    }
  }, [])

  function handleVerify() {
    // 화면 갱신 사이에 인증 시간이 끝나는 경우에도 실행하지 않는다.
    const currentTime = Date.now()
    setNow(currentTime)

    if (
      (
        interventionClosed(currentTime, interventionEndsAt) &&
        !['evaluating', 'verified', 'unverified', 'techFailed'].includes(
          promise.status,
        )
      ) ||
      !hasValidTime ||
      !import.meta.env.DEV ||
      currentTime < startsAt ||
      (promise.status === 'pending' && currentTime >= endsAt) ||
      ['cancelled', 'withdrawn', 'permissionBlocked'].includes(promise.status)
    ) {
      return
    }

    onVerify()
  }

  if (!hasValidTime) {
    return null
  }

  return (
    <div className="promise-auth-preview">
      {!isVerified && (
        <p role="status">
          {promise.status === 'techFailed' || promise.status === 'techError'
            ? '사용자 실패로 기록되지 않아요'
            : promise.status === 'evaluating'
              ? '사진 판정 결과를 기다리고 있어요.'
              : showAuthEnded
                ? '인증 시간이 종료되었어요.'
                : `${formatAuthWindow(startsAt, endsAt)} 사이에 인증할 수 있어요`}
        </p>
      )}

      {(hasReachedStart || isVerified) && (
        <Button onClick={handleVerify} disabled={!canVerify}>
          <img src={cameraIcon} alt="" width={16} height={16} />
          {promise.status === 'techFailed' || promise.status === 'techError'
            ? '인증 결과'
            : isVerified
              ? '인증 완료'
              : promise.status === 'evaluating'
                ? '판정 대기 중'
                : showAuthEnded
                  ? '인증 시간 종료'
                  : '약속 인증하기'}
        </Button>
      )}
    </div>
  )
}