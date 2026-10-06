import { useEffect, useState } from 'react'
import Button from '../../components/Button'
import cameraIcon from '../../assets/icons/camera-filled.svg'
import { formatStartTime } from './formatPromise'
import type { PreviewPromise } from './types'

type PromiseAuthPreviewProps = {
  promise: PreviewPromise
  onVerify: () => void
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
  const hasReachedStart = Number.isFinite(startsAt) && now >= startsAt
  const isVerified = promise.status === 'verified'

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

  return (
    <div className="promise-auth-preview">
      <p role="status">
        {isVerified
          ? '인증 완료 상태의 미리보기입니다. 실제 인증은 실행되지 않았어요.'
          : hasReachedStart
            ? '예정 시각에 도달했어요. 인증 버튼 미리보기입니다.'
            : `${formatStartTime(promise.startTime)}부터 인증 버튼이 표시됩니다. (KST · 미리보기)`}
      </p>

      {(hasReachedStart || isVerified) && (
        <Button onClick={onVerify} disabled={isVerified}>
          <img src={cameraIcon} alt="" width={16} height={16} />
          {isVerified ? '인증 완료 · 미리보기' : '약속 인증하기'}
        </Button>
      )}
    </div>
  )
}