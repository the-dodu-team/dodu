import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

test('rendered pages expose named sections, a single main, and native form/date semantics', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  try {
    const render = async (path, props = {}) => {
      const module = await server.ssrLoadModule(path)
      return renderToStaticMarkup(createElement(module.default, props))
    }
    const home = await render('/src/App.tsx')
    assert.equal((home.match(/<main\b/g) ?? []).length, 1)
    assert.equal((home.match(/<h1\b/g) ?? []).length, 1)
    assert.match(home, /<header class="app-header">[\s\S]*?<\/header><main/)
    assert.match(home, /<section[^>]*aria-labelledby="today-promise-title"/)
    assert.match(home, /<section[^>]*aria-labelledby="weekly-promise-title"/)
    assert.equal((home.match(/<time datetime=/gi) ?? []).length, 7)

    const draft = { taskName: '공부', startTime: '18:00', tool: 'computer' }
    const form = await render('/src/features/promise/PromiseFormPage.tsx', {
      draft, setDraft: () => {}, isEditing: false, onConfirm: () => {}, onBack: () => {},
    })
    assert.match(form, /<section[^>]*aria-labelledby="promise-form-title"/)
    assert.match(form, /<form aria-labelledby="promise-form-title"/)
    assert.match(form, /<fieldset[\s\S]*?<legend>인증 도구<\/legend>/)
    assert.match(form, /<label for="promise-task-name">/)

    const confirm = await render('/src/features/promise/PromiseConfirmPage.tsx', {
      draft, onEdit: () => {}, onPreview: () => {},
    })
    assert.match(confirm, /aria-labelledby="promise-confirm-title"/)
    assert.match(confirm, /<dl class="promise-summary">/)

    const promise = { ...draft, date: '2026-10-07', status: 'pending' }
    const history = await render('/src/features/promise/PromiseHistoryPage.tsx', {
      promise, onBack: () => {},
    })
    assert.match(history, /aria-labelledby="promise-history-title"/)
    assert.match(history, /<time datetime="2026-10-07">/i)
    const auth = await render('/src/features/promise/PromiseAuthPreview.tsx', {
      promise, onVerify: () => {},
    })
    assert.match(auth, /<section[^>]*aria-labelledby="promise-auth-preview-title"/)
    assert.match(auth, /<h2 id="promise-auth-preview-title"/)
  } finally {
    await server.close()
  }
})
