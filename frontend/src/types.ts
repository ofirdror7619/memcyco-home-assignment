export type TaskType = 'LOG' | 'DUMMY_EMAIL'
export type ScheduleType = 'ONE_TIME' | 'INTERVAL' | 'WEEKLY' | 'CRON'
export type IntervalUnit = 'MINUTES' | 'HOURS'
export type DayOfWeek =
  | 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY'
  | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface TaskDefinition {
  type: TaskType
  name: string
  description: string
  requiredParameters: string[]
}

export interface ScheduleRequest {
  name: string
  taskType: TaskType
  scheduleType: ScheduleType
  executionTime: string | null
  intervalValue: number | null
  intervalUnit: IntervalUnit | null
  daysOfWeek: DayOfWeek[] | null
  timeOfDay: string | null
  cronExpression: string | null
  parameters: Record<string, string>
  enabled: boolean
}

export interface Schedule extends ScheduleRequest {
  id: number
  createdAt: string
  updatedAt: string
}

export interface ApiErrorBody {
  message?: string
  fieldErrors?: Record<string, string>
}
