import { ApiError } from './client'

export const isUnauthorized = (error: unknown) => error instanceof ApiError && error.status === 401

export const isNotFound = (error: unknown) => error instanceof ApiError && error.status === 404

// A message a person can read, for any failure.
export function describeError(error: unknown): string {
  if (error instanceof ApiError && error.status === 429) {
    const wait = error.problem.retryAfterSeconds
    return wait ? `You are sending messages too quickly. Try again in ${wait} s.` : error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong.'
}
