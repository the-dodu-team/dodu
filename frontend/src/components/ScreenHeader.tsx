import backIcon from '../assets/final-icons/back.svg'
import closeIcon from '../assets/final-icons/close.svg'
import { useEffect, useRef } from 'react'

export default function ScreenHeader({ title, onBack, close = false }: {
  title: string; onBack?: () => void; close?: boolean
}) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [title])
  return <header className="screen-header">
    {onBack && <button className="screen-icon-button" type="button" aria-label={close ? '닫기' : '뒤로'} onClick={onBack}>
      <span className="screen-icon-slot"><img src={close ? closeIcon : backIcon} alt="" /></span>
    </button>}
    {title && <h1 ref={heading} tabIndex={-1}>{title}</h1>}
  </header>
}
