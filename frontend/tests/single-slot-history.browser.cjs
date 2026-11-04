const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ ...(process.env.DODU_CHROME_PATH ? { executablePath: process.env.DODU_CHROME_PATH } : process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}), headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 375, height: 812 } }); page.setDefaultTimeout(7000)
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    await page.addInitScript(() => {
      sessionStorage.setItem('dodu.live.session.v1', 'synthetic-only-session')
      Object.defineProperty(window, 'Notification', { value: { permission: 'granted' }, configurable: true })
      const status = new EventTarget(); status.state = 'granted'
      Object.defineProperty(navigator.permissions, 'query', { value: async () => status, configurable: true })
    })
    const now = Date.now(), envelope = data => ({ status: 'success', data, serverTime: new Date(now).toISOString() })
    const past = new Date(now - 3600000).toISOString()
    const history = ['AuthCompleted', 'AuthIncomplete', 'TechFailed', 'Cancelled', 'Withdrawn'].map((finalOutcome, i) => ({
      promiseId: 'past-' + i, taskTitle: '기존 약속 ' + i, toolType: 'BOOK', scheduledAt: past, version: 0, lifecycleStatus: 'Closed', finalOutcome,
    }))
    let active = null, failed = true, creates = 0
    await page.route('**/api/**', async route => {
      const req = route.request(), path = new URL(req.url()).pathname; let data, status = 200
      if (path.endsWith('/me')) data = envelope({ onboardingCompleted: true, expiresAt: new Date(now + 86400000).toISOString() })
      else if (path.endsWith('/active')) data = active
      else if (path.endsWith('/history')) { status = failed ? 503 : 200; data = failed ? { code: 'UNAVAILABLE' } : history }
      else if (path.endsWith('/notifications')) data = envelope([])
      else if (path === '/api/promises' && req.method() === 'POST') { creates++; active = { ...req.postDataJSON(), promiseId: 'next-promise', version: 0 }; data = active }
      else throw Error('Unexpected synthetic route ' + path)
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
    })
    await page.goto(process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4175')
    await page.getByText('지난 약속 기록을 확인할 수 없어요', { exact: true }).waitFor()
    await page.getByRole('button', { name: '전체 보기', exact: true }).click()
    await page.getByRole('heading', { name: '약속 기록', exact: true }).waitFor()
    failed = false
    await page.getByRole('button', { name: '다시 확인하기', exact: true }).click()
    await page.locator('.history-list li').first().waitFor()
    assert.equal(await page.locator('.history-list li').count(), 5)
    for (const status of ['인증 완료', '인증 미완료', '기술 문제', '취소', '참여 철회']) assert.equal(await page.getByText(status, { exact: true }).count(), 1)
    await page.locator('.screen-header button').click()
    const progress = page.getByRole('progressbar')
    await progress.waitFor(); assert.equal(await progress.getAttribute('aria-valuemax'), '2')
    assert.equal(await progress.getAttribute('aria-valuenow'), '1')
    await page.getByRole('button', { name: '약속 만들기', exact: true }).click()
    await page.locator('input[type="text"]').fill('같은 날 다음 약속')
    await page.getByRole('button', { name: '다음으로', exact: true }).click()
    await page.locator('.promise-date-chip--selected').click()
    await page.getByRole('button', { name: '다음으로', exact: true }).click()
    await page.getByRole('button', { name: '다음으로', exact: true }).click()
    await page.getByRole('button', { name: '약속 저장', exact: true }).click()
    await page.locator('.today-promise-name').getByText('같은 날 다음 약속', { exact: true }).waitFor()
    assert.equal(creates, 1); assert.deepEqual(errors, [])
    console.log('PASS: 전체보기 오류 복구·동일 날짜 5건/종료 사유 보존·집계 2건만 포함·종료 후 다음 약속 생성 (합성 API)')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e.message); process.exitCode = 1 })
