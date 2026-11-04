export function readPushConfig(env: Record<string, string | undefined>) {
  const firebase = {
    apiKey: env.VITE_FIREBASE_API_KEY?.trim() ?? '',
    projectId: env.VITE_FIREBASE_PROJECT_ID?.trim() ?? '',
    messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID?.trim() ?? '',
    appId: env.VITE_FIREBASE_APP_ID?.trim() ?? '',
  }
  const vapidKey = env.VITE_FIREBASE_VAPID_KEY?.trim() ?? ''
  const values = [...Object.values(firebase), vapidKey]
  return { firebase, vapidKey, configured: values.every(Boolean), missing: values.some(Boolean) && !values.every(Boolean) }
}

// Only known notification kinds and UUIDs may influence navigation. Never accept a payload URL.
export function pushDestination(data?: Record<string, string>) {
  return data?.type === 'SURVEY_REMINDER' && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(data.promiseId ?? '')
    ? `/?self-report=${encodeURIComponent(data.promiseId)}` : '/'
}
