import type { Schedule, ScheduleType } from '../types'

const pad = (value: number) => String(value).padStart(2, '0')

export function utcToLocalInput(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function localInputToUtc(value: string): string {
  return new Date(value).toISOString()
}

export function describeTiming(schedule: Schedule): string {
  switch (schedule.scheduleType) {
    case 'ONE_TIME':
      return schedule.executionTime
        ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(schedule.executionTime))
        : 'Not configured'
    case 'INTERVAL': {
      const unit = schedule.intervalUnit?.toLowerCase().replace(/s$/, '') ?? 'unit'
      return `Every ${schedule.intervalValue} ${unit}${schedule.intervalValue === 1 ? '' : 's'}`
    }
    case 'WEEKLY':
      return `${(schedule.daysOfWeek ?? []).map(formatEnum).join(', ')} at ${schedule.timeOfDay?.slice(0, 5)} UTC`
    case 'CRON':
      return `Quartz: ${schedule.cronExpression}`
  }
}

export function formatEnum(value: string): string {
  return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export const scheduleTypes: ScheduleType[] = ['ONE_TIME', 'INTERVAL', 'WEEKLY', 'CRON']
