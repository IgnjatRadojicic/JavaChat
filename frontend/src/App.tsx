import { useCallback, useState } from 'react'
import { useAuth } from './auth/useAuth'
import { AccountBar } from './components/AccountBar'
import { Chat } from './components/Chat'
import { Landing } from './components/Landing'
import { MemoryPanel } from './components/MemoryPanel'

function App() {
  const { state, retry, signOut, markSignedOut } = useAuth()
  // Bumped after every reply so the memory panel reloads.
  const [memoryVersion, setMemoryVersion] = useState(0)
  const refreshMemories = useCallback(() => setMemoryVersion(previous => previous + 1), [])

  switch (state.status) {
    case 'loading':
      return <p>Loading…</p>

    case 'error':
      return (
        <main>
          <p>Could not reach the server: {state.message}</p>
          <button type="button" onClick={retry}>Try again</button>
        </main>
      )

    case 'signedOut':
      return <Landing />

    case 'signedIn':
      return (
        <main>
          <AccountBar user={state.user} onSignOut={signOut} onAccountDeleted={markSignedOut} />
          <Chat onUnauthorized={markSignedOut} onReplied={refreshMemories} />
          <MemoryPanel version={memoryVersion} onUnauthorized={markSignedOut} />
        </main>
      )
  }
}

export default App
