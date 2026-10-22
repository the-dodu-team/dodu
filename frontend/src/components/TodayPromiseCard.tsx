import clockIcon from '../assets/icons/clock-filled-gray.svg'
import cameraIcon from '../assets/icons/camera-filled-gray.svg'
import Button from './Button'

type TodayPromise = {
  taskName: string
  startTimeLabel: string
  toolLabel: string
  isToday?: boolean
}

type TodayPromiseCardProps = {
  promise: TodayPromise | null
  onCreate?: () => void
  onEdit?: () => void
  canCreate?: boolean
  canEdit?: boolean
}

export default function TodayPromiseCard({
  promise,
  onCreate,
  onEdit,
  canCreate = true,
  canEdit = true,
}: TodayPromiseCardProps) {
  return (
    <section
      className="today-promise-card"
      aria-labelledby="today-promise-title"
    >
      <div className="home-card-heading">
        <h2 id="today-promise-title" className="today-promise-label">
          다가오는 약속
        </h2>

        {promise && canEdit && (
          <button
            type="button"
            className="home-card-action"
            onClick={onEdit}
            disabled={!onEdit}
          >
            수정하기 <span aria-hidden="true">›</span>
          </button>
        )}
      </div>

      {promise ? (
        <>
          <div className="upcoming-promise-name-row">
            <h3 className="today-promise-name" title={promise.taskName}>
              {promise.taskName}
            </h3>
            {promise.isToday && (
              <span className="upcoming-promise-today-tag">오늘</span>
            )}
          </div>

          <p className="today-promise-detail">
            <img src={clockIcon} alt="" width={16} height={16} />
            <span>시작 시간</span>
            <strong>{promise.startTimeLabel}</strong>
          </p>

          <p className="today-promise-detail">
            <img src={cameraIcon} alt="" width={16} height={16} />
            <span>인증 도구</span>
            <strong>{promise.toolLabel}</strong>
          </p>
        </>
      ) : (
        <div className="today-promise-empty">
          <h3 className="today-promise-empty-title">
            오늘은 약속이 없어요
          </h3>
          <p className="today-promise-empty-description">
            오늘 해야 할 일이 있나요?
            <br />
            약속을 만들어보세요
          </p>
          <Button onClick={onCreate} disabled={!onCreate || !canCreate}>
            약속 만들기
          </Button>
        </div>
      )}
    </section>
  )
}
