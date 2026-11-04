import { getApp, getApps, initializeApp } from 'firebase/app'
import { deleteToken, getMessaging, getToken, isSupported, onMessage } from 'firebase/messaging'
import workerUrl from './firebaseWorker.ts?worker&url'
import { readPushConfig } from './pushConfig'
import type { PushDriver, PushNotice } from './pushController'
import { createForegroundPushHandler } from './foregroundPush'

export function firebasePushDriver(): PushDriver {
  const config = readPushConfig(import.meta.env)
  let registration: ServiceWorkerRegistration | undefined
  let receiver: ((notice: PushNotice) => void) | undefined
  let foregroundStarted = false
  function messaging() {
    return getMessaging(getApps().some(app => app.name === 'dodu-push')
      ? getApp('dodu-push') : initializeApp(config.firebase, 'dodu-push'))
  }
  return {
    supported: async () => window.isSecureContext && 'serviceWorker' in navigator && await isSupported(),
    token: async () => {
      registration = await navigator.serviceWorker.register(workerUrl, { scope: '/' })
      // ready can otherwise wait forever on a failed worker installation.
      const ready = await new Promise<ServiceWorkerRegistration>((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('PUSH_WORKER_TIMEOUT')), 15000)
        void navigator.serviceWorker.ready.then(value => { clearTimeout(timer); resolve(value) }, () => {
          clearTimeout(timer); reject(new Error('PUSH_WORKER_UNAVAILABLE'))
        })
      })
      if (ready.scope !== registration.scope) throw new Error('PUSH_WORKER_SCOPE')
      let timer: ReturnType<typeof setTimeout> | undefined
      try {
        return await Promise.race([
          getToken(messaging(), { vapidKey: config.vapidKey, serviceWorkerRegistration: registration }),
          new Promise<never>((_, reject) => { timer = setTimeout(() => reject(new Error('PUSH_TOKEN_TIMEOUT')), 45000) }),
        ])
      } finally { clearTimeout(timer) }
    },
    listen: receive => {
      receiver = receive
      if (!foregroundStarted) {
        // Keep the SDK security-notice handler for the app lifetime. Session cleanup
        // detaches only the participant UI; a late revocation notice can still display.
        onMessage(messaging(), createForegroundPushHandler(
          (title, options) => registration
            ? registration.showNotification(title, options) : Promise.reject(new Error('PUSH_WORKER_UNAVAILABLE')),
          notice => receiver?.(notice),
        ))
        foregroundStarted = true
      }
      return () => { if (receiver === receive) receiver = undefined }
    },
    remove: async () => { if (await isSupported()) await deleteToken(messaging()) },
  }
}
