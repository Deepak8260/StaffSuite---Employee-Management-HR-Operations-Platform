import { Phone, UserRound } from 'lucide-react'
import Flash from '../../components/Flash'
import AvatarInitials from '../../components/ui/AvatarInitials'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { useEffect, useState, type FormEvent } from 'react'
import { getEmployee, updateOwnProfile } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import { useAuth } from '../../hooks/useAuth'
import type { Employee } from '../../types/employee'
import { EmployeeFields } from './EmployeeDetailPage'

/** Self-service: only phone, address and emergency contact are editable. */
export default function MyProfilePage() {
  const { user } = useAuth()
  const employeeId = user?.employeeId ?? null
  const [employee, setEmployee] = useState<Employee | null>(null)
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [emergencyContact, setEmergencyContact] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  function fill(e: Employee) {
    setEmployee(e)
    setPhone(e.phone ?? '')
    setAddress(e.address ?? '')
    setEmergencyContact(e.emergencyContact ?? '')
  }

  useEffect(() => {
    if (employeeId === null) return
    getEmployee(employeeId).then(fill).catch((err) => setError(errorMessage(err, 'Could not load profile')))
  }, [employeeId])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (employeeId === null) return
    setError(null)
    setSaved(false)
    setBusy(true)
    try {
      fill(await updateOwnProfile(employeeId, { phone, address, emergencyContact }))
      setSaved(true)
    } catch (err) {
      setError(errorMessage(err, 'Could not save profile'))
    } finally {
      setBusy(false)
    }
  }

  if (employeeId === null) {
    return (
      <main className="page">
        <PageHeader title="My profile" description="Your personal and job details." />
        <p>No employee record is linked to your login, so there is no profile to show.</p>
      </main>
    )
  }

  return (
    <main className="page">
      <PageHeader title="My profile" description="Your personal and job details." />
      {error && !employee && <p className="error">{error}</p>}
      {employee && (
        <>
          <SectionCard title="Personal details" icon={UserRound} tint="blue">
            <div className="person profile-id">
              <AvatarInitials name={employee.fullName} size="lg" />
              <div className="person-text">
                <span className="person-name">{employee.fullName}</span>
                <span className="person-meta">{employee.jobTitle}</span>
              </div>
            </div>
            <EmployeeFields employee={employee} />
          </SectionCard>
          <SectionCard title="Contact details" icon={Phone} tint="green">
          <p className="hint">You can update these fields. Everything else is managed by HR.</p>
          <form onSubmit={handleSubmit} className="form narrow-form">
            <label>Phone
              <input value={phone} onChange={(e) => setPhone(e.target.value)} maxLength={25} />
            </label>
            <label>Address
              <input value={address} onChange={(e) => setAddress(e.target.value)} maxLength={500} />
            </label>
            <label>Emergency contact
              <input value={emergencyContact} onChange={(e) => setEmergencyContact(e.target.value)} maxLength={150} />
            </label>
            {error && <p className="error">{error}</p>}
            <Flash message={saved ? 'Profile updated.' : null} onDone={() => setSaved(false)} />
            <button type="submit" disabled={busy}>Save changes</button>
          </form>
          </SectionCard>
        </>
      )}
    </main>
  )
}
