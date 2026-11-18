import type { ApiErrorBody, Schedule, ScheduleRequest, TaskDefinition } from '../types'

export class ApiError extends Error {
  readonly fieldErrors: Record<string, string>

  constructor(message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    let body: ApiErrorBody = {}
    try {
      body = await response.json() as ApiErrorBody
    } catch {
      // The status fallback below remains useful for non-JSON proxy failures.
    }
    throw new ApiError(body.message ?? `Request failed with status ${response.status}`, body.fieldErrors)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

const jsonRequest = (method: 'POST' | 'PUT', body: ScheduleRequest): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  getTasks: () => request<TaskDefinition[]>('/api/tasks'),
  getSchedules: () => request<Schedule[]>('/api/schedules'),
  getSchedule: (id: number) => request<Schedule>(`/api/schedules/${id}`),
  createSchedule: (body: ScheduleRequest) => request<Schedule>('/api/schedules', jsonRequest('POST', body)),
  updateSchedule: (id: number, body: ScheduleRequest) =>
    request<Schedule>(`/api/schedules/${id}`, jsonRequest('PUT', body)),
  deleteSchedule: (id: number) => request<void>(`/api/schedules/${id}`, { method: 'DELETE' }),
}
