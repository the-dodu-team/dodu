import type { ReactNode } from 'react'

type ButtonProps = {
  children: ReactNode
  onClick?: () => void
  disabled?: boolean
}

export default function Button({
  children,
  onClick,
  disabled = false,
}: ButtonProps) {
  return (
    <button
      type="button"
      className="button"
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  )
}