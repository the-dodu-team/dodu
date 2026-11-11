import { createRequire } from 'node:module'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
// 기존 workspace의 TypeScript를 재사용한다. 별도 전역 설치를 요구하지 않는다.
const require = createRequire(new URL('../frontend/package.json', import.meta.url))
for (const args of [[require.resolve('typescript/bin/tsc'), '-p', 'mobile/tsconfig.json'], ['--test', 'mobile/tests/native-session.test.cjs']]) {
  const result = spawnSync(process.execPath, args, { cwd: root, stdio: 'inherit' })
  if (result.status !== 0) process.exit(result.status ?? 1)
}
