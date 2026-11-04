export type CameraPermissionState = 'default' | 'granted' | 'denied' | 'unsupported' | 'insecure' | 'unavailable' | 'busy' | 'error'
type CameraStream = { getTracks: () => { stop: () => void }[] }
export type CameraEnvironment = {
  secure: boolean
  open?: (constraints: { video: boolean; audio: boolean }) => Promise<CameraStream>
}

export async function requestCameraPermission(environment: CameraEnvironment): Promise<CameraPermissionState> {
  if (!environment.secure) return 'insecure'
  if (!environment.open) return 'unsupported'
  try {
    const stream = await environment.open({ video: true, audio: false })
    // Permission checks never capture/upload a photo or leave the camera running.
    stream.getTracks().forEach(track => track.stop())
    return 'granted'
  } catch (cause) {
    const name = cause && typeof cause === 'object' && 'name' in cause ? cause.name : ''
    if (name === 'NotAllowedError' || name === 'SecurityError') return 'denied'
    if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'unavailable'
    if (name === 'NotReadableError' || name === 'AbortError') return 'busy'
    return 'error'
  }
}

export function cameraRecoveryMessage(state: CameraPermissionState): string {
  switch (state) {
    case 'denied': return '브라우저의 사이트 설정에서 카메라 권한을 허용한 뒤 다시 확인해 주세요.'
    case 'unsupported': return '이 브라우저에서는 카메라를 사용할 수 없어요. Safari 또는 Chrome에서 열어 주세요.'
    case 'insecure': return '카메라를 사용하려면 HTTPS 주소로 접속해 주세요.'
    case 'unavailable': return '연결된 카메라를 찾지 못했어요. 카메라가 있는 기기에서 열거나 연결 상태를 확인해 주세요.'
    case 'busy': return '카메라를 사용 중인 다른 앱을 닫고 다시 확인해 주세요.'
    default: return '카메라를 열지 못했어요. 기기와 브라우저 설정을 확인한 뒤 다시 시도해 주세요.'
  }
}
