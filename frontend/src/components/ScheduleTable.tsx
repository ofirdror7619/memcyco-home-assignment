import type { Schedule } from '../types'
import { describeTiming, formatEnum } from '../utils/schedule'

interface Props {
  schedules: Schedule[]
  onEdit: (schedule: Schedule) => void
  onDelete: (schedule: Schedule) => void
  deletingId: number | null
}

export default function ScheduleTable({ schedules, onEdit, onDelete, deletingId }: Props) {
  if (schedules.length === 0) {
    return (
      <div className="empty-state">
        <h2>No schedules yet</h2>
        <p>Create your first schedule to automate a predefined task.</p>
      </div>
    )
  }

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Name</th><th>Task</th><th>Mode</th><th>Timing</th><th>Status</th><th className="actions-heading">Actions</th>
          </tr>
        </thead>
        <tbody>
          {schedules.map((schedule) => (
            <tr key={schedule.id}>
              <td data-label="Name"><strong>{schedule.name}</strong></td>
              <td data-label="Task">{formatEnum(schedule.taskType)}</td>
              <td data-label="Mode">{formatEnum(schedule.scheduleType)}</td>
              <td data-label="Timing" className="timing-cell">{describeTiming(schedule)}</td>
              <td data-label="Status">
                <span className={`badge ${schedule.enabled ? 'badge-enabled' : 'badge-disabled'}`}>
                  {schedule.enabled ? 'Enabled' : 'Disabled'}
                </span>
              </td>
              <td data-label="Actions" className="row-actions">
                <button className="button button-secondary button-small" onClick={() => onEdit(schedule)}>Edit</button>
                <button className="button button-danger button-small" onClick={() => onDelete(schedule)} disabled={deletingId === schedule.id}>
                  {deletingId === schedule.id ? 'Deleting…' : 'Delete'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
