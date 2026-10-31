import { ArrowLeft, Briefcase, Camera, CalendarCheck, CalendarX, Clock, FileText, Pencil, Plane, Upload, UserRound, Users } from 'lucide-react'
import { useEffect, useRef, useState, type ChangeEvent, type MouseEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getProfile, uploadEmployeePhoto } from '../../api/employees'
import { downloadDocument } from '../../api/documents'
import { errorMessage } from '../../api/axiosInstance'
import Flash from '../../components/Flash'
import AvatarInitials from '../../components/ui/AvatarInitials'
import DataTable, { type Column } from '../../components/ui/DataTable'
import EmployeeLink from '../../components/ui/EmployeeLink'
import PhotoAvatar from '../../components/ui/PhotoAvatar'
import SectionCard from '../../components/ui/SectionCard'
import StatCard from '../../components/ui/StatCard'
import StatusPill, { StatusCodePill, toneFor } from '../../components/ui/StatusPill'
import type { Tint } from '../../components/ui/tints'
import { useAuth } from '../../hooks/useAuth'
import type { EmployeeDocument } from '../../types/documents'
import type { DirectoryEntry, EmployeeProfile } from '../../types/employee'
import { canManageEmployees, formatDate } from '../../utils/roles'
import DocumentList from '../documents/DocumentList'
import StoredPdfViewer from '../documents/StoredPdfViewer'

const MAX_PHOTO_BYTES = 5 * 1024 * 1024
const LEAVE_TINTS: Tint[] = ['blue', 'green', 'orange', 'purple', 'red', 'gray']

const reportColumns: Column<DirectoryEntry>[] = [
  { key: 'name', header: 'Name', render: (r) => (
    <span className="person"><AvatarInitials name={r.fullName} size="sm" />
      <EmployeeLink id={r.id} className="person-name">{r.fullName}</EmployeeLink></span>
  ) },
  { key: 'title', header: 'Job title', render: (r) => r.jobTitle },
]

