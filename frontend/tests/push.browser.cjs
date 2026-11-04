const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
async function main() {
  // This fixture build uses synthetic public config, never production credentials.
  const browser = await chromium.launch({ headless: true, ...(process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}) })
  try {
    const page = await browser.newPage({ viewport: { width: 375, height: 812 } }), errors = []
    page.on('pageerror', e => errors.push(e.message))
    await page.addInitScript(() => {
      Object.defineProperty(window, 'Notification', { configurable: true, value: { permission: 'granted', requestPermission: async () => 'granted' } })
      const permission = new EventTarget(); permission.state = 'granted'
      Object.defineProperty(navigator.permissions, 'query', { configurable: true, value: async () => permission })
    })
    const directory = path.resolve(__dirname, '../../.tools/push-browser-dist')
    const worker = fs.readdirSync(directory).find(name => /^firebase-messaging-sw-.*\.js$/.test(name))
    assert.ok(worker)
    let puts = 0, deletes = 0, reads = 0, replaced = false
    await page.route('**/assets/firebasePush-*.js', route => route.fulfill({ contentType: 'text/javascript', body: `
      export function firebasePushDriver() { return {
        supported: async () => true,
        token: async () => { await navigator.serviceWorker.register('/${worker}', {scope:'/'}); await navigator.serviceWorker.ready; return 'synthetic-browser-push'; },
        listen: fn => { window.syntheticPush = fn; return () => { window.syntheticPush = undefined; }; },
        remove: async () => { window.syntheticRemoved = true; }
      }; }
    ` }))
    const envelope = data => ({ status: 'success', data, serverTime: new Date().toISOString() })
    await page.route('**/api/**', async route => {
      const req = route.request(), url = new URL(req.url()); let body
      if (url.pathname.endsWith('/sessions')) body = envelope({ sessionToken: 'synthetic-owner', onboardingCompleted: true, expiresAt: new Date(Date.now() + 86400000).toISOString() })
      else if (url.pathname.endsWith('/me')) {
        if (replaced) return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ status: 'error', error: { code: 'SESSION_REPLACED' } }) })
        body = envelope({ onboardingCompleted: true, expiresAt: new Date(Date.now() + 86400000).toISOString() })
      }
      else if (url.pathname.endsWith('/active')) body = null
      else if (url.pathname.endsWith('/history')) body = []
      else if (url.pathname.endsWith('/notifications')) body = envelope([])
      else if (url.pathname.endsWith('/read')) { reads++; body = envelope(null) }
      else if (url.pathname.endsWith('/notification-subscription')) {
        assert.equal(req.headers().authorization, 'Bearer synthetic-owner')
        if (req.method() === 'PUT') { puts++; assert.deepEqual(req.postDataJSON(), { subscriptionKey: 'synthetic-browser-push' }) }
        if (req.method() === 'DELETE') deletes++
        body = envelope(null)
      } else throw Error('Unexpected synthetic route')
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
    })
    await page.goto(process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4177')
    await page.getByLabel('참가자 식별 코드', { exact: true }).fill('SYNTHETIC123')
    await page.getByRole('button', { name: '입장하기', exact: true }).click()
    await page.waitForFunction(() => typeof window.syntheticPush === 'function')
    assert.equal(puts, 1)
    assert.equal(await page.getByRole('button', { name: '서버 상태 다시 확인' }).count(), 0)
    await page.evaluate(() => { window.syntheticPush({ messageId: 'synthetic-message', data: { type: 'START' } }); window.syntheticPush({ messageId: 'synthetic-message', data: { type: 'START' } }) })
    await page.getByText('Dodu 알림이 도착했어요.').waitFor()
    assert.equal(await page.getByLabel('새 알림').count(), 1); assert.equal(reads, 0)
    await page.getByRole('button', { name: '닫기', exact: true }).click()
    await page.getByRole('button', { name: '설정', exact: true }).click()
    await page.getByText('알림이 연결되어 있어요.').waitFor()
    await page.getByRole('button', { name: '로그아웃', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '로그아웃', exact: true }).click()
    await page.getByRole('button', { name: '입장하기', exact: true }).waitFor()
    assert.equal(deletes, 1); assert.equal(await page.evaluate(() => window.syntheticRemoved), true)
    await page.getByLabel('참가자 식별 코드', { exact: true }).fill('SYNTHETIC123')
    await page.getByRole('button', { name: '입장하기', exact: true }).click()
    await page.waitForFunction(() => typeof window.syntheticPush === 'function')
    replaced = true
    await page.evaluate(() => window.syntheticPush({ messageId: 'synthetic-session-notice', data: { type: 'SESSION_REPLACED' } }))
    await page.getByText('다른 기기에서 입장했어요. 이 기기에서 사용하려면 다시 입장해 주세요.').waitFor()
    assert.deepEqual(errors, [])
    console.log('PASS: 합성 FCM 토큰→실제 worker 활성화→구독 PUT→전경 알림→로그아웃 해제·기존 기기 세션 종료 안내, JS 오류 0')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e.message); process.exitCode = 1 })
