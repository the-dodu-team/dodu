import TodayPromiseCard from './components/TodayPromiseCard'
import WeeklyPromiseCard from './components/WeeklyPromiseCard'
import PromiseAuthPreview from './features/promise/PromiseAuthPreview'
import { formatStartTime, getKstDateKey } from './features/promise/formatPromise'
import type { PreviewPromise } from './features/promise/types'

type DoduHomeProps = {
  promise: PreviewPromise | null
  onCreate: () => void
  onEdit: () => void
  onViewAll: () => void
  onVerify: () => void
}

export default function DoduHome({
  promise,
  onCreate,
  onEdit,
  onViewAll,
  onVerify,
}: DoduHomeProps) {
  const todayPromise =
    promise?.date === getKstDateKey() ? promise : null

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
          todayPromise
            ? {
                taskName: todayPromise.taskName,
                startTimeLabel: formatStartTime(todayPromise.startTime),
                toolLabel:
                  todayPromise.tool === 'computer' ? '컴퓨터' : '실제 책',
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

      {/* 이전: 인증 기능과 안내문을 모두 <footer className="home-footer">에 배치했다. */}
      <div className="home-bottom">
        {promise && (
          <PromiseAuthPreview
            key={promise.date + promise.startTime}
            promise={promise}
            onVerify={onVerify}
          />
        )}

        <footer className="home-footer">
        <p>
          입력한 약속의 미리보기입니다. 실제 예약이나 알림은
          실행되지 않으며 새로고침하면 사라집니다.
        </p>
        </footer>
      </div>

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
