import { useState } from 'react'
import closeIcon from '../../assets/icons/close.svg'
import infoIcon from '../../assets/icons/info-circle.svg'
import shareIcon from '../../assets/icons/upload.svg'
import addIcon from '../../assets/icons/checkbox-unchecked.svg'
import homeIcon from '../../assets/icons/home-outline.svg'

type OnboardingInstallPageProps = {
  onClose: () => void
}

export default function OnboardingInstallPage({
  onClose,
}: OnboardingInstallPageProps) {
  const [showNotice, setShowNotice] = useState(false)

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
        className="onboarding-main onboarding-install"
        aria-labelledby="onboarding-install-title"
      >
        <p className="onboarding-step" aria-label="전체 4단계 중 2단계">
          <span aria-hidden="true">
            <strong>2</strong> / 4
          </span>
        </p>

        <div className="onboarding-install-body">
          <div className="onboarding-headline">
            <p className="onboarding-overline">DODU 링크를</p>
            <h1
              id="onboarding-install-title"
              className="onboarding-title"
            >
              홈 화면에 추가하세요
            </h1>
            <p className="onboarding-description">
              앱을 홈 화면에 추가해야 시작 알림을 받을 수 있어요
            </p>
          </div>

          <div className="onboarding-install-card">
            <ol className="onboarding-install-steps">
              <li>
                <span className="onboarding-step-number" aria-hidden="true">
                  1
                </span>
                <span>Safari 하단의 공유 버튼을 눌러요</span>
                <span
                  className="onboarding-install-icon onboarding-install-icon--share"
                  style={{ maskImage: `url("${shareIcon}")` }}
                  aria-hidden="true"
                />
              </li>

              <li>
                <span className="onboarding-step-number" aria-hidden="true">
                  2
                </span>
                <span>목록에서 "홈 화면에 추가" → [추가]</span>
                <span
                  className="onboarding-install-icon"
                  style={{ maskImage: `url("${addIcon}")` }}
                  aria-hidden="true"
                />
              </li>

              <li>
                <span className="onboarding-step-number" aria-hidden="true">
                  3
                </span>
                <span>홈 화면의 앱 아이콘으로 다시 열어요</span>
                <span
                  className="onboarding-install-icon"
                  style={{ maskImage: `url("${homeIcon}")` }}
                  aria-hidden="true"
                />
              </li>
            </ol>

            <p className="onboarding-install-note">
              브라우저에서 입력한 정보는 홈 화면 앱으로 이어지지 않아요.
              홈 화면 앱에서 참가자 코드를 한 번 더 입력해 주세요.
            </p>
          </div>
        </div>
      </main>

      <footer className="onboarding-footer onboarding-install-footer">
        {showNotice && (
          <div className="onboarding-install-notice" role="status">
            <span
              className="onboarding-notice-icon"
              style={{ maskImage: `url("${infoIcon}")` }}
              aria-hidden="true"
            />
            <p>홈 화면의 DODU 아이콘으로 다시 열어주세요</p>
          </div>
        )}

        <button
          type="button"
          className="onboarding-next"
          onClick={() => setShowNotice(true)}
        >
          홈 화면에 추가했어요
        </button>
      </footer>
    </div>
  )
}