import { ApiError, deleteNoContent, getJson, request, type ProblemDetail } from './client'

export type Role = 'USER' | 'ASSISTANT'

// Same shape as ChatMessageResponse on the backend.
export interface ChatMessage {
  id: number
  role: Role
  content: string
  createdAt: string
}

export function fetchHistory(before?: number): Promise<ChatMessage[]> {
  const query = before === undefined ? '' : `?before=${before}`
  return getJson<ChatMessage[]>(`/api/chat/history${query}`)
}

export function clearHistory(): Promise<void> {
  return deleteNoContent('/api/chat/history')
}

// Sends a message and reads the reply as Server-Sent Events.
// Calls onToken for every piece, and resolves with the saved message from the done event.
export async function streamMessage(
  message: string,
  onToken: (text: string) => void,
  signal?: AbortSignal,
): Promise<ChatMessage> {
  // request() throws an ApiError for 400 / 401 / 429 before any streaming starts
  const response = await request('/api/chat/stream', {
    method: 'POST',
    body: JSON.stringify({ message }),
    signal,
  })
  if (!response.body) {
    throw new Error('The server sent no stream.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

    // Events end with a blank line. The last piece may be incomplete, so keep it for the next round.
    const events = buffer.split('\n\n')
    buffer = events.pop() ?? ''

    for (const raw of events) {
      const event = parseEvent(raw)
      if (!event) continue
      if (event.name === 'token') onToken((event.data as { text: string }).text)
      if (event.name === 'done') return event.data as ChatMessage
      if (event.name === 'error') throw new ApiError(event.data as ProblemDetail)
    }
  }

  throw new Error('The stream ended before the reply was finished.')
}

function parseEvent(raw: string): { name: string; data: unknown } | null {
  let name = 'message'
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) name = line.slice('event:'.length).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice('data:'.length))
  }
  return dataLines.length === 0 ? null : { name, data: JSON.parse(dataLines.join('\n')) }
}
