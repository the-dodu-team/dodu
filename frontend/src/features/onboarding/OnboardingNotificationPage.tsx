import closeIcon from '../../assets/icons/close.svg'
import bellIcon from '../../assets/icons/bell-filled.svg'

type OnboardingNotificationPageProps = {
  onClose: () => void
}

export default function OnboardingNotificationPage({
  onClose,
}: OnboardingNotificationPageProps) {
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
        className="onboarding-main onboarding-permission"
        aria-labelledby="onboarding-notification-title"
      >
        <p className="onboarding-step" aria-label="전체 4단계 중 3단계">
          <span aria-hidden="true">
            <strong>3</strong> / 4
          </span>
        </p>

        <div className="onboarding-permission-body">
          <span
            className="onboarding-permission-icon"
            style={{ maskImage: `url("${bellIcon}")` }}
            aria-hidden="true"
          />

          <div className="onboarding-headline">
            <p className="onboarding-overline">
              시작 시간 알림을 받으려면
            </p>
            <h1
              id="onboarding-notification-title"
              className="onboarding-title"
            >
              알림 권한이 필요해요
            </h1>
            <p className="onboarding-description">
              알림 권한을 허용하면 약속한 시간에 알려드려요
            </p>
          </div>
        </div>
      </main>

      <footer className="onboarding-footer">
        <button
          type="button"
          className="onboarding-next"
          onClick={() => {
            // 실제 알림 권한 요청은 연결 단계에서 구현한다.
          }}
        >
          알림 권한 허용하기
        </button>
      </footer>
    </div>
  )
}