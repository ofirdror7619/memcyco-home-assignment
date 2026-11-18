import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Schedule, ScheduleRequest, TaskDefinition } from './types'

const tasks: TaskDefinition[] = [
  { type: 'LOG', name: 'Log message', description: 'Writes a message to the application log', requiredParameters: ['message'] },
  { type: 'DUMMY_EMAIL', name: 'Dummy email', description: 'Logs a simulated outgoing email', requiredParameters: ['recipient', 'subject', 'body'] },
]
const existing: Schedule = {
  id: 1, name: 'Heartbeat log', taskType: 'LOG', scheduleType: 'INTERVAL', executionTime: null,
  intervalValue: 15, intervalUnit: 'MINUTES', daysOfWeek: [], timeOfDay: null, cronExpression: null,
  parameters: { message: 'alive' }, enabled: true, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
}

const completedOneTime: Schedule = {
  ...existing,
  id: 1,
  name: 'Completed reminder',
  scheduleType: 'ONE_TIME',
  executionTime: '2020-01-01T10:00:00Z',
  intervalValue: null,
  intervalUnit: null,
  enabled: false,
}

const json = (data: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } }))

function installServer(initial: Schedule[] = [existing]) {
  let schedules = [...initial]
  const requests: ScheduleRequest[] = []
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    if (url === '/api/tasks') return json(tasks)
    if (url === '/api/schedules' && method === 'GET') return json(schedules)
    if (url === '/api/schedules' && method === 'POST') {
      const request = JSON.parse(String(init?.body)) as ScheduleRequest
      requests.push(request)
      const created = { ...request, id: 2, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() } as Schedule
      schedules = [...schedules, created]
      return json(created, 201)
    }
    if (url === '/api/schedules/1' && method === 'PUT') {
      const request = JSON.parse(String(init?.body)) as ScheduleRequest
      requests.push(request)
      schedules = schedules.map((item) => item.id === 1 ? { ...item, ...request } : item)
      return json(schedules[0])
    }
    if (url === '/api/schedules/1' && method === 'DELETE') {
      schedules = schedules.filter((item) => item.id !== 1)
      return Promise.resolve(new Response(null, { status: 204 }))
    }
    throw new Error(`Unhandled request: ${method} ${url}`)
  })
  return { fetchMock, requests }
}

afterEach(() => vi.restoreAllMocks())

