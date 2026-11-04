// 이전 공통 UI import 보존: import settingsIcon from '../assets/icons/settings-outline.svg'
import settingsIcon from '../assets/final-icons/settings.svg'

export default function AppHeader({ onSettings }: { onSettings?: () => void }) {
  return (
    <header className="app-header">
      <button type="button" className="app-header-logo" aria-label="DODU 새로고침" onClick={() => window.location.reload()}>DODU</button>
      {onSettings ? <button className="screen-icon-button app-header-settings" type="button" aria-label="설정" onClick={onSettings}>
        <img src={settingsIcon} alt="" />
      </button> : <img src={settingsIcon} alt="설정 아이콘" />}
    </header>
  )
}
