const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ headless: true, ...(process.platform === 'win32' ? { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' } : {}) })
  try {
    const page = await browser.newPage()
    const url = process.env.DODU_TEST_BASE_URL || 'http://127.0.0.1:4181'
    await page.route('**/api/participant/sessions', route => route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ status: 'error', error: { code: 'ACCESS_CODE_INVALID', message: 'synthetic-private-detail' } }) }))
    await page.goto(url)
    await page.getByLabel('참가자 식별 코드', { exact: true }).fill('SYNTHETIC123')
    await page.getByRole('button', { name: '입장하기', exact: true }).click()
    await page.locator('.feedback-toast.is-error').waitFor()
    assert.match(await page.locator('.feedback-toast.is-error').innerText(), /코드를 다시 확인/)
    assert.doesNotMatch(await page.locator('body').innerText(), /synthetic-private-detail/)
    await page.getByRole('button', { name: '실패 안내 닫기' }).click()
    assert.equal(await page.locator('.feedback-toast').count(), 0)
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname
      const data = path.endsWith('/sessions') ? { sessionToken: 'synthetic-welcome-session', onboardingCompleted: false, expiresAt: '2099-01-01T00:00:00Z' }
        : path.endsWith('/me') ? { onboardingCompleted: false, expiresAt: '2099-01-01T00:00:00Z' }
        : path.endsWith('/active') ? null : []
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(path.endsWith('/active') || path.endsWith('/history') ? data : { status: 'success', data, serverTime: new Date().toISOString() }) })
    })
    await page.getByRole('button', { name: '입장하기', exact: true }).click()
    await page.getByText('Dodu에 오신 걸 환영합니다!', { exact: true }).waitFor()
    await page.evaluate(() => window.dispatchEvent(new CustomEvent('dodu:feedback', { detail: { kind: 'success', message: '약속이 저장됐어요.' } })))
    await page.locator('.feedback-toast.is-success').waitFor()
    assert.equal(await page.locator('[role="status"] .feedback-toast').count(), 1)
    await page.locator('.feedback-toast.is-success').waitFor({ state: 'detached', timeout: 7000 })
    await page.evaluate(() => window.dispatchEvent(new PromiseRejectionEvent('unhandledrejection', { promise: Promise.resolve(), reason: new Error('synthetic-private-rejection') })))
    await page.locator('.feedback-toast.is-error').waitFor()
    assert.doesNotMatch(await page.locator('body').innerText(), /synthetic-private-rejection/)
    await page.evaluate(() => window.dispatchEvent(new ErrorEvent('error', { error: new Error('synthetic-private-handler') })))
    assert.doesNotMatch(await page.locator('.feedback-toast.is-error').innerText(), /synthetic-private-handler/)
    await page.evaluate(() => sessionStorage.setItem('dodu.live.pending.v1', '{invalid-json'))
    await page.reload()
    await page.getByRole('heading', { name: '화면을 표시하지 못했어요' }).waitFor()
    assert.doesNotMatch(await page.locator('body').innerText(), /SyntaxError|invalid-json/)
    await page.evaluate(() => sessionStorage.removeItem('dodu.live.pending.v1'))
    await page.evaluate(() => localStorage.clear())
    await page.getByRole('button', { name: '다시 접속하기' }).click()
    await page.getByLabel('참가자 식별 코드', { exact: true }).waitFor()
    console.log('PASS: 실제 로그인 실패 토스트·성공 자동 해제·실패 닫기·미처리 비동기/이벤트 오류 비노출·렌더 오류 대체 화면 및 재접속 (합성 API)')
  } finally { await browser.close() }
}
main().catch(() => { console.error('FAIL: feedback browser verification'); process.exitCode = 1 })
