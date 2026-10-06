// 제목을 Tab 순서에 추가하지 않고 화면 전환을 안내한다.
export function focusScreenHeading(content: HTMLElement | null) {
  const heading = content?.querySelector<HTMLElement>('h1')
  if (!heading) return
  heading.tabIndex = -1
  heading.focus()
}
