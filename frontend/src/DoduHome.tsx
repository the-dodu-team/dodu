import { useState } from 'react'

export default function DoduHome() {
  const [connection, setConnection] = useState('확인 전')
  const [checking, setChecking] = useState(false)

  async function checkConnection() {
    setChecking(true)
    try {
      const response = await fetch('/api/health', { signal: AbortSignal.timeout(5000) })
      if (!response.ok) throw new Error('HTTP 오류')
      const result: unknown = await response.json()
      if (!result || typeof result !== 'object' || !('status' in result) || result.status !== 'ok') {
        throw new Error('응답 형식 오류')
      }
      setConnection('연결됨')
    } catch {
      setConnection('연결할 수 없습니다. 백엔드 실행을 확인해주세요.')
    } finally {
      setChecking(false)
    }
  }

  return (
    <main>
      <p className="eyebrow">DODU · v2.5</p>
      <h1>계획한 시작을<br />첫 행동으로.</h1>
      <p>스스로 정한 개인작업의 시작을 돕는 서비스입니다.</p>
      <p>사진 인증은 작업환경 준비에 대한 응답이며 실제 작업 시작의 증거가 아닙니다.</p>
      <section aria-labelledby="environment-title">
        <h2 id="environment-title">개발환경 확인</h2>
        <p>현재는 앱 실행과 API 연결을 확인하는 준비 화면입니다.</p>
        <button disabled={checking} onClick={checkConnection}>
          {checking ? '확인 중…' : '백엔드 연결 확인'}
        </button>
        <p role="status">{connection}</p>
      </section>
    </main>
  )
}
