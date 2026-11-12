const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ headless: true, ...(process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}) })
  try {
    const page = await browser.newPage()
    await page.addInitScript(() => {
      localStorage.setItem('dodu.live.session.v1', 'synthetic-permission-session')
      window.cameraState = 'denied'
      Object.defineProperty(window, 'Notification', { configurable: true, value: { permission: 'granted' } })
      Object.defineProperty(navigator.permissions, 'query', { configurable: true, value: async () => {
        if (window.unsupportedQuery) throw Error('synthetic-unsupported-query')
        const status = new EventTarget(); status.state = window.cameraState; return status
      } })
      Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => ({ getTracks: () => [{ stop() {} }] }) })
    })
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname
      const data = path.endsWith('/me') ? { onboardingCompleted: true, expiresAt: '2099-01-01T00:00:00Z' } : path.endsWith('/active') ? null : []
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(path.endsWith('/active') || path.endsWith('/history') ? data : { status: 'success', data, serverTime: new Date().toISOString() }) })
    })
    await page.goto(process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4181')
    await page.getByRole('heading', { name: '카메라 권한이 필요해요', exact: true }).waitFor()
    assert.equal(await page.locator('.home-page:visible').count(), 0)
    console.log('PASS: initial denied permission hides home')
    await page.evaluate(() => { window.cameraState = 'granted'; window.dispatchEvent(new Event('focus')) })
    await page.locator('.home-page:visible').waitFor()
    console.log('PASS: granted permission restores home')
    // 일부 브라우저는 권한 change 이벤트나 focus 이벤트를 전달하지 않는다.
    await page.evaluate(() => { window.cameraState = 'denied' })
    await page.getByRole('heading', { name: '카메라 권한이 필요해요', exact: true }).waitFor({ timeout: 5000 })
    assert.equal(await page.locator('.home-page:visible').count(), 0)
    await page.evaluate(() => { window.unsupportedQuery = true; window.dispatchEvent(new Event('focus')) })
    await page.getByRole('button', { name: '다시 확인', exact: true }).click()
    await page.locator('.home-page:visible').waitFor()
    await page.waitForTimeout(1500)
    assert.equal(await page.locator('.home-page:visible').count(), 1, 'Unsupported periodic queries must not discard an explicit camera check')
    console.log('PASS: 온보딩 완료 후 카메라 차단 시 홈 미표시·권한 복원·이벤트 없는 권한 철회 차단 (합성 API)')
  } finally { await browser.close() }
}
main().catch(() => { console.error('FAIL: permission gate browser verification'); process.exitCode = 1 })
