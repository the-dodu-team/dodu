const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const root = path.resolve(__dirname, '..')
const req = createRequire(root + '/package.json')
const ts = req('typescript')
const React = req('react')
const { renderToStaticMarkup } = req('react-dom/server')
const cache = new Map()
function load(file) {
  if (!path.extname(file)) file = ['.tsx', '.ts'].map(ext => file + ext).find(fs.existsSync)
  if (/\.(svg|png|css)$/.test(file)) return { default: file }
  if (cache.has(file)) return cache.get(file).exports
  const mod = { exports: {} }; cache.set(file, mod)
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022,
  }}).outputText
  new Function('require', 'module', 'exports', code)(name => name.startsWith('.')
    ? load(path.resolve(path.dirname(file), name)) : req(name), mod, mod.exports)
  return mod.exports
}
const {getFinalPromiseRecord: final, getWeeklyFinalRecords: weekly, formatAuthWindow: format} = load(root + '/src/features/promise/promiseHistory.ts')
const Home = load(root + '/src/DoduHome.tsx').default
const Auth = load(root + '/src/features/promise/PromiseAuthPreview.tsx').default
const Card = load(root + '/src/components/WeeklyPromiseCard.tsx').default
const { getInterventionEndError } = load(root + '/src/features/promise/validateInterventionEnd.ts')
const officialEnd = Date.parse('2026-10-19T10:35:00+09:00')
assert.equal(getInterventionEndError(officialEnd - 30 * 60_000, officialEnd), '')
assert.equal(getInterventionEndError(officialEnd - 30 * 60_000 - 1, officialEnd), '')
assert.equal(getInterventionEndError(officialEnd - 30 * 60_000 + 1, officialEnd), '행동검증 종료시간 30분 전부터는 약속을 생성할 수 없어요')
assert.notEqual(getInterventionEndError(officialEnd, officialEnd), '')
const TimePage = load(root + '/src/features/promise/PromiseTimePage.tsx').default
const NativeDate = Date
let current = NativeDate.parse('2026-10-18T12:05:00+09:00')
global.Date = class extends NativeDate {
  constructor(...args) { super(...(args.length ? args : [current])) }
  static now() { return current }
}
const render = (Component, props) => renderToStaticMarkup(React.createElement(Component, props))
current = Date.parse('2026-10-19T10:00:00+09:00')
for (const mode of ['create', 'edit']) {
  const html = render(TimePage, {initialValue:'2026-10-19T10:10:00+09:00',mode,interventionEndsAt:officialEnd,onBack(){},onNext(){}})
  assert.ok(html.includes('행동검증 종료시간 30분 전부터는 약속을 생성할 수 없어요'))
  assert.ok(html.includes('aria-invalid="true"'))
  assert.ok(html.includes('disabled'))
}
current = Date.parse('2026-10-18T12:05:00+09:00')
const p = {date:'2026-10-18', startTime:'12:00', taskName:'테스트', tool:'book', status:'pending'}
const old = {date:'2026-10-17', status:'verified'}
const home = promise => render(Home, {promise, pastResults:[old], onCreate(){}, onEdit(){}, onVerify(){}})
const count = (html, total, success) => assert.ok(html.includes(`${total}번 중 <strong>${success}번</strong>`), html)
count(home(p), 1, 1)
count(home({...p,status:'verified'}), 2, 2)
count(home({...p,status:'unverified'}), 2, 1)
for (const status of ['evaluating','techFailed','permissionBlocked','cancelled','withdrawn']) {
  count(home({...p,status}),1,1)
  assert.equal(final({...p,status}, Date.parse('2026-10-18T13:00:00+09:00')),null)
}
const end = Date.parse('2026-10-18T12:30:00+09:00')
assert.equal(final(p,end-1),null)
assert.deepEqual(final(p,end),{date:p.date,status:'unverified'})
current=end; count(home(p),2,1); count(home(p),2,1)
const boundaries = ['2026-10-11','2026-10-12','2026-10-18','2026-10-19'].map(date=>({date,status:'verified'}))
assert.deepEqual(weekly(boundaries,'2026-10-18').map(r=>r.date),['2026-10-12','2026-10-18'])
current=Date.parse('2026-10-19T00:00:00+09:00')
assert.ok(home({...p,status:'verified'}).includes('이번 주의 지난 약속이 없어요'))
const empty = render(Home,{promise:null,onCreate(){},onEdit(){},onVerify(){}})
for(const text of ['다가오는 약속','오늘은 약속이 없어요','오늘 해야 할 일이 있나요?','약속을 만들어보세요','약속 만들기','지난 약속','전체 보기','이번 주의 지난 약속이 없어요','약속이 있다면 알려드릴게요']) assert.ok(empty.includes(text),text)
assert.ok(!empty.includes('role="progressbar"'))
count(render(Card,{records:[{date:'2026-10-19',status:'unverified'}],todayKey:'2026-10-19'}),1,0)
assert.ok(render(Card,{records:[{date:'2026-10-19',status:'unverified'}],todayKey:'2026-10-19'}).includes('width:0%'))
const cases = [
 ['2026-10-18T21:05:00+09:00','2026-10-18T21:35:00+09:00','10월 18일 오후 9:05 ~ 오후 9:35'],
 ['2026-10-18T23:50:00+09:00','2026-10-19T00:20:00+09:00','10월 18일 오후 11:50 ~ 10월 19일 오전 12:20'],
 ['2026-12-31T23:50:00+09:00','2027-01-01T00:20:00+09:00','12월 31일 오후 11:50 ~ 1월 1일 오전 12:20'],
 ['2026-10-18T11:50:00+09:00','2026-10-18T12:20:00+09:00','10월 18일 오전 11:50 ~ 오후 12:20'],
]
for(const [start,end,label] of cases) assert.equal(format(Date.parse(start),Date.parse(end)),label)
current=Date.parse('2026-10-18T11:59:59+09:00')
let html=render(Auth,{promise:p,onVerify(){}})
assert.ok(html.includes('10월 18일 오후 12:00 ~ 오후 12:30 사이에 인증할 수 있어요'))
assert.ok(!html.includes('<button'))
current=Date.parse('2026-10-18T12:00:00+09:00')
assert.ok(!render(Auth,{promise:p,onVerify(){}}).includes('disabled'))
current=end
assert.ok(render(Auth,{promise:p,onVerify(){}}).includes('disabled'))
assert.ok(render(Auth,{promise:p,onVerify(){}}).includes('인증 시간 종료'))
assert.ok(render(Auth,{promise:{...p,status:'evaluating'},onVerify(){}}).includes('사진 판정 결과를 기다리고 있어요.'))
global.Date=NativeDate
console.log('PASS: official-end boundaries, create/edit errors, actual-source helper and React rendering tests: final-only counts, excluded states, deadline, Monday/Sunday rollover, empty design copy, 0% progress, Korean date windows, midnight/year/noon boundaries, button timing.')
