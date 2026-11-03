import { useAuth } from './auth/useAuth'
import { Chat } from './components/Chat'
import { Landing } from './components/Landing'

function App() {
  const { state, retry, signOut, markSignedOut } = useAuth()

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
          <p>Signed in as {state.user.name ?? state.user.email}</p>
          <button type="button" onClick={signOut}>Log out</button>
          <Chat onUnauthorized={markSignedOut} />
        </main>
      )
  }
}

export default App
