import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createLeaveType, listLeaveTypes, updateLeaveType } from '../../api/leave'
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

  return (
    <main className="page">
      <h1>Leave types</h1>
      <section>
        <h2>{editing ? `Edit "${editing.name}"` : 'New leave type'}</h2>
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
      </section>

      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr><th>Name</th><th>Days / year</th><th>Carry-forward</th><th>Description</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>
          {types.map((t) => (
            <tr key={t.id} className={t.active ? undefined : 'muted'}>
              <td>{t.name}</td>
              <td>{t.annualEntitlementDays}</td>
              <td>{t.carryForwardAllowed ? `Up to ${t.maxCarryForwardDays} days` : 'No'}</td>
              <td>{t.description ?? '—'}</td>
              <td>{t.active ? 'Active' : 'Inactive'}</td>
              <td className="actions">
                {t.active ? (
                  <>
                    <button onClick={() => startEdit(t)}>Edit</button>
                    <button onClick={() => setActive(t, false)}>Deactivate</button>
                  </>
                ) : (
                  <button onClick={() => setActive(t, true)}>Restore</button>
                )}
              </td>
            </tr>
          ))}
          {types.length === 0 && <tr><td colSpan={6}>No leave types yet. Create the first one above.</td></tr>}
        </tbody>
      </table>
    </main>
  )
}
