import { useCallback, useEffect, useState } from 'react'
import { fetchCurrentUser, logout, type User } from '../api/auth'

export type AuthState =
  | { status: 'loading' }
  | { status: 'signedOut' }
  | { status: 'signedIn'; user: User }
  | { status: 'error'; message: string }

export function useAuth() {
  const [state, setState] = useState<AuthState>({ status: 'loading' })
  // Bumped by retry(); a new value makes the effect below ask the backend again.
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    // If this effect is cleaned up (StrictMode's double run, or a newer retry),
    // its late answer must not overwrite the newer one.
    let ignore = false

    fetchCurrentUser()
      .then(user => {
        if (!ignore) setState(user ? { status: 'signedIn', user } : { status: 'signedOut' })
      })
      .catch((error: unknown) => {
        if (!ignore) setState({ status: 'error', message: error instanceof Error ? error.message : 'Something went wrong.' })
      })

    return () => {
      ignore = true
    }
  }, [attempt])

  // The error screen's "Try again": show loading again, then ask once more.
  const retry = useCallback(() => {
    setState({ status: 'loading' })
    setAttempt(previous => previous + 1)
  }, [])

  const signOut = useCallback(async () => {
    await logout()
    setState({ status: 'signedOut' })
  }, [])

  // For when any API call gets a 401 later (session expired): switch to the landing page.
  const markSignedOut = useCallback(() => setState({ status: 'signedOut' }), [])

  return { state, retry, signOut, markSignedOut }
}
