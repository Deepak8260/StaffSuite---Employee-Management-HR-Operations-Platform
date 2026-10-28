import { CalendarPlus, ListChecks } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createLeaveType, listLeaveTypes, updateLeaveType } from '../../api/leave'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill from '../../components/ui/StatusPill'
import type { LeaveType, LeaveTypeInput } from '../../types/leave'

export default function LeaveTypesPage() {
  const [types, setTypes] = useState<LeaveType[]>([])
  const [editing, setEditing] = useState<LeaveType | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [days, setDays] = useState('')
  const [carry, setCarry] = useState(false)
  const [maxCarry, setMaxCarry] = useState('0')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setTypes(await listLeaveTypes(true))
    } catch (err) {
      setError(errorMessage(err, 'Could not load leave types'))
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function startEdit(t: LeaveType | null) {
    setEditing(t)
    setName(t?.name ?? '')
    setDescription(t?.description ?? '')
    setDays(t ? String(t.annualEntitlementDays) : '')
    setCarry(t?.carryForwardAllowed ?? false)
    setMaxCarry(t ? String(t.maxCarryForwardDays) : '0')
    setError(null)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const input: LeaveTypeInput = {
      name,
      description,
      annualEntitlementDays: Number(days),
      carryForwardAllowed: carry,
      maxCarryForwardDays: carry ? Number(maxCarry) : 0,
    }
    try {
      if (editing) await updateLeaveType(editing.id, { ...input, active: editing.active })
      else await createLeaveType(input)
      startEdit(null)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not save leave type'))
    }
  }

  async function setActive(t: LeaveType, active: boolean) {
    setError(null)
    try {
      await updateLeaveType(t.id, {
        name: t.name,
        description: t.description ?? '',
        annualEntitlementDays: t.annualEntitlementDays,
        carryForwardAllowed: t.carryForwardAllowed,
        maxCarryForwardDays: t.maxCarryForwardDays,
        active,
      })
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not update leave type'))
    }
  }

  const columns: Column<LeaveType>[] = [
    { key: 'name', header: 'Name', render: (t) => t.name },
    { key: 'days', header: 'Days / year', render: (t) => t.annualEntitlementDays },
    { key: 'carry', header: 'Carry-forward', render: (t) => (t.carryForwardAllowed ? `Up to ${t.maxCarryForwardDays} days` : 'No') },
    { key: 'desc', header: 'Description', render: (t) => t.description ?? '—' },
    { key: 'status', header: 'Status', render: (t) => <StatusPill tone={t.active ? 'success' : 'neutral'}>{t.active ? 'Active' : 'Inactive'}</StatusPill> },
    { key: 'actions', header: '', align: 'right', render: (t) => (
      <span className="actions">
        {t.active ? (
          <>
            <button onClick={() => startEdit(t)}>Edit</button>
            <button onClick={() => setActive(t, false)}>Deactivate</button>
          </>
        ) : (
          <button onClick={() => setActive(t, true)}>Restore</button>
        )}
      </span>
    ) },
  ]

  return (
    <main className="page">
      <PageHeader title="Leave types" description="Define the kinds of leave employees can request." />
      <SectionCard title={editing ? `Edit "${editing.name}"` : 'New leave type'} icon={CalendarPlus} tint="orange">
        <form onSubmit={handleSubmit} className="inline-form">
          <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} required />
          <input type="number" min={0} max={366} step="0.5" placeholder="Days per year" value={days}
            onChange={(e) => setDays(e.target.value)} required />
          <label className="check">
            <input type="checkbox" checked={carry} onChange={(e) => setCarry(e.target.checked)} />
            Allow carry-forward
          </label>
          <input type="number" min={0} max={366} step="0.5" placeholder="Max carry-forward days" value={carry ? maxCarry : '0'}
            disabled={!carry} onChange={(e) => setMaxCarry(e.target.value)} />
          <input placeholder="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} size={30} />
          <button type="submit">{editing ? 'Save' : 'Create'}</button>
          {editing && <button type="button" onClick={() => startEdit(null)}>Cancel</button>}
        </form>
        <p className="hint">
          Changing the yearly days affects balances created from now on; balances already created for a year are not changed.
        </p>
      </SectionCard>

      {error && <p className="error">{error}</p>}
      <SectionCard title="All leave types" icon={ListChecks} tint="orange">
        <DataTable columns={columns} rows={types} rowKey={(t) => t.id}
          rowClassName={(t) => (t.active ? undefined : 'muted')} emptyText="No leave types yet. Create the first one above." />
      </SectionCard>
    </main>
  )
}
