import { useCallback, useState } from 'react'

export type Theme = 'light' | 'dark'
const KEY = 'staffsuite.theme'

function current(): Theme {
  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
}

/** Light/dark colour theme: the choice is kept in this browser and applied to <html data-theme>. */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(current)

  const toggle = useCallback(() => {
    const next: Theme = current() === 'dark' ? 'light' : 'dark'
    document.documentElement.setAttribute('data-theme', next)
    try {
      localStorage.setItem(KEY, next)
    } catch {
      /* the choice just won't be remembered */
    }
    setTheme(next)
  }, [])

  return { theme, toggle }
}
