const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const Module = require('node:module')

const file = path.join(__dirname, '../src/api/doduApi.ts')
const compiled = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const loaded = new Module(file, module)
loaded.filename = file
loaded.paths = module.paths
loaded._compile(compiled, file)
const { createDoduAccessApi, createDoduApi, DoduApiError, DoduRequestTimeout, fetchDoduJson } = loaded.exports

async function main() {
  const previousFetch = global.fetch
  try {
    const data = { sessionToken: 'synthetic-session', expiresAt: '2026-11-13T00:00:00Z', onboardingCompleted: false }
    global.fetch = async (url, init) => {
      assert.equal(url, 'https://example.invalid/api/participant/sessions')
      assert.equal(init.method, 'POST')
      assert.deepEqual(JSON.parse(init.body), { code: 'synthetic-code', deviceKey: 'synthetic-device' })
      return new Response(JSON.stringify({ status: 'success', data }), { status: 200 })
    }
    assert.deepEqual(await createDoduAccessApi('https://example.invalid').enter('synthetic-code', 'synthetic-device'), data)

    const failure = { status: 'error', error: { code: 'ACCESS_CODE_INVALID', message: 'Invalid code' } }
    global.fetch = async () => new Response(JSON.stringify(failure), { status: 401 })
    await assert.rejects(createDoduAccessApi().enter('synthetic-code', 'synthetic-device'), error => {
      assert.ok(error instanceof DoduApiError)
      assert.equal(error.httpStatus, 401)
      assert.equal(error.response.error.code, 'ACCESS_CODE_INVALID')
      return true
    })
    let calls = 0
    let signal
    global.fetch = (_url, init) => { calls++; signal = init.signal; return new Promise(() => {}) }
    await assert.rejects(fetchDoduJson('https://example.invalid', {}, 5), DoduRequestTimeout)
    assert.equal(signal.aborted, true)
    assert.equal(calls, 1, 'Timed-out writes must not retry automatically')

    global.fetch = async () => ({ text: () => new Promise(() => {}) })
    await assert.rejects(fetchDoduJson('https://example.invalid', {}, 5), DoduRequestTimeout)

    global.fetch = async () => new Response('', { status: 200 })
    assert.equal(await createDoduApi('synthetic-session').getActivePromise(), null)
    global.fetch = async () => new Response('null', { status: 200 })
    assert.equal(await createDoduApi('synthetic-session').getActivePromise(), null)
    console.log('PASS: participant entry contract, bounded fetch/body waits, no automatic retry, and empty active promise')
  } finally {
    global.fetch = previousFetch
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
