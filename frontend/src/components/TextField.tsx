type TextFieldProps = {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  hint?: string
  error?: string
}

export default function TextField({
  id,
  label,
  value,
  onChange,
  hint,
  error,
}: TextFieldProps) {
  const describedBy = [
    hint ? `${id}-hint` : '',
    error ? `${id}-error` : '',
  ].filter(Boolean).join(' ') || undefined

  return (
    <div className="text-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="text"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />

      {hint && (
        <p id={`${id}-hint`} className="text-field-hint">
          {hint}
        </p>
      )}

      {error && (
        <p
          id={`${id}-error`}
          className="text-field-error"
          role="alert"
        >
          {error}
        </p>
      )}
    </div>
  )
}
