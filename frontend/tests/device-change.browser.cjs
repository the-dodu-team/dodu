const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ headless: true, ...(process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}) })
  try {
    const page = await browser.newPage({ viewport: { width: 375, height: 812 } }), errors = []
    page.on('pageerror', e => errors.push(e.message))
    await page.addInitScript(() => {
      Object.defineProperty(window, 'Notification', { configurable: true, value: { permission: 'granted', requestPermission: async () => 'granted' } })
      const permission = new EventTarget(); permission.state = 'granted'
      Object.defineProperty(navigator.permissions, 'query', { configurable: true, value: async () => permission })
    })
    let attempts = 0, confirmed = 0
    const envelope = data => ({ status: 'success', data, serverTime: new Date().toISOString() })
    await page.route('**/api/**', async route => {
      const req = route.request(), url = new URL(req.url()); let data
      if (url.pathname.endsWith('/sessions')) {
        attempts++
        if (!req.postDataJSON().confirmDeviceChange) return route.fulfill({ status: 409, contentType: 'application/json',
          body: JSON.stringify({ status: 'error', error: { code: 'DEVICE_CHANGE_CONFIRMATION_REQUIRED' } }) })
        confirmed++
        data = envelope({ sessionToken: 'synthetic-new-device-session', onboardingCompleted: true, expiresAt: new Date(Date.now() + 86400000).toISOString() })
      } else if (url.pathname.endsWith('/me')) data = envelope({ onboardingCompleted: true, expiresAt: new Date(Date.now() + 86400000).toISOString() })
      else if (url.pathname.endsWith('/active')) data = null
      else if (url.pathname.endsWith('/history')) data = []
      else if (url.pathname.endsWith('/notifications')) data = envelope([])
      else throw Error('Unexpected synthetic route')
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) })
    })
    await page.goto(process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4178')
    await page.getByLabel('참가자 식별 코드', { exact: true }).fill('SYNTHETIC123')
    await page.getByRole('button', { name: '입장하기', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.waitFor(); assert.equal(confirmed, 0)
    await dialog.getByRole('button', { name: '취소', exact: true }).click()
    assert.equal(confirmed, 0); assert.equal(attempts, 1)
    assert.equal(await page.evaluate(() => sessionStorage.getItem('dodu.live.session.v1')), null)
    await page.getByRole('button', { name: '입장하기', exact: true }).click()
    await dialog.waitFor()
    await dialog.getByRole('button', { name: '확인', exact: true }).click()
    await page.locator('.home-page').waitFor()
    assert.equal(confirmed, 1); assert.equal(attempts, 3)
    assert.equal(await page.getByRole('button', { name: '서버 상태 다시 확인' }).count(), 0)
    assert.deepEqual(errors, [])
    console.log('PASS: 새 기기 경고→취소 시 미발급→확인 시 명시 플래그로 입장, JS 오류 0 (합성 API)')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e.message); process.exitCode = 1 })
