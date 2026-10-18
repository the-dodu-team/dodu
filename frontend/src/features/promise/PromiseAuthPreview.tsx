import { useEffect, useState } from 'react'
import Button from '../../components/Button'
import cameraIcon from '../../assets/icons/camera-filled.svg'
import type { PreviewPromise } from './types'

type PromiseAuthPreviewProps = {
  promise: PreviewPromise
  onVerify: () => void
}

const AUTH_DURATION_MS = 30 * 60 * 1000

function formatTime(timestamp: number) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  }).format(new Date(timestamp))
}

// 화면 확인용이며 실제 사진 인증이나 서버 저장은 실행하지 않는다.
export default function PromiseAuthPreview({
  promise,
  onVerify,
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
  const canVerify = hasReachedStart && !hasExpired && !isVerified

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
      !hasValidTime ||
      isVerified ||
      currentTime < startsAt ||
      currentTime >= endsAt
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
          {hasExpired
            ? '인증 시간이 종료되었어요.'
            : `${formatTime(startsAt)} ~ ${formatTime(endsAt)} 사이에 인증할 수 있어요`}
        </p>
      )}

      {(hasReachedStart || isVerified) && (
        <Button onClick={handleVerify} disabled={!canVerify}>
          <img src={cameraIcon} alt="" width={16} height={16} />
          {isVerified
            ? '인증 완료'
            : hasExpired
              ? '인증 시간 종료'
              : '약속 인증하기'}
        </Button>
      )}
    </div>
  )
}