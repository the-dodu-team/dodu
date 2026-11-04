const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ ...(process.env.DODU_CHROME_PATH ? { executablePath: process.env.DODU_CHROME_PATH } : process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}), headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 375, height: 812 } }); page.setDefaultTimeout(7000)
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    await page.addInitScript(() => {
      sessionStorage.setItem('dodu.live.session.v1', 'synthetic-only-session')
      window.noticeState = 'granted'; window.cameraState = 'granted'; window.cameraAllowed = true; window.cameraStops = 0
      Object.defineProperty(window, 'Notification', { value: { get permission() { return window.noticeState },
        requestPermission: async () => { window.noticeState = 'granted'; return 'granted' } }, configurable: true })
      const camera = new EventTarget(); Object.defineProperty(camera, 'state', { get: () => window.cameraState })
      Object.defineProperty(navigator.permissions, 'query', { value: async ({ name }) => {
        if (name === 'camera') return camera
        throw Error('unsupported synthetic permission')
      }, configurable: true })
      window.changeCamera = state => { window.cameraState = state; camera.dispatchEvent(new Event('change')) }
      Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { value: async () => {
        if (!window.cameraAllowed) throw new DOMException('private camera detail', 'NotAllowedError')
        window.cameraState = 'granted'; return { getTracks: () => [{ stop: () => window.cameraStops++ }] }
      }, configurable: true })
    })
    const now = Date.now(), promiseId = '00000000-0000-0000-0000-000000000002'
    let claimed = false, claims = 0, skipped = false, skips = 0
    const envelope = data => ({ status: 'success', data, serverTime: new Date(now).toISOString() })
    await page.route('**/api/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname; let data
      if (path.endsWith('/me')) data = envelope({ onboardingCompleted: true, expiresAt: new Date(now + 86400000).toISOString() })
      else if (path.endsWith('/active')) data = { promiseId: 'active', taskTitle: '진행 중 약속', scheduledAt: new Date(now - 60000).toISOString(), toolType: 'COMPUTER', version: 0 }
      else if (path.endsWith('/history')) data = [{ promiseId, taskTitle: '어제 약속', scheduledAt: new Date(now - 86400000).toISOString(), toolType: 'BOOK', version: 0, finalOutcome: 'AuthCompleted', lifecycleStatus: 'Closed' }]
      else if (path.endsWith('/notifications')) data = envelope([{ promiseId, notificationType: 'SURVEY_REMINDER' }])
      else if (path.endsWith('/inapp-reminder')) { claims++; data = envelope({ show: !claimed }); claimed = true }
      else if (path.endsWith('/auth-attempts')) data = []
      else {
        if (path.endsWith('/skip')) { skipped = true; skips++ }
        if (!path.includes('validation-survey')) throw Error('Unexpected synthetic path ' + path)
        data = { id: 'survey', questionnaireVersion: 'v2.5.2', lifecycleStatus: skipped ? 'Skipped' : 'Prompted', eligibilityStatus: 'AuthCompleted',
          actualStarted: null, startOrder: null, startDelayBucket: null, noStartReason: null,
          availableAt: new Date(now - 3600000).toISOString(), expiresAt: new Date(now + 86400000).toISOString(), submittedAt: null }
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
    })
    await page.goto((process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4175'))
    await page.getByRole('dialog').waitFor()
    assert.equal(claims, 1)
    assert.equal(await page.getByRole('dialog').getByText(/어제 약속/).count(), 1)
    await page.keyboard.press('Escape')
    assert.equal(skips, 0)
    await page.getByRole('button', { name: '지금 작성하기', exact: true }).click()
    await page.getByRole('heading', { name: '지난 약속은 어떠셨나요?', exact: true }).waitFor()
    await page.getByRole('button', { name: '닫기', exact: true }).click()
    await page.getByRole('button', { name: '약속 인증하기', exact: true }).waitFor()
    assert.equal(await page.getByRole('dialog').count(), 0)
    assert.equal(skips, 0)
    await page.getByRole('button', { name: '약속 인증하기', exact: true }).click()
    await page.getByRole('button', { name: '카메라 열기', exact: true }).waitFor()
    await page.evaluate(() => { window.noticeState = 'denied'; window.dispatchEvent(new Event('focus')) })
    await page.getByRole('heading', { name: '알림 권한이 필요해요', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '카메라 열기', exact: true }).count(), 0)
    await page.evaluate(() => { window.noticeState = 'default'; window.cameraAllowed = false; window.changeCamera('denied') })
    await page.getByRole('button', { name: '다시 확인', exact: true }).click()
    await page.getByRole('heading', { name: '카메라 권한이 필요해요', exact: true }).waitFor()
    assert.doesNotMatch(await page.locator('body').innerText(), /private camera detail/)
    await page.evaluate(() => { window.cameraAllowed = true })
    await page.getByRole('button', { name: '다시 확인', exact: true }).click()
    await page.getByRole('button', { name: '카메라 열기', exact: true }).waitFor()
    assert.equal(await page.evaluate(() => window.cameraStops), 1)
    await page.getByRole('button', { name: '닫기', exact: true }).click()
    await page.waitForTimeout(500)
    claimed = false; await page.reload(); await page.getByRole('dialog').waitFor()
    await page.getByRole('dialog').getByRole('button', { name: '건너뛰기', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'detached' })
    assert.equal(skips, 1)
    await page.reload(); await page.locator('.home-page').waitFor()
    assert.equal(await page.getByRole('dialog').count(), 0)
    assert.equal(await page.getByRole('button', { name: '지금 작성하기', exact: true }).count(), 0)
    await page.evaluate(() => {
      Object.defineProperty(navigator.permissions, 'query', { configurable: true, value: async () => { throw Error('unsupported query') } })
      window.dispatchEvent(new Event('focus'))
    })
    await page.getByRole('heading', { name: '카메라 권한이 필요해요', exact: true }).waitFor()
    await page.setViewportSize({ width: 320, height: 812 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    const previousStops = await page.evaluate(() => window.cameraStops)
    await page.getByRole('button', { name: '다시 확인', exact: true }).click()
    await page.locator('.home-page').waitFor()
    assert.equal(await page.evaluate(() => window.cameraStops), previousStops + 1)
    assert.deepEqual(errors, [])
    console.log('PASS: StrictMode 재안내 1회·닫기 응답 보존·재진입 미노출·명시적 건너뛰기 종료, 권한 철회 차단·알림/카메라 복구·원래 인증 화면 복귀·트랙 종료·JS 오류 0 (합성 API)')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e.message); process.exitCode = 1 })
