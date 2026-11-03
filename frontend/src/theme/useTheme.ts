import { useCallback, useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

// index.html already applied the saved (or system) theme before React started; read it back.
function initialTheme(): Theme {
  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
}

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(initialTheme)

  // Keep the page and the saved choice in sync with React state.
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    try {
      localStorage.setItem('theme', theme)
    } catch {
      // Private windows can refuse storage; the theme still works for this visit.
    }
  }, [theme])

  const toggle = useCallback(() => setTheme(previous => (previous === 'dark' ? 'light' : 'dark')), [])

  return { theme, toggle }
}
