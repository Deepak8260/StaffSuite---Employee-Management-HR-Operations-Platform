import { CalendarPlus, PartyPopper } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createHoliday, deleteHoliday, listHolidays, updateHoliday } from '../../api/leave'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill from '../../components/ui/StatusPill'
import type { Holiday } from '../../types/leave'
import { fromISO } from '../../utils/dates'

export default function HolidaysPage() {
  const [year, setYear] = useState(new Date().getFullYear())
  const [holidays, setHolidays] = useState<Holiday[]>([])
  const [editing, setEditing] = useState<Holiday | null>(null)
  const [name, setName] = useState('')
  const [date, setDate] = useState('')
  const [optional, setOptional] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setHolidays(await listHolidays(year))
    } catch (err) {
      setError(errorMessage(err, 'Could not load holidays'))
    }
  }, [year])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function startEdit(h: Holiday | null) {
    setEditing(h)
    setName(h?.name ?? '')
    setDate(h?.holidayDate ?? '')
    setOptional(h?.optional ?? false)
    setError(null)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const input = { name, holidayDate: date, optional }
    try {
      if (editing) await updateHoliday(editing.id, input)
      else await createHoliday(input)
      const savedYear = Number(date.slice(0, 4))
      startEdit(null)
      if (savedYear !== year) setYear(savedYear)
      else await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not save holiday'))
    }
  }

  async function handleDelete(h: Holiday) {
    if (!window.confirm(`Delete "${h.name}" on ${h.holidayDate}?`)) return
    setError(null)
    try {
      await deleteHoliday(h.id)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not delete holiday'))
    }
  }

  const columns: Column<Holiday>[] = [
    { key: 'date', header: 'Date', render: (h) => fromISO(h.holidayDate).toLocaleDateString() },
    { key: 'day', header: 'Day', render: (h) => fromISO(h.holidayDate).toLocaleDateString(undefined, { weekday: 'long' }) },
    { key: 'name', header: 'Name', render: (h) => h.name },
    { key: 'type', header: 'Type', render: (h) => <StatusPill tone={h.optional ? 'info' : 'success'}>{h.optional ? 'Optional' : 'Public holiday'}</StatusPill> },
    { key: 'actions', header: '', align: 'right', render: (h) => (
      <span className="actions">
        <button onClick={() => startEdit(h)}>Edit</button>
        <button className="danger" onClick={() => handleDelete(h)}>Delete</button>
      </span>
    ) },
  ]

  return (
    <main className="page">
      <PageHeader title="Public holidays" description="Maintain the company holiday list for each year."
        actions={(
          <>
            <button onClick={() => setYear(year - 1)}>‹</button>
            <strong>{year}</strong>
            <button onClick={() => setYear(year + 1)}>›</button>
          </>
        )} />

      <SectionCard title={editing ? `Edit "${editing.name}"` : 'New holiday'} icon={CalendarPlus} tint="orange">
        <form onSubmit={handleSubmit} className="inline-form">
          <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} maxLength={120} required />
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
          <label className="check">
            <input type="checkbox" checked={optional} onChange={(e) => setOptional(e.target.checked)} />
            Optional (does not reduce leave days)
          </label>
          <button type="submit">{editing ? 'Save' : 'Add'}</button>
          {editing && <button type="button" onClick={() => startEdit(null)}>Cancel</button>}
        </form>
        <p className="hint">
          Holidays added or removed later do not change days already counted on existing leave requests.
        </p>
      </SectionCard>

      {error && <p className="error">{error}</p>}
      <SectionCard title={`Holidays in ${year}`} icon={PartyPopper} tint="orange">
        <DataTable columns={columns} rows={holidays} rowKey={(h) => h.id} emptyText={`No holidays defined for ${year}.`} />
      </SectionCard>
    </main>
  )
}
