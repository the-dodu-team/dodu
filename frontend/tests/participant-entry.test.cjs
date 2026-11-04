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
const { createDoduAccessApi, DoduApiError } = loaded.exports

async function main() {
  const previousFetch = global.fetch
  try {
    const data = { sessionToken: 'synthetic-session', expiresAt: '2026-11-13T00:00:00Z', onboardingCompleted: false }
    global.fetch = async (url, init) => {
      assert.equal(url, 'https://example.invalid/api/participant/sessions')
      assert.equal(init.method, 'POST')
      assert.deepEqual(JSON.parse(init.body), { code: 'synthetic-code', deviceKey: 'synthetic-device' })
      return { ok: true, status: 200, json: async () => ({ status: 'success', data }) }
    }
    assert.deepEqual(await createDoduAccessApi('https://example.invalid').enter('synthetic-code', 'synthetic-device'), data)

    const failure = { status: 'error', error: { code: 'ACCESS_CODE_INVALID', message: 'Invalid code' } }
    global.fetch = async () => ({ ok: false, status: 401, json: async () => failure })
    await assert.rejects(createDoduAccessApi().enter('synthetic-code', 'synthetic-device'), error => {
      assert.ok(error instanceof DoduApiError)
      assert.equal(error.httpStatus, 401)
      assert.equal(error.response.error.code, 'ACCESS_CODE_INVALID')
      return true
    })
    console.log('PASS: participant entry uses current session endpoint and unwraps success/error envelopes')
  } finally {
    global.fetch = previousFetch
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