function days(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

function Skeleton({ lines = 3, tall = false }: { lines?: number; tall?: boolean }) {
  return (
    <div className="skeleton-block" aria-hidden="true">
      {Array.from({ length: lines }, (_, i) => <span key={i} className={`skeleton-line ${tall ? 'tall' : ''}`} style={{ width: `${92 - i * 14}%` }} />)}
    </div>
  )
}

function KeyValues({ rows }: { rows: [string, string][] }) {
  return (
    <dl className="kv">
      {rows.map(([label, value]) => (
        <div key={label}><dt>{label}</dt><dd>{value}</dd></div>
      ))}
    </dl>
  )
}

/** One scrolling page with everything about an employee. What it shows depends on what the backend allows this viewer. */
export default function EmployeeProfilePage() {
  const { id } = useParams()
  const employeeId = Number(id)
  const { user } = useAuth()
  const navigate = useNavigate()
  const fileInput = useRef<HTMLInputElement>(null)

  const [profile, setProfile] = useState<EmployeeProfile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [viewing, setViewing] = useState<EmployeeDocument | null>(null)
  const [photoVersion, setPhotoVersion] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [notice, setNotice] = useState<{ text: string; kind: 'success' | 'error' } | null>(null)

  const canManage = canManageEmployees(user?.role)
  const isSelf = user?.employeeId === employeeId
  const canChangePhoto = canManage || isSelf

  useEffect(() => {
    let cancelled = false
    getProfile(employeeId)
      .then((p) => { if (!cancelled) { setProfile(p); setError(null) } })
      .catch((err) => { if (!cancelled) setError(errorMessage(err, 'Could not load this profile')) })
    return () => { cancelled = true }
  }, [employeeId])

  // a different person's profile (e.g. via "Reports to") starts from the skeletons again
  const current = profile && profile.id === employeeId ? profile : null

  function goBack(e: MouseEvent) {
    // return to wherever the list was opened from (its filters and page are remembered), else the plain list
    if (window.history.state && typeof window.history.state.idx === 'number' && window.history.state.idx > 0) {
      e.preventDefault()
      navigate(-1)
    }
  }

  async function onPhotoChosen(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    if (file.type !== 'image/jpeg' && file.type !== 'image/png') {
      setNotice({ text: 'Please choose a JPG or PNG image.', kind: 'error' })
      return
    }
    if (file.size > MAX_PHOTO_BYTES) {
      setNotice({ text: 'The image is too large (maximum 5 MB).', kind: 'error' })
      return
    }
    setUploading(true)
    try {
      const updated = await uploadEmployeePhoto(employeeId, file)
      setProfile((p) => (p ? { ...p, profilePhotoUrl: updated.profilePhotoUrl } : p))
      setPhotoVersion((v) => v + 1)
      setNotice({ text: 'Photo updated.', kind: 'success' })
    } catch (err) {
      setNotice({ text: errorMessage(err, 'Could not upload the photo'), kind: 'error' })
    } finally {
      setUploading(false)
    }
  }

  const header = (
    <div className="profile-bar">
      <Link to="/employees" className="back-link" onClick={goBack}><ArrowLeft size={16} aria-hidden="true" /> Back to Employees</Link>
      {canManage && current && (
        <Link to={`/employees/${employeeId}/edit`} className="button"><Pencil size={15} aria-hidden="true" /> Edit</Link>
      )}
    </div>
  )

  if (error) {
    return (
      <main className="page profile-page">
        {header}
        <p className="error">{error}</p>
      </main>
    )
  }

  if (!current) {
    return (
      <main className="page profile-page" aria-busy="true">
        {header}
        <SectionCard>
          <div className="profile-head">
            <div className="skeleton-circle" />
            <div className="profile-head-text"><Skeleton lines={3} tall /></div>
          </div>
        </SectionCard>
        <div className="profile-grid">
          {['Contact & Employment', 'Leave Summary', 'Attendance Summary', 'Documents'].map((t) => (
            <SectionCard key={t} title={t}><Skeleton lines={4} /></SectionCard>
          ))}
        </div>
      </main>
    )
  }

  const full = current.accessLevel === 'FULL'
  const reports = current.directReports ?? []
  const leaveHref = isSelf ? '/leave' : canManage ? `/leave/all?employee=${employeeId}` : '/leave/team'
  const attendanceHref = isSelf ? '/attendance' : canManage ? `/attendance/reports/employee/${employeeId}` : '/attendance/team'
  const uploadHref = isSelf ? '/documents' : `/admin/documents?employee=${employeeId}`
  const att = current.attendance
  const today = att?.todayStatus ?? null

  return (
    <main className="page profile-page">
      {header}

      <SectionCard>
        <div className="profile-head">
          <div className="profile-photo">
            <PhotoAvatar employeeId={employeeId} name={current.fullName} photoUrl={current.profilePhotoUrl} version={photoVersion} />
            {canChangePhoto && (
              <>
                <button type="button" className="photo-edit" aria-label="Change photo" title="Change photo"
                  disabled={uploading} onClick={() => fileInput.current?.click()}>
                  <Camera size={16} aria-hidden="true" />
                </button>
                <input ref={fileInput} type="file" accept="image/png,image/jpeg" hidden onChange={onPhotoChosen} />
              </>
            )}
          </div>
          <div className="profile-head-text">
            <h1>{current.fullName}</h1>
            <p className="profile-title">{current.jobTitle}</p>
            <div className="chips">
              {current.employeeCode && <span className="chip">{current.employeeCode}</span>}
              {current.departmentName && <span className="chip">{current.departmentName}</span>}
              {current.employmentStatus && <StatusCodePill status={current.employmentStatus} />}
            </div>
            {current.managerId != null && current.managerName && (
              <p className="reports-to">Reports to: <EmployeeLink id={current.managerId}>{current.managerName}</EmployeeLink></p>
            )}
          </div>
        </div>
      </SectionCard>

      <div className="profile-grid">
        <SectionCard title="Contact & Employment" icon={Briefcase} tint="blue">
          {full ? (
            <KeyValues rows={[
              ['Email', current.email ?? '—'],
              ['Phone', current.phone ?? '—'],
              ['Date of birth', formatDate(current.dateOfBirth)],
              ['Joining date', formatDate(current.joiningDate)],
              ['Designation', current.designationName ?? '—'],
            ]} />
          ) : (
            <KeyValues rows={[
              ['Job title', current.jobTitle],
              ['Department', current.departmentName ?? '—'],
            ]} />
          )}
        </SectionCard>

        {full && current.leave && (
          <SectionCard title="Leave Summary" icon={Plane} tint="green">
            {current.leave.balances.length === 0 ? <p className="hint">No leave balances yet.</p> : (
              <div className="mini-stats">
                {current.leave.balances.map((b, i) => (
                  <StatCard key={b.leaveTypeId} icon={Plane} tint={LEAVE_TINTS[i % LEAVE_TINTS.length]} label={b.leaveTypeName}
                    value={`${days(b.remainingDays)}/${days(b.allocatedDays + b.carriedForwardDays)}`} trend="days remaining" />
                ))}
              </div>
            )}
            <p className="card-line">
              {current.leave.pendingRequests} pending request{current.leave.pendingRequests === 1 ? '' : 's'}
              <Link to={leaveHref} className="card-link">View leave history →</Link>
            </p>
          </SectionCard>
        )}

        {full && att && (
          <SectionCard title="Attendance Summary" icon={Clock} tint="orange">
            <div className="mini-stats">
              <StatCard icon={CalendarCheck} tint="green" label="Present" value={att.present} trend="this month" />
              <StatCard icon={CalendarX} tint="red" label="Absent" value={att.absent} trend="this month" />
              <StatCard icon={Clock} tint="orange" label="Late" value={att.late} trend="this month" />
            </div>
            <p className="card-line">
              {today ? <StatusPill tone={toneFor(today)}>Today: {today.charAt(0) + today.slice(1).toLowerCase().replace(/_/g, ' ')}</StatusPill>
                : <StatusPill tone="neutral">Today: not marked</StatusPill>}
              <Link to={attendanceHref} className="card-link">View attendance records →</Link>
            </p>
          </SectionCard>
        )}

        {full && current.documents && (
          <SectionCard title="Documents" icon={FileText} tint="purple" className="profile-docs"
            actions={(canManage || isSelf) ? <Link to={uploadHref} className="button secondary"><Upload size={15} aria-hidden="true" /> Upload</Link> : undefined}>
            <DocumentList documents={current.documents} onView={setViewing} onDownload={(d) => { void downloadDocument(d) }} emptyText="No documents yet." />
          </SectionCard>
        )}
      </div>

      {full && reports.length > 0 && (
        <SectionCard title="Direct Reports" icon={Users} tint="blue">
          <DataTable columns={reportColumns} rows={reports} rowKey={(r) => r.id} />
        </SectionCard>
      )}

      {!full && (
        <p className="hint profile-note"><UserRound size={14} aria-hidden="true" /> You can see directory details for this person.</p>
      )}

      {viewing && <StoredPdfViewer document={viewing} onClose={() => setViewing(null)} />}
      <Flash message={notice?.text ?? null} kind={notice?.kind} onDone={() => setNotice(null)} />
    </main>
  )
}
