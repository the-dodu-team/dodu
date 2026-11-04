const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ ...(process.env.DODU_CHROME_PATH ? { executablePath: process.env.DODU_CHROME_PATH } : process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}), headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 375, height: 812 } }); page.setDefaultTimeout(7000)
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    await page.addInitScript(() => {
      Object.defineProperty(window, 'Notification', { configurable: true, value: { permission: 'granted', requestPermission: async () => 'granted' } })
      const permission = new EventTarget(); permission.state = 'granted'
      Object.defineProperty(navigator.permissions, 'query', { configurable: true, value: async () => permission })
    })
    const now = new Date().toISOString(), envelope = data => ({ status: 'success', data, serverTime: now })
    let row = null, logins = 0, creates = 0, unsubscribes = 0
    const activeTokens = []
    await page.route('**/api/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname; let data
      if (path.endsWith('/sessions')) {
        assert.equal(request.postDataJSON().code, 'SYNTHETIC123')
        logins++
        data = envelope({ sessionToken: 'synthetic-session-' + logins, onboardingCompleted: true, expiresAt: new Date(Date.now() + 86400000).toISOString() })
      } else if (path.endsWith('/me')) data = envelope({ onboardingCompleted: true, expiresAt: new Date(Date.now() + 86400000).toISOString() })
      else if (path.endsWith('/active')) { activeTokens.push(request.headers().authorization); data = row }
      else if (path.endsWith('/history')) data = []
      else if (path.endsWith('/notifications')) data = envelope([])
      else if (path.endsWith('/notification-subscription')) { assert.equal(request.method(), 'DELETE'); unsubscribes++; data = envelope(null) }
      else if (path === '/api/promises' && request.method() === 'POST') {
        creates++; row = { ...request.postDataJSON(), promiseId: 'saved-promise', version: 0 }; data = row
      } else throw Error('Unexpected synthetic API ' + path)
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
    })
    await page.goto(process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4175')
    async function login() {
      await page.getByLabel('참가자 식별 코드', { exact: true }).fill('SYNTHETIC123')
      await page.getByRole('button', { name: '입장하기', exact: true }).click()
      await page.locator('.home-page').waitFor()
    }
    await login()
    await page.getByRole('button', { name: '약속 만들기', exact: true }).click()
    await page.locator('input[type="text"]').fill('재입장 보존 테스트')
    await page.getByRole('button', { name: '다음으로', exact: true }).click()
    await page.locator('.promise-date-chip--selected').click()
    await page.getByRole('button', { name: '다음으로', exact: true }).click()
    await page.getByRole('button', { name: '다음으로', exact: true }).click()
    await page.getByRole('button', { name: '약속 저장', exact: true }).click()
    await page.locator('.today-promise-name').getByText('재입장 보존 테스트', { exact: true }).waitFor()
    await page.getByRole('button', { name: '설정', exact: true }).click()
    await page.getByRole('button', { name: '로그아웃', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '로그아웃', exact: true }).click()
    await login()
    await page.locator('.today-promise-name').getByText('재입장 보존 테스트', { exact: true }).waitFor()
    assert.equal(creates, 1); assert.equal(unsubscribes, 1); assert.equal(logins, 2)
    assert.ok(activeTokens.includes('Bearer synthetic-session-2'))
    await page.reload()
    await page.locator('.today-promise-name').getByText('재입장 보존 테스트', { exact: true }).waitFor()
    assert.equal(creates, 1)
    assert.deepEqual(errors, [])
    console.log('PASS: 실제 생성 폼→홈 카드→로그아웃→같은 코드 재입장→새 세션으로 기존 약속 조회→새로고침 보존 (합성 API)')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e.message); process.exitCode = 1 })
