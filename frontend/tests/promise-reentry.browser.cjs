const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ ...(process.env.DODU_CHROME_PATH ? { executablePath: process.env.DODU_CHROME_PATH } : process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}), headless: true })
  try {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true })
    let page = await context.newPage(); page.setDefaultTimeout(7000)
    const errors = []; context.on('page', tab => tab.on('pageerror', e => errors.push(e.message)))
    page.on('pageerror', e => errors.push(e.message))
    await context.addInitScript(() => {
      Object.defineProperty(window, 'Notification', { configurable: true, value: { permission: 'granted', requestPermission: async () => 'granted' } })
      const permission = new EventTarget(); permission.state = 'granted'
      Object.defineProperty(navigator.permissions, 'query', { configurable: true, value: async () => permission })
    })
    const now = new Date().toISOString(), envelope = data => ({ status: 'success', data, serverTime: now })
    let row = null, logins = 0, creates = 0, unsubscribes = 0, expired = false
    const activeTokens = []
    await context.route('**/api/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname; let data
      if (path.endsWith('/sessions')) {
        assert.equal(request.postDataJSON().code, 'SYNTHETIC123')
        logins++
        data = envelope({ sessionToken: 'synthetic-session-' + logins, onboardingCompleted: true, expiresAt: new Date(Date.now() + 86400000).toISOString() })
      } else if (path.endsWith('/me')) data = envelope({ onboardingCompleted: true, expiresAt: new Date(Date.now() + 86400000).toISOString() })
      else if (path.endsWith('/active')) { activeTokens.push(request.headers().authorization); data = expired ? null : row }
      else if (path.endsWith('/history')) data = expired ? [{ ...row, scheduledAt: new Date(Date.now() - 3600000).toISOString(), lifecycleStatus: 'Scheduled', finalOutcome: 'AuthIncomplete' }] : []
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
    const time = page.locator('#promise-selected-time')
    assert.equal(await time.getAttribute('type'), 'text')
    assert.equal(await time.getAttribute('inputmode'), 'numeric')
    const defaultTime = await time.inputValue()
    await time.fill('2460')
    assert.equal(await page.getByRole('button', { name: '다음으로', exact: true }).isDisabled(), true)
    await time.fill(defaultTime.replace(':', ''))
    await time.blur()
    assert.equal(await time.inputValue(), defaultTime)
    assert.equal(await page.locator('.promise-time-wheel').count(), 0)
    await page.getByRole('button', { name: '다음으로', exact: true }).click()
    await page.getByRole('button', { name: '다음으로', exact: true }).click()
    await page.getByRole('button', { name: '약속 저장', exact: true }).click()
    await page.locator('.today-promise-name').getByText('재입장 보존 테스트', { exact: true }).waitFor()
    await page.getByText('약속이 저장됐어요.', { exact: true }).waitFor()
    await page.close()
    page = await context.newPage(); page.setDefaultTimeout(7000)
    await page.goto(process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4175')
    await page.locator('.today-promise-name').getByText('재입장 보존 테스트', { exact: true }).waitFor()
    assert.equal(logins, 1, 'Closing and reopening must restore the session without code entry')
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
    await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
    await page.reload()
    await login()
    await page.locator('.today-promise-name').getByText('재입장 보존 테스트', { exact: true }).waitFor()
    assert.equal(creates, 1, 'Re-entry after storage deletion restores the existing server promise')
    expired = true
    await page.reload()
    await page.getByRole('button', { name: '전체 보기', exact: true }).click()
    await page.locator('.history-chip.unverified').waitFor()
    assert.equal(await page.locator('.history-chip.unverified').innerText(), '인증 미완료')
    assert.deepEqual(errors, [])
    console.log('PASS: 생성→홈 카드→로그아웃→동일 코드→새 세션 조회→새로고침 보존·마감 미제출 이력 표시 (합성 API)')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e.message); process.exitCode = 1 })
