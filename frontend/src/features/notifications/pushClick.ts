/// <reference lib="webworker" />
import { pushDestination } from './pushConfig'

// Evaluated before Firebase's worker module; own the click without following arbitrary links.
const worker = globalThis as unknown as ServiceWorkerGlobalScope
worker.addEventListener('notificationclick', event => {
  event.stopImmediatePropagation()
  event.notification.close()
  const data = event.notification.data
  const destination = new URL(pushDestination(data?.FCM_MSG?.data ?? data), worker.location.origin)
  event.waitUntil((async () => {
    const clients = await worker.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const client of clients) {
      if (new URL(client.url).origin !== destination.origin) continue
      await client.navigate(destination.href); await client.focus(); return
    }
    await worker.clients.openWindow(destination.href)
  })())
})
