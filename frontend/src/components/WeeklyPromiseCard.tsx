import { getKstDateKey } from '../features/promise/formatPromise'
import { getWeekRange, getWeeklyFinalRecords } from '../features/promise/promiseHistory'
import type { FinalPromiseRecord } from '../features/promise/promiseHistory'

type WeeklyPromiseCardProps = {
  records?: FinalPromiseRecord[]
  todayKey?: string
  onViewAll?: () => void
}

const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일']
const formatDate = (date: Date) => `${date.getUTCMonth() + 1}월 ${date.getUTCDate()}일`

export default function WeeklyPromiseCard({
  records = [], todayKey = getKstDateKey(), onViewAll,
}: WeeklyPromiseCardProps) {
  // KST 달력 날짜의 월요일부터 일요일까지 집계한다.
  const { monday, sunday } = getWeekRange(todayKey)
  const weekRecords = getWeeklyFinalRecords(records, todayKey)
  const totalCount = weekRecords.length
  const verifiedCount = weekRecords.filter(record => record.status === 'verified').length
  const progress = totalCount ? verifiedCount / totalCount * 100 : 0
  const days = DAY_LABELS.map((label, index) => {
    const date = new Date(monday)
    date.setUTCDate(date.getUTCDate() + index)
    const key = date.toISOString().slice(0, 10)
    return {
      date, key, label,
      status: weekRecords.find(record => record.date === key)?.status ?? 'none',
      timing: key === todayKey ? 'today' : key < todayKey ? 'past' : 'future',
    }
  })

  return (
    <section className="weekly-promise-card" aria-labelledby="weekly-promise-title">
      <div className="home-card-heading">
        <h2 id="weekly-promise-title" className="weekly-promise-label">지난 약속</h2>
        <button type="button" className="home-card-action" onClick={onViewAll} disabled={!onViewAll}>
          전체 보기 <span aria-hidden="true">›</span>
        </button>
      </div>
      <p className="weekly-promise-period">{formatDate(monday)} ~ {formatDate(sunday)}</p>
      {totalCount === 0 ? (
        <div className="weekly-promise-empty">
          <h3>이번 주의 지난 약속이 없어요</h3>
          <p>약속이 있다면 알려드릴게요</p>
        </div>
      ) : (
        <>
          <p className="weekly-promise-count">{totalCount}번 중 <strong>{verifiedCount}번</strong></p>
          <p className="weekly-promise-description">인증했어요</p>
          <div className="weekly-promise-progress" role="progressbar"
            aria-label="이번 주 최종 인증 결과" aria-valuemin={0} aria-valuemax={totalCount}
            aria-valuenow={verifiedCount} aria-valuetext={`${totalCount}번 중 ${verifiedCount}번 인증`}>
            <div className={progress === 100
              ? 'weekly-promise-progress-fill weekly-promise-progress-fill--complete'
              : 'weekly-promise-progress-fill'} style={{ width: `${progress}%` }} />
          </div>
        </>
      )}
      <ul className="weekly-promise-days">
        {days.map(day => (
          <li key={day.key}
            className={`weekly-promise-day weekly-promise-day--${day.timing} weekly-promise-day--${day.status}`}
            aria-current={day.timing === 'today' ? 'date' : undefined}>
            <span>{day.date.getUTCDate()}</span><span>{day.label}</span>
            <span className="visually-hidden">{day.status === 'verified' ? '인증 완료'
              : day.status === 'unverified' ? '인증 미완료' : '최종 인증 결과 없음'}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
