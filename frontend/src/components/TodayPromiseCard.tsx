import clockIcon from '../assets/icons/clock-filled-gray.svg'
import cameraIcon from '../assets/icons/camera-filled-gray.svg'
import Button from './Button'

type TodayPromise = {
  taskName: string
  startTimeLabel: string
  toolLabel: string
}

type TodayPromiseCardProps = {
  promise: TodayPromise | null
  onCreate?: () => void
  onEdit?: () => void
}

export default function TodayPromiseCard({
  promise,
  onCreate,
  onEdit,
}: TodayPromiseCardProps) {
  return (
    <section
      className="today-promise-card"
      aria-labelledby="today-promise-title"
    >
      {/* 이전 제목 배치 컨테이너: <div className="home-card-heading"> */}
      <header className="home-card-heading">
        <h2 id="today-promise-title" className="today-promise-label">
          오늘 약속
        </h2>

        {promise && (
          <button
            type="button"
            className="home-card-action"
            onClick={onEdit}
            disabled={!onEdit}
          >
            수정하기 <span aria-hidden="true">›</span>
          </button>
        )}
      </header>

      {promise ? (
        <>
          <h3 className="today-promise-name" title={promise.taskName}>
            {promise.taskName}
          </h3>

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
          <Button onClick={onCreate} disabled={!onCreate}>
            약속 만들기
          </Button>
        </div>
      )}
    </section>
  )
}
