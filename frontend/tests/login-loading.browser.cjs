const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ headless: true, ...(process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}) })
  let releaseLogin, releaseMe, releaseHistory
  const login = new Promise(resolve => { releaseLogin = resolve })
  const me = new Promise(resolve => { releaseMe = resolve })
  const history = new Promise(resolve => { releaseHistory = resolve })
  try {
    const page = await browser.newPage()
    const errors = []; page.on('pageerror', () => errors.push('pageerror'))
    await page.addInitScript(() => {
      Object.defineProperty(window, 'Notification', { configurable: true, value: { permission: 'granted' } })
      Object.defineProperty(navigator.permissions, 'query', { configurable: true, value: async () => {
        const status = new EventTarget(); status.state = 'granted'; return status
      } })
    })
    let sessions = 0, meRequested = false, historyRequested = false
    const envelope = data => ({ status: 'success', data, serverTime: new Date().toISOString() })
    const participant = { onboardingCompleted: true, expiresAt: '2099-01-01T00:00:00Z' }
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname; let data
      if (path.endsWith('/sessions')) {
        sessions++; await login
        data = envelope({ ...participant, sessionToken: 'synthetic-loading-session', firstEntry: false })
      } else if (path.endsWith('/me')) { meRequested = true; await me; data = envelope(participant) }
      else if (path.endsWith('/active')) data = null
      else if (path.endsWith('/history')) { historyRequested = true; await history; data = [] }
      else data = envelope([])
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) })
    })
    await page.goto(process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4181')
    await page.getByLabel('참가자 식별 코드', { exact: true }).fill('SYNTHETIC123')
    await page.getByRole('button', { name: '입장하기', exact: true }).click()
    await page.getByRole('button', { name: '확인 중…', exact: true }).waitFor()
    assert.equal(meRequested, false)
    releaseLogin()
    await page.getByText('로그인 정보를 확인하고 약속을 불러오고 있어요.', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('참가자 식별 코드', { exact: true }).count(), 0)
    assert.equal(await page.locator('.loading-indicator').isVisible(), true)
    assert.equal(await page.locator('.loading-dots').evaluate(el => getComputedStyle(el, '::after').animationName), 'loading-dots')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    assert.equal(await page.locator('.loading-dots').evaluate(el => getComputedStyle(el, '::after').animationName), 'none')
    assert.equal(await page.locator('.screen-spinner').evaluate(el => getComputedStyle(el).animationName), 'none')
    releaseMe()
    await page.locator('.home-page:visible').waitFor()
    assert.equal(historyRequested, true)
    assert.equal(await page.getByRole('button', { name: '약속 만들기', exact: true }).isEnabled(), true)
    assert.equal(await page.getByRole('button', { name: '약속 만들기', exact: true }).evaluate(el => Boolean(el.closest('[inert]'))), false)
    releaseHistory()
    assert.equal(sessions, 1)
    assert.deepEqual(errors, [])
    console.log('PASS: 로그인 응답 전후 화면 분리·점/회전 애니메이션·동작 줄이기·이력 대기 중 홈 조작·로그인 중복 없음 (합성 API)')
  } finally { releaseLogin(); releaseMe(); releaseHistory(); await browser.close() }
}
main().catch(() => { console.error('FAIL: login loading browser verification'); process.exitCode = 1 })
