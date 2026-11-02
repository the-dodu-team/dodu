const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const api = fs.readFileSync(path.join(__dirname, '../src/api/doduApi.ts'), 'utf8')
const livePhoto = fs.readFileSync(path.join(__dirname, '../src/features/photo-auth/LivePhotoAuth.tsx'), 'utf8')
const livePanel = fs.readFileSync(path.join(__dirname, '../src/features/self-report/LiveParticipantPanel.tsx'), 'utf8')

for (const endpoint of [
  '/api/promises/active', '/api/promises/history', '/api/promises', '/api/participant/notifications',
  '/api/promises/${promiseId}/auth-attempts', '/api/promises/${promiseId}/validation-survey',
]) assert.ok(api.includes(endpoint), `missing endpoint: ${endpoint}`)
assert.match(api, /instanceof FormData/)
assert.match(livePhoto, /submitPhoto/)
assert.match(livePanel, /listNotifications/)
assert.match(livePanel, /listAuthAttempts/)
assert.match(livePanel, /markNotificationRead/)
console.log('PASS: live FE API contract and participant data surfaces')
