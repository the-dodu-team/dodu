import { detectOnboardingPlatform } from './detectOnboardingPlatform'
import { useEffect, useRef, useState } from 'react'
import OnboardingIntroPage from './OnboardingIntroPage'
import OnboardingInstallPage from './OnboardingInstallPage'
import OnboardingNotificationPage from './OnboardingNotificationPage'
import OnboardingCameraPage from './OnboardingCameraPage'

type Step = 'intro' | 'install' | 'notification' | 'camera' | 'complete'
type Platform = 'ios' | 'other'
type Permission = 'default' | 'granted' | 'denied'

type Props = {
  initialStep?: Step
  onClose: () => void
  onComplete: () => void
}

// 실제 설치·권한·세션 상태와 분리된 개발용 화면 테스트다.
export default function OnboardingMockFlow({
  initialStep = 'intro', onClose, onComplete,
}: Props) {
  // const [platform, setPlatform] = useState<Platform>('ios')
  const [platform, setPlatform] = useState<Platform>(detectOnboardingPlatform)
  const [step, setStep] = useState<Step>(() => initialStep === 'install' && platform !== 'ios' ? 'notification' : initialStep)
  const [notification, setNotification] = useState<Permission>('default')
  const [camera, setCamera] = useState<Permission>(
    initialStep === 'complete' ? 'granted' : 'default',
  )
  const [result, setResult] = useState<'granted' | 'denied'>('granted')
  const [attempt, setAttempt] = useState(0)
  const contentRef = useRef<HTMLDivElement>(null)
  const totalSteps = platform === 'ios' ? 4 : 3

  useEffect(() => {
    const heading = contentRef.current?.querySelector('h1')
    heading?.setAttribute('tabindex', '-1')
    heading?.focus()
  }, [step, platform])

  function changePlatform(value: Platform) {
    setPlatform(value)
    setStep('intro')
    setNotification('default')
    setCamera('default')
    setResult('granted')
    setAttempt(0)
  }

  function checkPermission() {
    setAttempt((previous) => previous + 1)
    if (step === 'notification') {
      setNotification(result)
      if (result === 'granted') setStep('camera')
    } else if (step === 'camera') {
      setCamera(result)
      if (result === 'granted') setStep('complete')
    }
  }

  const isPermissionStep = step === 'notification' || step === 'camera'
  const denied = step === 'notification'
    ? notification === 'denied'
    : step === 'camera' && camera === 'denied'
  const recovery = platform === 'ios'
    ? step === 'notification'
      ? '홈 화면의 DODU 앱에서 다시 확인해 주세요. iPhone 설정의 앱별 알림 설정도 확인해 주세요.'
      : 'Safari 웹사이트 설정과 iPhone 설정에서 카메라 접근을 확인한 뒤 다시 확인해 주세요.'
    : step === 'notification'
      ? '사용 중인 브라우저의 사이트 권한에서 알림을 허용한 뒤 다시 확인해 주세요.'
      : '사용 중인 브라우저의 사이트 권한에서 카메라를 허용한 뒤 다시 확인해 주세요.'

  // 개발 전용 분기는 실제 권한이나 설치 상태를 변경하지 않는다.
  if (!import.meta.env.DEV) return null

  return (
    <div>
      <aside aria-label="개발용 온보딩 테스트" style={{ padding: 16, background: '#ebe9fb', color: '#283044' }}>
        <p style={{ margin: '0 0 8px' }}>개발용 mock · 실제 설치나 권한 요청은 실행하지 않아요.</p>
        <label style={{ display: 'block' }}>
          테스트 환경
          <select
            value={platform}
            onChange={(event) => changePlatform(event.target.value === 'ios' ? 'ios' : 'other')}
            style={{ display: 'block', minHeight: 44, width: '100%', font: 'inherit' }}
          >
            <option value="ios">iOS · 4단계</option>
            <option value="other">기타 · 3단계</option>
          </select>
        </label>
        {isPermissionStep && (
          <label style={{ display: 'block', marginTop: 8 }}>
            다음 권한 요청·재확인 결과
            <select
              value={result}
              onChange={(event) => setResult(event.target.value === 'denied' ? 'denied' : 'granted')}
              style={{ display: 'block', minHeight: 44, width: '100%', font: 'inherit' }}
            >
              <option value="granted">허용</option>
              <option value="denied">거부</option>
            </select>
          </label>
        )}
      </aside>
      <div ref={contentRef}>
        {step === 'intro' && (
          <OnboardingIntroPage totalSteps={totalSteps} onClose={onClose}
            onNext={() => setStep(platform === 'ios' ? 'install' : 'notification')} />
        )}
        {step === 'install' && (
          <OnboardingInstallPage onClose={onClose} onBack={() => setStep('intro')}
            onNext={() => setStep('notification')} />
        )}
        {step === 'notification' && (
          <OnboardingNotificationPage totalSteps={totalSteps} onClose={onClose}
            onBack={() => setStep(platform === 'ios' ? 'install' : 'intro')}
            onRequest={checkPermission} isDenied={denied} recoveryText={recovery} attempt={attempt} />
        )}
        {(step === 'camera' || step === 'complete') && (
          <OnboardingCameraPage totalSteps={totalSteps} isComplete={step === 'complete'}
            onClose={onClose} onBack={() => setStep('notification')}
            onRequest={step === 'complete' ? onComplete : checkPermission}
            isDenied={denied} recoveryText={recovery} attempt={attempt} />
        )}
      </div>
    </div>
  )
}
