import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(fileURLToPath(new URL('.', import.meta.url)), '..')
const files = execFileSync('git', ['ls-files', '-co', '--exclude-standard', '-z'], {
  cwd: root,
  encoding: 'utf8'
}).split('\0').filter(Boolean)

const filenamePattern = /(?:api[_-]?key|apikey|private[_-]?key|access[_-]?token|secret|password|credentials).*\.(?:txt|json|ya?ml|env|properties)$/i
const contentPatterns = [
  /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
  /(?:api[_-]?key|secret|password|access[_-]?token)\s*[:=]\s*["']?[A-Za-z0-9+/=_-]{20,}/i,
  /AKIA[0-9A-Z]{16}/
]
const findings = []

for (const relative of files) {
  const normalized = relative.replaceAll('\\', '/')
  if (filenamePattern.test(normalized) || /\.(?:key|pem|p12|pfx)$/i.test(normalized)) {
    findings.push(`${relative}: secret-like filename`)
    continue
  }
  const absolute = join(root, relative)
  if (!existsSync(absolute)) continue
  let content
  try {
    content = readFileSync(absolute, 'utf8')
  } catch {
    continue
  }
  if (content.includes('\0')) continue
  const lines = content.split(/\r?\n/)
  lines.forEach((line, index) => {
    if (contentPatterns.some(pattern => pattern.test(line))) {
      findings.push(`${relative}:${index + 1}: secret-like value`)
    }
  })
}

if (findings.length > 0) {
  console.error('Secret scan failed. Remove secret material before committing:')
  for (const finding of findings) console.error(`- ${finding}`)
  process.exit(1)
}
console.log(`Secret scan passed (${files.length} Git-visible files checked).`)