describe('scheduling dashboard', () => {
  it('renders the schedule list with timing and status', async () => {
    installServer([existing, { ...existing, id: 2, name: 'Minute pulse', intervalValue: 1 }])
    render(<App />)
    expect(screen.getByRole('img', { name: 'Memcyco' })).toBeInTheDocument()
    expect(screen.getByText('Loading schedules…')).toBeInTheDocument()
    expect(await screen.findByText('Heartbeat log')).toBeInTheDocument()
    expect(screen.getByText('Every 15 minutes')).toBeInTheDocument()
    expect(screen.getByText('Every 1 minute')).toBeInTheDocument()
    expect(screen.getAllByText('Enabled')).toHaveLength(2)
  })

  it('selects tasks dynamically and shows every scheduling mode', async () => {
    installServer([])
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: /new schedule/i }))
    expect(screen.getByText('Required fields')).toBeInTheDocument()
    expect(screen.getByLabelText('Schedule name')).toBeRequired()
    expect(screen.getByLabelText('Predefined task')).toBeRequired()
    expect(screen.getByLabelText('Scheduling mode')).toBeRequired()
    expect(screen.getByRole('checkbox', { name: /Enabled/ })).not.toBeRequired()
    expect(screen.getByLabelText('Execution date and time')).toBeRequired()
    await user.selectOptions(screen.getByLabelText('Predefined task'), 'DUMMY_EMAIL')
    expect(screen.getByLabelText('Recipient')).toBeRequired()
    expect(screen.getByLabelText('Subject')).toBeRequired()
    expect(screen.getByLabelText('Body')).toBeRequired()
    const mode = screen.getByLabelText('Scheduling mode')
    await user.selectOptions(mode, 'INTERVAL')
    expect(screen.queryByLabelText('Execution date and time')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Repeat every')).toBeRequired()
    expect(screen.getByLabelText('Interval unit')).toBeRequired()
    await user.selectOptions(mode, 'WEEKLY')
    expect(screen.queryByLabelText('Repeat every')).not.toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Weekdays' })).toHaveAttribute('aria-required', 'true')
    expect(screen.getByLabelText('Execution time (UTC)')).toBeRequired()
    expect(screen.getByText(/explicitly UTC/i)).toBeInTheDocument()
    await user.selectOptions(mode, 'CRON')
    expect(screen.queryByRole('group', { name: 'Weekdays' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Quartz cron expression')).toBeRequired()
    expect(screen.getByText(/not Unix cron/i)).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Predefined task'), 'LOG')
    expect(screen.queryByLabelText('Recipient')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Message')).toBeRequired()
  })

  it('reactively validates required task parameters', async () => {
    installServer([])
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: /new schedule/i }))
    await user.type(screen.getByLabelText('Schedule name'), 'Email report')
    await user.selectOptions(screen.getByLabelText('Predefined task'), 'DUMMY_EMAIL')
    await user.selectOptions(screen.getByLabelText('Scheduling mode'), 'CRON')
    await user.type(screen.getByLabelText('Quartz cron expression'), '0 0 9 ? * MON')
    const submit = screen.getByRole('button', { name: 'Create schedule' })
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Recipient'), 'person@example.test')
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Subject'), 'Subject')
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Body'), 'Body')
    expect(submit).toBeEnabled()
  })

  it('reactively validates all four modes and ignores stale mode values', async () => {
    const server = installServer([])
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: /new schedule/i }))
    const submit = screen.getByRole('button', { name: 'Create schedule' })
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Schedule name'), 'Mode validation')
    await user.selectOptions(screen.getByLabelText('Predefined task'), 'LOG')
    await user.type(screen.getByLabelText('Message'), 'hello')

    fireEvent.change(screen.getByLabelText('Execution date and time'), { target: { value: '2020-01-01T10:00' } })
    expect(submit).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Execution date and time'), { target: { value: '2099-01-01T10:00' } })
    expect(submit).toBeEnabled()

    const mode = screen.getByLabelText('Scheduling mode')
    await user.selectOptions(mode, 'INTERVAL')
    const interval = screen.getByLabelText('Repeat every')
    expect(interval).toHaveValue(1)
    expect(submit).toBeEnabled()
    await user.clear(interval)
    expect(submit).toBeDisabled()
    await user.type(interval, '1.5')
    expect(submit).toBeDisabled()
    await user.clear(interval)
    await user.type(interval, '2')
    expect(submit).toBeEnabled()

    await user.selectOptions(mode, 'WEEKLY')
    expect(submit).toBeDisabled()
    await user.click(screen.getByLabelText('Mon'))
    expect(submit).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Execution time (UTC)'), { target: { value: '09:30' } })
    expect(submit).toBeEnabled()

    await user.selectOptions(mode, 'CRON')
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Quartz cron expression'), '0 0/15 * ? * *')
    expect(submit).toBeEnabled()
    expect(server.requests).toHaveLength(0)
  })

  it('revalidates inside the submit handler even when submission is invoked directly', async () => {
    const server = installServer([])
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: /new schedule/i }))
    const form = screen.getByRole('button', { name: 'Create schedule' }).closest('form')
    expect(form).not.toBeNull()
    fireEvent.submit(form!)
    expect(await screen.findByText('Schedule name is required.')).toBeInTheDocument()
    expect(server.requests).toHaveLength(0)
  })

  it('creates a schedule and clears fields from other timing modes', async () => {
    const server = installServer([])
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: /new schedule/i }))
    await user.type(screen.getByLabelText('Schedule name'), 'Weekly log')
    await user.selectOptions(screen.getByLabelText('Predefined task'), 'LOG')
    await user.type(screen.getByLabelText('Message'), 'weekly message')
    await user.selectOptions(screen.getByLabelText('Scheduling mode'), 'WEEKLY')
    await user.click(screen.getByLabelText('Mon'))
    await user.type(screen.getByLabelText('Execution time (UTC)'), '09:30')
    await user.click(screen.getByRole('button', { name: 'Create schedule' }))
    expect(await screen.findByText('Weekly log')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Schedule created successfully.')
    expect(server.requests[0]).toMatchObject({ scheduleType: 'WEEKLY', daysOfWeek: ['MONDAY'], timeOfDay: '09:30:00', executionTime: null, intervalValue: null, intervalUnit: null, cronExpression: null, parameters: { message: 'weekly message' } })
  })

  it('edits an existing schedule and refreshes the table', async () => {
    const server = installServer()
    const user = userEvent.setup()
    render(<App />)
    await screen.findByText('Heartbeat log')
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    expect(screen.getByLabelText('Repeat every')).toHaveValue(15)
    expect(screen.getByLabelText('Interval unit')).toHaveValue('MINUTES')
    const submit = screen.getByRole('button', { name: 'Save changes' })
    expect(submit).toBeEnabled()
    await user.clear(screen.getByLabelText('Repeat every'))
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Repeat every'), '15')
    expect(submit).toBeEnabled()
    const name = screen.getByLabelText('Schedule name')
    await user.clear(name)
    await user.type(name, 'Updated heartbeat')
    await user.click(submit)
    expect(await screen.findByText('Updated heartbeat')).toBeInTheDocument()
    expect(server.requests[0]).toMatchObject({ scheduleType: 'INTERVAL', intervalValue: 15, intervalUnit: 'MINUTES' })
  })

  it('allows non-timing edits to a disabled completed one-time schedule', async () => {
    const server = installServer([completedOneTime])
    const user = userEvent.setup()
    render(<App />)
    await screen.findByText('Completed reminder')
    await user.click(screen.getByRole('button', { name: 'Edit' }))

    const submit = screen.getByRole('button', { name: 'Save changes' })
    expect(screen.getByLabelText('Execution date and time')).toHaveValue('2020-01-01T12:00')
    expect(submit).toBeEnabled()
    const name = screen.getByLabelText('Schedule name')
    await user.clear(name)
    await user.type(name, 'Archived reminder')
    await user.click(submit)

    expect(await screen.findByText('Archived reminder')).toBeInTheDocument()
    expect(server.requests[0]).toMatchObject({ enabled: false, executionTime: '2020-01-01T10:00:00.000Z' })
  })

  it('allows disabling an existing completed one-time schedule without changing its time', async () => {
    const server = installServer([{ ...completedOneTime, enabled: true }])
    const user = userEvent.setup()
    render(<App />)
    await screen.findByText('Completed reminder')
    await user.click(screen.getByRole('button', { name: 'Edit' }))

    const submit = screen.getByRole('button', { name: 'Save changes' })
    expect(submit).toBeDisabled()
    expect(screen.getByText(/completed one-time schedule cannot be enabled/i)).toBeInTheDocument()
    await user.click(screen.getByRole('checkbox', { name: /Enabled/ }))
    expect(submit).toBeEnabled()
    await user.click(submit)

    expect(server.requests[0]).toMatchObject({ enabled: false, executionTime: '2020-01-01T10:00:00.000Z' })
  })

  it('blocks re-enabling a completed one-time schedule until its time is in the future', async () => {
    installServer([completedOneTime])
    const user = userEvent.setup()
    render(<App />)
    await screen.findByText('Completed reminder')
    await user.click(screen.getByRole('button', { name: 'Edit' }))

    const submit = screen.getByRole('button', { name: 'Save changes' })
    await user.click(screen.getByRole('checkbox', { name: /Enabled/ }))
    expect(submit).toBeDisabled()
    expect(screen.getByText(/completed one-time schedule cannot be enabled/i)).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Execution date and time'), { target: { value: '2099-01-01T10:00' } })
    expect(screen.queryByText(/completed one-time schedule cannot be enabled/i)).not.toBeInTheDocument()
    expect(submit).toBeEnabled()
  })

  it('confirms and deletes a schedule', async () => {
    const server = installServer()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    render(<App />)
    await screen.findByText('Heartbeat log')
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('Heartbeat log'))
    expect(await screen.findByText('No schedules yet')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Schedule deleted successfully.')
    expect(server.fetchMock).toHaveBeenCalledWith('/api/schedules/1', { method: 'DELETE' })
  })

  it('displays backend validation errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if (!init && String(input) === '/api/tasks') return json(tasks)
      if (!init && String(input) === '/api/schedules') return json([])
      if (init?.method === 'POST') return json({ message: 'Schedule validation failed', fieldErrors: { cronExpression: 'must be a valid Quartz cron expression' } }, 400)
      throw new Error('Unexpected request')
    })
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: /new schedule/i }))
    await user.type(screen.getByLabelText('Schedule name'), 'Bad cron')
    await user.selectOptions(screen.getByLabelText('Predefined task'), 'LOG')
    await user.type(screen.getByLabelText('Message'), 'test')
    await user.selectOptions(screen.getByLabelText('Scheduling mode'), 'CRON')
    await user.type(screen.getByLabelText('Quartz cron expression'), 'invalid')
    await user.click(screen.getByRole('button', { name: 'Create schedule' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Schedule validation failed')
    expect(screen.getByText('must be a valid Quartz cron expression')).toBeInTheDocument()
  })

  it('shows a retry state when initial API loading fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      json({ message: 'Backend unavailable' }, 503),
    )
    render(<App />)
    const alerts = await screen.findAllByRole('alert')
    expect(alerts[0]).toHaveTextContent('Backend unavailable')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
