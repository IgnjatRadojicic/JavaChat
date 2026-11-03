import { useState } from 'react'
import { deleteAccount, type User } from '../api/auth'
import { describeError } from '../api/errors'

interface AccountBarProps {
  user: User
  onSignOut: () => Promise<void>
  onAccountDeleted: () => void
}

export function AccountBar({ user, onSignOut, onAccountDeleted }: AccountBarProps) {
  const [error, setError] = useState<string | null>(null)

  async function signOut() {
    try {
      await onSignOut()
    } catch (failure) {
      setError(describeError(failure))
    }
  }

  async function removeAccount() {
    const confirmed = window.confirm(
      'Delete your account, your conversation and everything JavaChat remembers about you? This cannot be undone.')
    if (!confirmed) return
    try {
      await deleteAccount()
      onAccountDeleted()
    } catch (failure) {
      setError(describeError(failure))
    }
  }

  return (
    <header>
      <span>Signed in as {user.name ?? user.email}</span>{' '}
      <button type="button" onClick={() => void signOut()}>Log out</button>{' '}
      <button type="button" onClick={() => void removeAccount()}>Delete account</button>
      {error && <p role="alert">{error}</p>}
    </header>
  )
}
