/// <reference lib="webworker" />
import './pushClick'
import { initializeApp } from 'firebase/app'
import { getMessaging, onBackgroundMessage } from 'firebase/messaging/sw'
import { readPushConfig } from './pushConfig'

const config = readPushConfig(import.meta.env)
const worker = globalThis as unknown as ServiceWorkerGlobalScope
worker.addEventListener('install', event => { event.waitUntil(worker.skipWaiting()) })
worker.addEventListener('activate', event => { event.waitUntil(worker.clients.claim()) })
if (config.configured) {
  const messaging = getMessaging(initializeApp(config.firebase))
  // The backend sends a notification payload: Firebase displays it once automatically.
  // Never showNotification again here or record receipt as a user read.
  onBackgroundMessage(messaging, () => {})
}
