import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { DayOfWeek, IntervalUnit, Schedule, ScheduleRequest, ScheduleType, TaskDefinition, TaskType } from '../types'
import { formatEnum, localInputToUtc, scheduleTypes, utcToLocalInput } from '../utils/schedule'

interface Props {
  tasks: TaskDefinition[]
  schedule: Schedule | null
  onSubmit: (request: ScheduleRequest) => Promise<void>
  onCancel: () => void
}

interface FormState {
  name: string
  taskType: TaskType | ''
  scheduleType: ScheduleType
  executionTime: string
  intervalValue: string
  intervalUnit: IntervalUnit | ''
  daysOfWeek: DayOfWeek[]
  timeOfDay: string
  cronExpression: string
  parameters: Record<string, string>
  enabled: boolean
}

const weekdays: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

function initialState(schedule: Schedule | null): FormState {
  return {
    name: schedule?.name ?? '',
    taskType: schedule?.taskType ?? '',
    scheduleType: schedule?.scheduleType ?? 'ONE_TIME',
    executionTime: utcToLocalInput(schedule?.executionTime ?? null),
    intervalValue: schedule
      ? schedule.intervalValue == null ? '' : String(schedule.intervalValue)
      : '1',
    intervalUnit: schedule ? schedule.intervalUnit ?? '' : 'MINUTES',
    daysOfWeek: schedule?.daysOfWeek ?? [],
    timeOfDay: schedule?.timeOfDay?.slice(0, 5) ?? '',
    cronExpression: schedule?.cronExpression ?? '',
    parameters: schedule?.parameters ?? {},
    enabled: schedule?.enabled ?? true,
  }
}

function isValidTime(value: string): boolean {
  const match = /^(\d{2}):(\d{2})$/.exec(value)
  if (!match) return false
  const hours = Number(match[1])
  const minutes = Number(match[2])
  return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59
}

function validateForm(
  form: FormState,
  selectedTask: TaskDefinition | undefined,
  schedule: Schedule | null,
): Record<string, string> {
  const next: Record<string, string> = {}
  if (!form.name.trim()) next.name = 'Schedule name is required.'
  if (!selectedTask) next.taskType = 'Select a valid predefined task.'
  selectedTask?.requiredParameters.forEach((parameter) => {
    if (!form.parameters[parameter]?.trim()) next[`parameters.${parameter}`] = `${formatEnum(parameter)} is required.`
  })

  switch (form.scheduleType) {
    case 'ONE_TIME': {
      const timestamp = new Date(form.executionTime).getTime()
      const isUnchangedHistoricalExecution = Boolean(
        schedule?.scheduleType === 'ONE_TIME'
        && form.executionTime === utcToLocalInput(schedule.executionTime),
      )
      if (!form.executionTime || !Number.isFinite(timestamp)) {
        next.executionTime = 'A valid execution date and time are required.'
      } else if (timestamp <= Date.now() && form.enabled) {
        next.executionTime = 'A completed one-time schedule cannot be enabled. Choose a future execution date and time or disable the schedule.'
      } else if (timestamp <= Date.now() && !isUnchangedHistoricalExecution) {
        next.executionTime = 'Execution date and time must be in the future.'
      }
      break
    }
    case 'INTERVAL': {
      const interval = Number(form.intervalValue)
      if (!form.intervalValue.trim()) next.intervalValue = 'Interval is required.'
      else if (!Number.isInteger(interval) || interval <= 0) next.intervalValue = 'Interval must be a positive integer.'
      if (form.intervalUnit !== 'MINUTES' && form.intervalUnit !== 'HOURS') {
        next.intervalUnit = 'Select a valid interval unit.'
      }
      break
    }
    case 'WEEKLY':
      if (form.daysOfWeek.length === 0) next.daysOfWeek = 'Select at least one weekday.'
      if (!isValidTime(form.timeOfDay)) next.timeOfDay = 'A valid UTC execution time is required.'
      break
    case 'CRON':
      if (!form.cronExpression.trim()) next.cronExpression = 'Cron expression is required.'
      break
  }
  return next
}

