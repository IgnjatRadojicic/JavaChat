import { deleteNoContent, getJson } from './client'

// Same shape as MemoryResponse on the backend.
export interface Memory {
  id: string
  content: string
}

export function fetchMemories(): Promise<Memory[]> {
  return getJson<Memory[]>('/api/memories')
}

export function deleteMemory(id: string): Promise<void> {
  return deleteNoContent(`/api/memories/${encodeURIComponent(id)}`)
}
