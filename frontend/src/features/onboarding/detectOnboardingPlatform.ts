export type OnboardingPlatform = 'ios' | 'other'

type DeviceInfo = { userAgent: string; platform: string; maxTouchPoints: number }

// 안내 단계 선택용 추정값이다. 실제 권한·설치 여부를 뜻하지 않는다.
export function detectOnboardingPlatform(
  device: DeviceInfo | undefined = typeof navigator === 'undefined' ? undefined : navigator,
): OnboardingPlatform {
  if (!device) return 'other'
  if (/iPhone|iPad|iPod/i.test(device.userAgent)) return 'ios'
  // 데스크톱 형태의 사용자 에이전트를 사용하는 iPad도 고려한다.
  if (/Mac/i.test(device.platform) && device.maxTouchPoints > 1) return 'ios'
  return 'other'
}
