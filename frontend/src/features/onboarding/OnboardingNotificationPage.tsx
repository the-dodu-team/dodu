import closeIcon from '../../assets/icons/close.svg'
import bellIcon from '../../assets/icons/bell-filled.svg'

type OnboardingNotificationPageProps = {
  totalSteps: 3 | 4
  isDenied: boolean
  recoveryText: string
  attempt: number
  onRequest: () => void
  onBack: () => void
  onClose: () => void
}

export default function OnboardingNotificationPage({
  totalSteps, isDenied, recoveryText, attempt, onRequest,
  onBack,
  onClose,
}: OnboardingNotificationPageProps) {
  return (
    <div className="onboarding-page">
      <header className="onboarding-header">
        <button type="button" className="onboarding-close" aria-label="이전 단계로" onClick={onBack}>
          <span aria-hidden="true">←</span>
        </button>
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
        <p className="onboarding-step" aria-label={`전체 ${totalSteps}단계 중 ${totalSteps - 1}단계`}>
          <span aria-hidden="true">
            <strong>{totalSteps - 1}</strong> / {totalSteps}
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
        {isDenied && (
          <section className="onboarding-camera-guide" aria-label="권한 복구 안내">
            <p role="status" key={attempt}>권한이 아직 허용되지 않았어요. 설정을 확인한 뒤 다시 확인해 주세요.</p>
            <p>{recoveryText}</p>
            <p>설정 메뉴 이름은 OS와 브라우저 버전에 따라 다를 수 있어요.</p>
          </section>
        )}
      </main>

      <footer className="onboarding-footer">
        <button
          type="button"
          className="onboarding-next"
          onClick={onRequest}
        >
          {isDenied ? '권한 다시 확인하기' : '알림 권한 허용하기'}
        </button>
      </footer>
    </div>
  )
}