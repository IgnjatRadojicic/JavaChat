import { ApiError, getJson, postNoContent, deleteNoContent } from './client'

// What GET /api/me returns (UserResponse on the backend).
export interface User {
  email: string
  name: string | null
  pictureUrl: string | null
}

// Returns the signed in user, or null when nobody is signed in (401).
// Any other failure is thrown, so the caller can show an error instead of the landing page.
export async function fetchCurrentUser(): Promise<User | null> {
  try {
    return await getJson<User>('/api/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

// Login is a full page navigation, not a fetch: Google's login page cannot run inside ours.
export function login(): void {
  window.location.assign('/oauth2/authorization/google')
}

// Ends the session on the server. Spring answers 204.
export function logout(): Promise<void> {
  return postNoContent('/logout')
}

// Deletes the account and all its data. The server also ends the session.
export function deleteAccount(): Promise<void> {
  return deleteNoContent('/api/me')
}
