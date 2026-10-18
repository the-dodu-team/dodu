import { useEffect, useState } from 'react'
import TodayPromiseCard from './components/TodayPromiseCard'
import WeeklyPromiseCard from './components/WeeklyPromiseCard'
import PromiseAuthPreview from './features/promise/PromiseAuthPreview'
import { formatStartTime, getKstDateKey } from './features/promise/formatPromise'
import type { PreviewPromise } from './features/promise/types'

function formatScheduledLabel(promise: PreviewPromise) {
  const date = new Date(`${promise.date}T00:00:00+09:00`)
  if (!Number.isFinite(date.getTime())) return '날짜를 확인해 주세요'

  const parts = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', weekday: 'short',
  }).formatToParts(date)
  const month = parts.find((part) => part.type === 'month')?.value ?? ''
  const day = parts.find((part) => part.type === 'day')?.value ?? ''
  const weekday = parts.find((part) => part.type === 'weekday')?.value ?? ''

  return `${month} ${day}(${weekday}) ${formatStartTime(promise.startTime)}`
}

type DoduHomeProps = {
  promise: PreviewPromise | null
  onCreate: () => void
  onEdit: () => void
  onViewAll?: () => void
  onVerify: () => void
}

export default function DoduHome({
  promise,
  onCreate,
  onEdit,
  onViewAll,
  onVerify,
}: DoduHomeProps) {
  const [today, setToday] = useState(getKstDateKey)

  useEffect(() => {
    const updateDate = () => setToday(getKstDateKey())
    const timer = window.setInterval(updateDate, 1000)
    window.addEventListener('focus', updateDate)
    document.addEventListener('visibilitychange', updateDate)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', updateDate)
      document.removeEventListener('visibilitychange', updateDate)
    }
  }, [])

  // 현재 앱은 약속 한 개를 보관하므로 오늘 날짜로 제한하지 않고 표시한다.
  // 인증 진행 중인 약속도 시작 시각이 지났다는 이유만으로 숨기지 않는다.
  const upcomingPromise = promise

  /*
  const [connection, setConnection] = useState('확인 전')
  const [checking, setChecking] = useState(false)

  async function checkConnection() {
    setChecking(true)
    try {
      const response = await fetch('/api/health', { signal: AbortSignal.timeout(5000) })
      if (!response.ok) throw new Error('HTTP 오류')
      const result: unknown = await response.json()
      if (!result || typeof result !== 'object' || !('status' in result) || result.status !== 'ok') {
        throw new Error('응답 형식 오류')
      }
      setConnection('연결됨')
    } catch {
      setConnection('연결할 수 없습니다. 백엔드 실행을 확인해주세요.')
    } finally {
      setChecking(false)
    }
  }
  */

  return (
    <>
      <TodayPromiseCard
        promise={
          upcomingPromise
            ? {
                taskName: upcomingPromise.taskName,
                startTimeLabel: formatScheduledLabel(upcomingPromise),
                isToday: upcomingPromise.date === today,
                toolLabel:
                  upcomingPromise.tool === 'computer' ? '컴퓨터' : '실제 책',
              }
            : null
        }
        onCreate={onCreate}
        onEdit={onEdit}
      />

      <WeeklyPromiseCard
        records={
          promise
            ? [
                {
                  date: promise.date,
                  status: promise.status,
                },
              ]
            : []
        }
        onViewAll={onViewAll}
      />

      <footer className="home-footer">
        {promise && (
          <PromiseAuthPreview
            key={promise.date + promise.startTime}
            promise={promise}
            onVerify={onVerify}
          />
        )}

      </footer>

      {/*
      <p className="eyebrow">DODU · v2.5</p>
      <h1>계획한 시작을<br />첫 행동으로.</h1>
      <p>스스로 정한 개인작업의 시작을 돕는 서비스입니다.</p>
      <p>사진 인증은 작업환경 준비에 대한 응답이며 실제 작업 시작의 증거가 아닙니다.</p>
      <section aria-labelledby="environment-title">
        <h2 id="environment-title">개발환경 확인</h2>
        <p>현재는 앱 실행과 API 연결을 확인하는 준비 화면입니다.</p>
        <button disabled={checking} onClick={checkConnection}>
          {checking ? '확인 중…' : '백엔드 연결 확인'}
        </button>
        <p role="status">{connection}</p>
      </section>
      */}
    </>
  )
}
