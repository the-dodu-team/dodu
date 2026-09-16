import { spawn } from 'node:child_process'
import { existsSync, readdirSync } from 'node:fs'
import { createServer } from 'node:net'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const windows = process.platform === 'win32'
const javaName = windows ? 'java.exe' : 'java'

function javaEnvironment() {
  const jdkRoot = join(root, '.tools', 'jdk')
  const local = existsSync(jdkRoot)
    ? readdirSync(jdkRoot).map(name => join(jdkRoot, name)).find(path => existsSync(join(path, 'bin', javaName)))
    : undefined
  const javaHome = process.env.JAVA_HOME || local
  if (!javaHome) throw new Error('JDK 21 is required. Set JAVA_HOME or prepare .tools/jdk (see README.md).')
  if (!existsSync(join(javaHome, 'bin', javaName))) throw new Error('JAVA_HOME does not contain bin/java.')
  // Windows environment keys are case insensitive; avoid duplicate Path/PATH keys.
  const env = { ...process.env }
  const pathKey = Object.keys(env).find(key => key.toLowerCase() === 'path') || 'PATH'
  env[pathKey] = `${join(javaHome, 'bin')}${windows ? ';' : ':'}${env[pathKey] || ''}`
  env.JAVA_HOME = javaHome
  return env
}

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd: root, stdio: 'inherit', ...options })
    child.on('error', reject)
    child.on('exit', (code, signal) => {
      if (code === 0) resolve()
      else reject(new Error(`${command} exited (${signal || code}).`))
    })
  })
}

function npm(args) {
  const cli = process.env.npm_execpath
  if (!cli) throw new Error('Run this workflow through npm run.')
  return run(process.execPath, [cli, ...args])
}

function maven(task) {
  const env = javaEnvironment()
  env.DODU_MAVEN_REPO = join(root, '.tools', 'm2')
  const args = task === 'backend'
    ? ['spring-boot:run', '-Dspring-boot.run.arguments=--server.address=127.0.0.1']
    : ['clean', task === 'verify' ? 'verify' : 'package', '-Pbundle-frontend']
  if (windows) {
    // Paths are passed as environment data, never interpolated into shell code.
    const literalArgs = args.map(arg => `'${arg}'`).join(', ')
    return run('powershell.exe', ['-NoProfile', '-Command',
      `& .\\mvnw.cmd '-B' '-ntp' "-Dmaven.repo.local=$env:DODU_MAVEN_REPO" @(${literalArgs}); exit $LASTEXITCODE`],
      { cwd: join(root, 'backend'), env })
  }
  return run('sh', ['./mvnw', '-B', '-ntp', `-Dmaven.repo.local=${env.DODU_MAVEN_REPO}`, ...args],
    { cwd: join(root, 'backend'), env })
}

async function checkPorts(ports) {
  for (const port of ports) {
    await new Promise((resolve, reject) => {
      const server = createServer()
      server.once('error', () => reject(new Error(`Port ${port} is in use. Stop the existing server before starting Dodu.`)))
      server.listen(port, '127.0.0.1', () => server.close(resolve))
    })
  }
}

try {
  const task = process.argv[2]
  if (task === 'check-ports') {
    javaEnvironment()
    await checkPorts([8080, 5173])
  } else if (task === 'backend') {
    await maven(task)
  } else if (task === 'build' || task === 'verify') {
    javaEnvironment()
    if (task === 'verify') await npm(['run', 'lint', '--workspace', 'frontend'])
    await npm(['run', 'build', '--workspace', 'frontend'])
    await maven(task)
    console.log('Bundled application: backend/target/dodu-backend-0.0.1-SNAPSHOT.jar')
  } else if (task === 'start') {
    const env = javaEnvironment()
    const jar = join(root, 'backend', 'target', 'dodu-backend-0.0.1-SNAPSHOT.jar')
    if (!existsSync(jar)) throw new Error('Run npm run build first.')
    await checkPorts([8080])
    await run(join(env.JAVA_HOME, 'bin', javaName), ['-jar', jar, '--server.address=127.0.0.1'], { env })
  } else {
    throw new Error('Unknown workflow task.')
  }
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
