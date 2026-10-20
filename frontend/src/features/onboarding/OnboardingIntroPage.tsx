import promiseImage from '../../assets/onboarding-promise.png'
import closeIcon from '../../assets/icons/close.svg'
import infoIcon from '../../assets/icons/info-circle.svg'

type OnboardingIntroPageProps = {
  totalSteps: 3 | 4
  onNext: () => void
  onClose: () => void
}

export default function OnboardingIntroPage({
  totalSteps,
  onNext,
  onClose,
}: OnboardingIntroPageProps) {
  return (
    <div className="onboarding-page">
      <header className="onboarding-header">
        <button
          type="button"
          className="onboarding-close"
          aria-label="온보딩 닫기"
          onClick={onClose}
        >
          <img src={closeIcon} alt="" width={24} height={24} />
        </button>
      </header>

      <main
        className="onboarding-main onboarding-intro"
        aria-labelledby="onboarding-title"
      >
        <p
          className="onboarding-step"
          aria-label={`전체 ${totalSteps}단계 중 1단계`}
        >
          <span aria-hidden="true">
            <strong>1</strong> / {totalSteps}
          </span>
        </p>

        <div className="onboarding-illustration">
          <img src={promiseImage} alt="" width={256} height={256} />
        </div>

        <div className="onboarding-intro-copy">
          <h1 id="onboarding-title" className="onboarding-title">
            약속을 행동으로 이어드려요
          </h1>

          <div className="onboarding-intro-description">
            <p>
              스스로 정한 작업 시작 약속을 실제 행동으로 연결하도록
              돕는 서비스예요
            </p>
            <p>
              시작 시간이 되면 작업 도구 사진 인증으로 다시 알려드려요
            </p>
          </div>
        </div>

        {totalSteps === 4 && <div className="onboarding-platform-info">
          <span
            className="onboarding-platform-icon"
            style={{ maskImage: `url("${infoIcon}")` }}
            aria-hidden="true"
          />
          <p>
            iPhone은 홈 화면에 추가해야 알림을 받을 수 있어요.
          </p>
        </div>}
      </main>

      <footer className="onboarding-footer">
        <button
          type="button"
          className="onboarding-next"
          onClick={onNext}
        >
          다음으로
        </button>
      </footer>
    </div>
  )
}
