import settingsIcon from '../assets/icons/settings-outline.svg'

export default function AppHeader() {
  return (
    <header className="app-header">
      <span className="app-header-logo">DODU</span>
      <img
        src={settingsIcon}
        alt="설정 아이콘"
        width={24}
        height={24}
      />
    </header>
  )
}