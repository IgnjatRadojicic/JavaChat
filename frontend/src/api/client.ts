// Shape of every error the backend returns (RFC 9457 ProblemDetail).
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  // Extra fields our backend adds on some errors
  errors?: Record<string, string>   // 400 validation: field -> message
  retryAfterSeconds?: number        // 429 rate limit
}

// Thrown for any non-2xx response, so callers can use try/catch.
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`)
    this.status = problem.status
    this.problem = problem
  }
}

// Spring puts the CSRF token in a readable cookie (csrf.spa()).
// We copy it into a header on every state-changing request.
function readCsrfToken(): string | undefined {
  const cookie = document.cookie
    .split('; ')
    .find(part => part.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.split('=')[1]) : undefined
}

const UNSAFE_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE']

// Low-level request: adds headers, returns the raw Response if it is OK.
// Streaming uses this directly, because it reads the body as a stream.
export async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (UNSAFE_METHODS.includes(method)) {
    const token = readCsrfToken()
    if (token) headers.set('X-XSRF-TOKEN', token)
  }

  const response = await fetch(path, { ...init, method, headers })

  if (!response.ok) {
    throw new ApiError(await readProblem(response))
  }
  return response
}

// Turns an error response into a ProblemDetail, even when the body is empty
// (our 401 has no body) or not JSON.
async function readProblem(response: Response): Promise<ProblemDetail> {
  const text = await response.text()
  if (text) {
    try {
      return { status: response.status, ...JSON.parse(text) }
    } catch {
      // not JSON, fall through
    }
  }
  return { status: response.status, title: response.statusText }
}

// Convenience helpers for normal JSON endpoints.
export async function getJson<T>(path: string): Promise<T> {
  const response = await request(path)
  return response.json() as Promise<T>
}

export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await request(path, { method: 'POST', body: JSON.stringify(body) })
  return response.json() as Promise<T>
}

export async function postNoContent(path: string): Promise<void> {
  await request(path, { method: 'POST' })
}

export async function deleteNoContent(path: string): Promise<void> {
  await request(path, { method: 'DELETE' })
}
