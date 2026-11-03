import { useAuth } from './auth/useAuth'
import { Landing } from './components/Landing'

function App() {
  const { state, retry, signOut } = useAuth()

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
          {/* 6.5: the chat view goes here */}
        </main>
      )
  }
}

export default App