export default function ScheduleForm({ tasks, schedule, onSubmit, onCancel }: Props) {
  const [form, setForm] = useState(() => initialState(schedule))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    setForm(initialState(schedule))
    setErrors({})
    setSubmitError('')
  }, [schedule])

  const selectedTask = useMemo(
    () => tasks.find((task) => task.type === form.taskType),
    [tasks, form.taskType],
  )
  const validationErrors = useMemo(
    () => validateForm(form, selectedTask, schedule),
    [form, selectedTask, schedule],
  )
  const formIsValid = Object.keys(validationErrors).length === 0

  const setValue = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors((current) => {
      const next = { ...current }
      delete next[key]
      return next
    })
    setSubmitError('')
  }

  const setParameter = (parameter: string, value: string) => {
    setForm((current) => ({ ...current, parameters: { ...current.parameters, [parameter]: value } }))
    setErrors((current) => {
      const next = { ...current }
      delete next[`parameters.${parameter}`]
      return next
    })
    setSubmitError('')
  }

  const changeMode = (scheduleType: ScheduleType) => {
    setForm((current) => ({ ...current, scheduleType }))
    setErrors({})
    setSubmitError('')
  }

  const makeRequest = (): ScheduleRequest => {
    const mode = form.scheduleType
    const parameters = Object.fromEntries(
      (selectedTask?.requiredParameters ?? []).map((key) => [key, form.parameters[key]?.trim() ?? '']),
    )
    return {
      name: form.name.trim(),
      taskType: form.taskType as TaskType,
      scheduleType: mode,
      executionTime: mode === 'ONE_TIME' ? localInputToUtc(form.executionTime) : null,
      intervalValue: mode === 'INTERVAL' ? Number(form.intervalValue) : null,
      intervalUnit: mode === 'INTERVAL' ? form.intervalUnit as IntervalUnit : null,
      daysOfWeek: mode === 'WEEKLY' ? form.daysOfWeek : null,
      timeOfDay: mode === 'WEEKLY' ? `${form.timeOfDay}:00` : null,
      cronExpression: mode === 'CRON' ? form.cronExpression.trim() : null,
      parameters,
      enabled: form.enabled,
    }
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const currentErrors = validateForm(form, selectedTask, schedule)
    if (Object.keys(currentErrors).length) {
      setErrors(currentErrors)
      setSubmitError('Please correct the highlighted fields.')
      return
    }
    setSubmitting(true)
    setSubmitError('')
    try {
      await onSubmit(makeRequest())
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        setSubmitError(error.message)
      } else {
        setSubmitError('Unable to save the schedule. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const parameterField = (parameter: string) => {
    const id = `parameter-${parameter}`
    const common = {
      id,
      value: form.parameters[parameter] ?? '',
      required: true,
      onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
        setParameter(parameter, event.target.value),
      'aria-invalid': Boolean(errors[`parameters.${parameter}`]),
    }
    return (
      <div className={`field ${parameter === 'body' ? 'field-full' : ''}`} key={parameter}>
        <label htmlFor={id} className="required-label">{formatEnum(parameter)}</label>
        {parameter === 'body' ? <textarea {...common} rows={4} /> : <input {...common} type={parameter === 'recipient' ? 'email' : 'text'} />}
        {errors[`parameters.${parameter}`] && <p className="field-error">{errors[`parameters.${parameter}`]}</p>}
      </div>
    )
  }

  return (
    <section className="form-card" aria-labelledby="form-title">
      <div className="form-heading">
        <div><p className="eyebrow">{schedule ? 'Edit schedule' : 'New schedule'}</p><h2 id="form-title">{schedule ? schedule.name : 'Create a schedule'}</h2></div>
        <button type="button" className="icon-button" onClick={onCancel} aria-label="Close form">×</button>
      </div>
      <form onSubmit={submit} noValidate>
        {submitError && <div className="alert alert-error" role="alert">{submitError}</div>}
        <p className="required-note"><span aria-hidden="true">*</span> Required fields</p>
        <div className="form-grid">
          <div className="field field-full">
            <label htmlFor="schedule-name" className="required-label">Schedule name</label>
            <input id="schedule-name" value={form.name} onChange={(event) => setValue('name', event.target.value)} aria-invalid={Boolean(errors.name)} required autoFocus />
            {errors.name && <p className="field-error">{errors.name}</p>}
          </div>
          <div className="field">
            <label htmlFor="task-type" className="required-label">Predefined task</label>
            <select id="task-type" value={form.taskType} onChange={(event) => {
              setValue('taskType', event.target.value as TaskType)
              setForm((current) => ({ ...current, parameters: {} }))
            }} aria-invalid={Boolean(errors.taskType)} required>
              <option value="">Select a task</option>
              {tasks.map((task) => <option key={task.type} value={task.type}>{task.name}</option>)}
            </select>
            {selectedTask && <p className="field-hint">{selectedTask.description}</p>}
            {errors.taskType && <p className="field-error">{errors.taskType}</p>}
          </div>
          <div className="field">
            <label htmlFor="schedule-type" className="required-label">Scheduling mode</label>
            <select id="schedule-type" value={form.scheduleType} onChange={(event) => changeMode(event.target.value as ScheduleType)} required>
              {scheduleTypes.map((type) => <option key={type} value={type}>{formatEnum(type)}</option>)}
            </select>
          </div>

          {form.scheduleType === 'ONE_TIME' && <div className="field field-full">
            <label htmlFor="execution-time" className="required-label">Execution date and time</label>
            <input id="execution-time" type="datetime-local" value={form.executionTime} min={utcToLocalInput(new Date().toISOString())} onChange={(event) => setValue('executionTime', event.target.value)} aria-invalid={Boolean(errors.executionTime)} required />
            <p className="field-hint">Entered in your local timezone and sent to the server as UTC.</p>
            {(errors.executionTime || validationErrors.executionTime) && <p className="field-error">{errors.executionTime || validationErrors.executionTime}</p>}
          </div>}

          {form.scheduleType === 'INTERVAL' && <>
            <div className="field"><label htmlFor="interval-value" className="required-label">Repeat every</label><input id="interval-value" type="number" min="1" step="1" value={form.intervalValue} onChange={(event) => setValue('intervalValue', event.target.value)} aria-invalid={Boolean(errors.intervalValue)} required />{errors.intervalValue && <p className="field-error">{errors.intervalValue}</p>}</div>
            <div className="field"><label htmlFor="interval-unit" className="required-label">Interval unit</label><select id="interval-unit" value={form.intervalUnit} onChange={(event) => setValue('intervalUnit', event.target.value as IntervalUnit | '')} aria-invalid={Boolean(errors.intervalUnit)} required><option value="">Select a unit</option><option value="MINUTES">Minutes</option><option value="HOURS">Hours</option></select>{errors.intervalUnit && <p className="field-error">{errors.intervalUnit}</p>}</div>
          </>}

          {form.scheduleType === 'WEEKLY' && <>
            <fieldset className="field field-full weekday-fieldset" aria-required="true"><legend className="required-label">Weekdays</legend><div className="weekday-grid">{weekdays.map((day) => <label key={day} className="check-option"><input type="checkbox" checked={form.daysOfWeek.includes(day)} onChange={(event) => setValue('daysOfWeek', event.target.checked ? [...form.daysOfWeek, day] : form.daysOfWeek.filter((value) => value !== day))} />{formatEnum(day).slice(0, 3)}</label>)}</div>{errors.daysOfWeek && <p className="field-error">{errors.daysOfWeek}</p>}</fieldset>
            <div className="field field-full"><label htmlFor="time-of-day" className="required-label">Execution time (UTC)</label><input id="time-of-day" type="time" value={form.timeOfDay} onChange={(event) => setValue('timeOfDay', event.target.value)} aria-invalid={Boolean(errors.timeOfDay)} required /><p className="field-hint">This value is explicitly UTC, not your browser’s local timezone.</p>{errors.timeOfDay && <p className="field-error">{errors.timeOfDay}</p>}</div>
          </>}

          {form.scheduleType === 'CRON' && <div className="field field-full"><label htmlFor="cron-expression" className="required-label">Quartz cron expression</label><input id="cron-expression" value={form.cronExpression} onChange={(event) => setValue('cronExpression', event.target.value)} placeholder="0 0/15 * ? * *" aria-invalid={Boolean(errors.cronExpression)} required /><p className="field-hint">Quartz syntax (not Unix cron). Example: <code>0 0/15 * ? * *</code> runs every 15 minutes in UTC.</p>{errors.cronExpression && <p className="field-error">{errors.cronExpression}</p>}</div>}

          {selectedTask && <div className="section-divider field-full"><h3>Task parameters</h3></div>}
          {selectedTask?.requiredParameters.map(parameterField)}
          <label className="enabled-control field-full"><input type="checkbox" checked={form.enabled} onChange={(event) => setValue('enabled', event.target.checked)} /><span><strong>Enabled</strong><small>Quartz will register and execute this schedule.</small></span></label>
        </div>
        <div className="form-actions"><button type="button" className="button button-secondary" onClick={onCancel}>Cancel</button><button type="submit" className="button button-primary" disabled={submitting || !formIsValid}>{submitting ? 'Saving…' : schedule ? 'Save changes' : 'Create schedule'}</button></div>
      </form>
    </section>
  )
}
