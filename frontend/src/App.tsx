import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from './api/client'
import ScheduleForm from './components/ScheduleForm'
import ScheduleTable from './components/ScheduleTable'
import memcycoLogo from './assets/Memcyco-logo.svg'
import type { Schedule, ScheduleRequest, TaskDefinition } from './types'
import './styles.css'

export default function App() {
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [tasks, setTasks] = useState<TaskDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [actionSuccess, setActionSuccess] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Schedule | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError('')
    try {
      const [scheduleData, taskData] = await Promise.all([api.getSchedules(), api.getTasks()])
      setSchedules(scheduleData)
      setTasks(taskData)
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : 'Unable to load scheduling data.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
    setActionError('')
    setActionSuccess('')
  }

  const openEdit = (schedule: Schedule) => {
    setEditing(schedule)
    setFormOpen(true)
    setActionError('')
    setActionSuccess('')
  }

  const save = async (request: ScheduleRequest) => {
    const wasEditing = Boolean(editing)
    if (editing) await api.updateSchedule(editing.id, request)
    else await api.createSchedule(request)
    setSchedules(await api.getSchedules())
    setFormOpen(false)
    setEditing(null)
    setActionSuccess(wasEditing ? 'Schedule updated successfully.' : 'Schedule created successfully.')
  }

  const remove = async (schedule: Schedule) => {
    if (!window.confirm(`Delete “${schedule.name}”? This also removes its Quartz job.`)) return
    setDeletingId(schedule.id)
    setActionError('')
    setActionSuccess('')
    try {
      await api.deleteSchedule(schedule.id)
      setSchedules(await api.getSchedules())
      if (editing?.id === schedule.id) {
        setEditing(null)
        setFormOpen(false)
      }
      setActionSuccess('Schedule deleted successfully.')
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : 'Unable to delete the schedule.')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div className="header-brand">
          <img className="brand-logo" src={memcycoLogo} alt="Memcyco" />
          <div className="header-title"><h1>Scheduling System</h1><p className="header-copy">Create and manage persistent Quartz schedules.</p></div>
        </div>
        <button className="button button-primary" onClick={openCreate} disabled={loading || tasks.length === 0}>+ New schedule</button>
      </header>
      {loadError && <div className="alert alert-error load-alert" role="alert"><span>{loadError}</span><button className="button button-secondary button-small" onClick={() => void load()}>Retry</button></div>}
      {actionError && <div className="alert alert-error" role="alert">{actionError}</div>}
      {actionSuccess && <div className="alert alert-success" role="status">{actionSuccess}</div>}
      {formOpen && <ScheduleForm key={editing?.id ?? 'new'} tasks={tasks} schedule={editing} onSubmit={save} onCancel={() => { setFormOpen(false); setEditing(null) }} />}
      <section className="list-card" aria-labelledby="schedule-list-title">
        <div className="list-heading"><div><h2 id="schedule-list-title">Schedules</h2><p>{schedules.length} {schedules.length === 1 ? 'schedule' : 'schedules'}</p></div>{!loading && !loadError && <button className="text-button" onClick={() => void load()}>Refresh</button>}</div>
        {loading ? <div className="loading-state" role="status"><span className="spinner" />Loading schedules…</div>
          : loadError ? <div className="empty-state"><h2>Schedules unavailable</h2><p>Reconnect to the backend and try again.</p></div>
            : <ScheduleTable schedules={schedules} onEdit={openEdit} onDelete={(schedule) => void remove(schedule)} deletingId={deletingId} />}
      </section>
    </main>
  )
}
