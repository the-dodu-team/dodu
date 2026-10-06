type WeeklyRecord = {
  date: string
  status: 'pending' | 'verified' | 'unverified'
}

type WeeklyPromiseCardProps = {
  records?: WeeklyRecord[]
  onViewAll?: () => void
}

const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일']

function getKstDateKey() {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())

  const year = parts.find((part) => part.type === 'year')!.value
  const month = parts.find((part) => part.type === 'month')!.value
  const day = parts.find((part) => part.type === 'day')!.value

  return `${year}-${month}-${day}`
}

function formatDate(date: Date) {
  return `${date.getUTCMonth() + 1}월 ${date.getUTCDate()}일`
}

export default function WeeklyPromiseCard({
  records = [],
  onViewAll,
}: WeeklyPromiseCardProps) {
  const todayKey = getKstDateKey()

  // KST의 달력 날짜를 UTC 기반으로 계산해 기기 시간대 영향을 피한다.
  const monday = new Date(`${todayKey}T00:00:00Z`)
  const offset = (monday.getUTCDay() + 6) % 7
  monday.setUTCDate(monday.getUTCDate() - offset)

  const days = DAY_LABELS.map((label, index) => {
    const date = new Date(monday)
    date.setUTCDate(monday.getUTCDate() + index)

    const dateKey = date.toISOString().slice(0, 10)
    const record = records.find((item) => item.date === dateKey)

    return {
      date,
      dateKey,
      label,
      status: record?.status ?? 'none',
      timing:
        dateKey === todayKey
          ? 'today'
          : dateKey < todayKey
            ? 'past'
            : 'future',
    }
  })

  const weekRecords = records.filter(
    (record) =>
      record.date >= days[0].dateKey &&
      record.date <= days[6].dateKey,
  )
  const totalCount = weekRecords.length
  const verifiedCount = weekRecords.filter(
    (record) => record.status === 'verified',
  ).length
  const progress = totalCount > 0
    ? (verifiedCount / totalCount) * 100
    : 0

  const allUnverified =
    totalCount > 0 &&
    weekRecords.every((record) => record.status === 'unverified')

  return (
    <section
      className="weekly-promise-card"
      aria-labelledby="weekly-promise-title"
    >
      {/* 이전 제목 배치 컨테이너: <div className="home-card-heading"> */}
      <header className="home-card-heading">
        <h2 id="weekly-promise-title" className="weekly-promise-label">
          이번 주 약속
        </h2>
        <button
          type="button"
          className="home-card-action"
          onClick={onViewAll}
          disabled={!onViewAll}
        >
          전체 보기 <span aria-hidden="true">›</span>
        </button>
      </header>

      <p className="weekly-promise-period">
        {formatDate(days[0].date)} ~ {formatDate(days[6].date)}
      </p>

      {totalCount === 0 ? (
        <div className="weekly-promise-empty">
          <h3>이번 주는 약속이 없어요</h3>
          <p>약속이 있다면 알려드릴게요</p>
        </div>
      ) : (
        <>
          {!allUnverified && (
            <>
              <p className="weekly-promise-count">
                {totalCount}번 중 <strong>{verifiedCount}번</strong>
              </p>
              <p className="weekly-promise-description">
                인증했어요
              </p>
            </>
          )}

          <div
            className="weekly-promise-progress"
            role="progressbar"
            aria-label="이번 주 약속 인증"
            aria-valuemin={0}
            aria-valuemax={totalCount}
            aria-valuenow={verifiedCount}
            aria-valuetext={`${totalCount}번 중 ${verifiedCount}번 인증`}
          >
            {allUnverified ? (
              <div className="weekly-promise-progress-zero" />
            ) : (
              <div
                className={
                  progress === 100
                    ? 'weekly-promise-progress-fill weekly-promise-progress-fill--complete'
                    : 'weekly-promise-progress-fill'
                }
                style={{ width: `${progress}%` }}
              />
            )}
          </div>
        </>
      )}

      <ul className="weekly-promise-days">
        {days.map((day) => (
          <li
            key={day.dateKey}
            className={`weekly-promise-day weekly-promise-day--${day.timing} weekly-promise-day--${day.status}`}
            aria-current={day.timing === 'today' ? 'date' : undefined}
          >
            {/* 이전 날짜 표시: <span>{day.date.getUTCDate()}</span> */}
            <time dateTime={day.dateKey}>{day.date.getUTCDate()}</time>
            <span>{day.label}</span>
            <span className="visually-hidden">
              {day.status === 'verified'
                ? '인증 완료'
                : day.status === 'unverified'
                  ? '인증 미완료'
                  : day.status === 'pending'
                    ? '약속 예정'
                    : '약속 없음'}
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}
