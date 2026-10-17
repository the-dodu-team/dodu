import closeIcon from '../../assets/icons/close.svg'
import cameraIcon from '../../assets/icons/camera-filled.svg'

type OnboardingCameraPageProps = {
  isComplete?: boolean
  onClose: () => void
}

export default function OnboardingCameraPage({
  isComplete = false,
  onClose,
}: OnboardingCameraPageProps) {
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
        aria-labelledby="onboarding-camera-title"
      >
        <p className="onboarding-step" aria-label="전체 4단계 중 4단계">
          <span aria-hidden="true">
            <strong>4</strong> / 4
          </span>
        </p>

        <div className="onboarding-camera-body">
          <div className="onboarding-permission-body">
            <span
              className="onboarding-permission-icon"
              style={{ maskImage: `url("${cameraIcon}")` }}
              aria-hidden="true"
            />

            <div className="onboarding-headline">
              <p className="onboarding-overline">
                {isComplete
                  ? '카메라가 잘 열려요'
                  : '브라우저 카메라 권한이 필요해요'}
              </p>

              <h1
                id="onboarding-camera-title"
                className="onboarding-title"
              >
                {isComplete
                  ? '이제 사진으로 인증할 수 있어요'
                  : '지금 카메라를 한 번 열어보세요'}
              </h1>

              <p className="onboarding-description onboarding-camera-description">
                {isComplete ? (
                  <>
                    시작 시간이 되면 같은 방법으로
                    <br />
                    작업 도구를 찍어서 인증해요
                  </>
                ) : (
                  '열리지 않으면 아래 방법으로 허용해 주세요.'
                )}
              </p>
            </div>
          </div>

          {!isComplete && (
            <section
              className="onboarding-camera-guide"
              aria-labelledby="camera-guide-title"
            >
              <h2 id="camera-guide-title">
                카메라가 열리지 않으면
              </h2>

              <dl className="onboarding-camera-platform">
                <dt>iPhone · Safari</dt>
                <dd>설정 › Safari › 카메라 › 허용</dd>
              </dl>

              <dl className="onboarding-camera-platform">
                <dt>Android · Chrome</dt>
                <dd>
                  <span className="onboarding-menu-symbol" aria-hidden="true">
                    ⋮
                  </span>
                  <span className="visually-hidden">더보기</span>
                  <span>› 설정 › 사이트 설정 › 카메라 › 허용</span>
                </dd>
              </dl>

              <p className="onboarding-camera-note">
                설정 메뉴 이름은 OS 버전에 따라 다를 수 있어요.
              </p>
            </section>
          )}
        </div>
      </main>

      <footer className="onboarding-footer">
        <button
          type="button"
          className="onboarding-next"
          onClick={() => {
            // 디자인 미리보기: 실제 권한 요청과 완료 처리는 연결 전이다.
          }}
        >
          {!isComplete && (
            <span
              className="onboarding-button-icon"
              style={{ maskImage: `url("${cameraIcon}")` }}
              aria-hidden="true"
            />
          )}
          {isComplete ? '완료하기' : '카메라 권한 허용하기'}
        </button>
      </footer>
    </div>
  )
}