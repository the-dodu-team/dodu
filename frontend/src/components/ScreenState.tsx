import ScreenHeader from './ScreenHeader'

export default function ScreenState({ title, description, loading = false, onBack, onRetry }: {
  title: string; description: string; loading?: boolean; onBack?: () => void; onRetry?: () => void
}) {
  return <main className="screen-page screen-state-page" aria-busy={loading}>
    <ScreenHeader title={title} onBack={onBack} />
    <div className="screen-state-content" role={loading ? 'status' : 'alert'}>
      {/* 이전: loading && <span className="screen-spinner" aria-hidden="true" /> */}
      {loading && <div className="loading-indicator" aria-hidden="true">
        <span className="screen-spinner" />
        <span className="loading-label">Loading<span className="loading-dots" /></span>
      </div>}
      <p>{description}</p>
      {onRetry && <button className="report-primary" type="button" onClick={onRetry}>다시 확인하기</button>}
    </div>
  </main>
}
