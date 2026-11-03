import { login } from '../api/auth'

export function Landing() {
  return (
    <main>
      <h1>JavaChat</h1>
      <p>A chat assistant that remembers you. Sign in, talk to it, and it keeps what matters about you for next time.</p>
      <button type="button" onClick={login}>
        Login with Google
      </button>
    </main>
  )
}
