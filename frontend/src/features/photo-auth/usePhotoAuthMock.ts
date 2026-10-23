import { useEffect, useReducer } from 'react'
import { photoReducer } from './photoAuthModel'
import type { PhotoSession } from './photoAuthModel'

export function usePhotoAuthMock(initialSession: PhotoSession | null = null) {
  const [session, dispatch] = useReducer(photoReducer, initialSession)
  const active = session !== null
  useEffect(() => {
    if (!active) return
    const tick = () => dispatch({ type: 'tick', now: Date.now() })
    const timer = window.setInterval(tick, 250)
    window.addEventListener('focus', tick)
    document.addEventListener('visibilitychange', tick)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', tick)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [active])
  return { session, dispatch }
}
