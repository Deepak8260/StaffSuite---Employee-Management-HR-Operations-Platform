import { useCallback, useSyncExternalStore } from 'react'

export type Theme = 'light' | 'dark'
const KEY = 'staffsuite.theme'

const listeners = new Set<() => void>()

function current(): Theme {
  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function apply(next: Theme) {
  document.documentElement.setAttribute('data-theme', next)
  try {
    localStorage.setItem(KEY, next)
  } catch {
    /* the choice just won't be remembered */
  }
  listeners.forEach((l) => l())
}

/** Light/dark colour theme, shared by every component that uses it (the top-bar toggle and the Settings page stay in step). */
export function useTheme() {
  const theme = useSyncExternalStore(subscribe, current)
  const toggle = useCallback(() => apply(current() === 'dark' ? 'light' : 'dark'), [])
  const setTheme = useCallback((next: Theme) => apply(next), [])
  return { theme, toggle, setTheme }
}
