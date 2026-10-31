import { ArrowLeft, Briefcase, CalendarCheck, CalendarX, CheckCircle2, Circle, Clock, FileText, Eye, ImagePlus, Pencil, Plane, SlidersHorizontal, Trash2, Upload, UserRound, Users, X } from 'lucide-react'
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type MouseEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { fetchPhotoObjectUrl, getProfile, removeEmployeePhoto, updateOwnProfile, uploadEmployeePhoto } from '../../api/employees'
import { downloadDocument } from '../../api/documents'
import { errorMessage } from '../../api/axiosInstance'
import Flash from '../../components/Flash'
import PhotoEditorModal from '../../components/PhotoEditorModal'
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
    <span className="person">      <EmployeeLink id={r.id} className="person-name">{r.fullName}</EmployeeLink></span>
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
  const [editing, setEditing] = useState<File | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [viewUrl, setViewUrl] = useState<string | null>(null)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const [contact, setContact] = useState({ phone: '', address: '', emergencyContact: '' })
  const [savingContact, setSavingContact] = useState(false)
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

  useEffect(() => {
    if (!menuOpen) return
    const onDown = (e: globalThis.MouseEvent) => { if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [menuOpen])

  useEffect(() => () => { if (viewUrl) URL.revokeObjectURL(viewUrl) }, [viewUrl])

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
    setEditing(file)
  }

  /** The edited picture (cropped, adjusted) is what gets uploaded as the profile photo. */
  async function savePhoto(edited: File) {
    setUploading(true)
    try {
      await uploadEmployeePhoto(employeeId, edited)
      setEditing(null)
      setProfile(await getProfile(employeeId))   // the photo and the completion bar both change
      setPhotoVersion((v) => v + 1)
      setNotice({ text: 'Photo updated.', kind: 'success' })
    } catch (err) {
      setNotice({ text: errorMessage(err, 'Could not upload the photo'), kind: 'error' })
    } finally {
      setUploading(false)
    }
  }

  /** The photo as it is stored, for viewing or for editing again. */
  async function currentPhoto(): Promise<Blob> {
    const url = await fetchPhotoObjectUrl(employeeId)
    try {
      return await (await fetch(url)).blob()
    } finally {
      URL.revokeObjectURL(url)
    }
  }

  async function viewPhoto() {
    setMenuOpen(false)
    try {
      setViewUrl(URL.createObjectURL(await currentPhoto()))
    } catch (err) {
      setNotice({ text: errorMessage(err, 'Could not open the photo'), kind: 'error' })
    }
  }

  async function editPhoto() {
    setMenuOpen(false)
    try {
      const blob = await currentPhoto()
      setEditing(new File([blob], 'photo.jpg', { type: blob.type || 'image/jpeg' }))
    } catch (err) {
      setNotice({ text: errorMessage(err, 'Could not open the photo'), kind: 'error' })
    }
  }

  async function removePhoto() {
    setUploading(true)
    try {
      await removeEmployeePhoto(employeeId)
      setConfirmRemove(false)
      setProfile(await getProfile(employeeId))
      setPhotoVersion((v) => v + 1)
      setNotice({ text: 'Photo removed.', kind: 'success' })
    } catch (err) {
      setNotice({ text: errorMessage(err, 'Could not remove the photo'), kind: 'error' })
    } finally {
      setUploading(false)
    }
  }

  async function saveContact(e: FormEvent) {
    e.preventDefault()
    const body: { phone?: string; address?: string; emergencyContact?: string } = {}
    if (contact.phone.trim()) body.phone = contact.phone.trim()
    if (contact.address.trim()) body.address = contact.address.trim()
    if (contact.emergencyContact.trim()) body.emergencyContact = contact.emergencyContact.trim()
    if (Object.keys(body).length === 0) return
    setSavingContact(true)
    try {
      await updateOwnProfile(employeeId, body)
      setProfile(await getProfile(employeeId))
      setContact({ phone: '', address: '', emergencyContact: '' })
      setNotice({ text: 'Details saved.', kind: 'success' })
    } catch (err) {
      setNotice({ text: errorMessage(err, 'Could not save your details'), kind: 'error' })
    } finally {
      setSavingContact(false)
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
          <div className="profile-photo" ref={menuRef}>
            {current.profilePhotoUrl ? (
              <button type="button" className="photo-open" aria-label="View photo" onClick={viewPhoto}>
                <PhotoAvatar employeeId={employeeId} name={current.fullName} photoUrl={current.profilePhotoUrl} version={photoVersion} />
              </button>
            ) : (
              <PhotoAvatar employeeId={employeeId} name={current.fullName} photoUrl={null} version={photoVersion} />
            )}
            {canChangePhoto && (
              <>
                <button type="button" className="photo-edit" aria-label="Photo options" title="Photo options" aria-haspopup="menu" aria-expanded={menuOpen}
                  disabled={uploading} onClick={() => setMenuOpen((o) => !o)}>
                  <Pencil size={16} aria-hidden="true" />
                </button>
                {menuOpen && (
                  <div className="photo-menu" role="menu">
                    {current.profilePhotoUrl && <button type="button" role="menuitem" onClick={viewPhoto}><Eye size={16} aria-hidden="true" /> View photo</button>}
                    {current.profilePhotoUrl && <button type="button" role="menuitem" onClick={editPhoto}><SlidersHorizontal size={16} aria-hidden="true" /> Edit photo</button>}
                    <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); fileInput.current?.click() }}>
                      <ImagePlus size={16} aria-hidden="true" /> {current.profilePhotoUrl ? 'Upload new photo' : 'Upload photo'}
                    </button>
                    {current.profilePhotoUrl && <button type="button" role="menuitem" className="danger-item" onClick={() => { setMenuOpen(false); setConfirmRemove(true) }}><Trash2 size={16} aria-hidden="true" /> Remove photo</button>}
                  </div>
                )}
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

      {full && current.completion && (() => {
        const c = current.completion
        const missing = (key: string) => c.items.some((i) => i.key === key && !i.done)
        return (
          <SectionCard title="Profile completion" icon={CheckCircle2} tint={c.complete ? 'green' : 'orange'}>
            <div className="completion-top">
              <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={c.percent} aria-label="Profile completion">
                <span className={c.complete ? 'done' : ''} style={{ width: `${c.percent}%` }} />
              </div>
              <strong className="completion-percent">{c.percent}%</strong>
            </div>
            <p className="hint completion-hint">
              {c.complete ? 'Profile complete.' : isSelf ? 'Complete these to reach 100%. Your manager and the administrators are told when you do.' : `${current.fullName} still needs to complete the items below.`}
            </p>
            <ul className="completion-list">
              {c.items.map((i) => (
                <li key={i.key} className={i.done ? 'done' : ''}>
                  {i.done ? <CheckCircle2 size={18} aria-hidden="true" /> : <Circle size={18} aria-hidden="true" />}
                  <span>{i.label}</span>
                  {!i.done && i.key === 'photo' && canChangePhoto && (
                    <button type="button" className="secondary" onClick={() => fileInput.current?.click()}>Add photo</button>
                  )}
                </li>
              ))}
            </ul>
            {isSelf && !c.complete && (missing('phone') || missing('address') || missing('emergencyContact')) && (
              <form className="completion-form" onSubmit={saveContact}>
                {missing('phone') && <label>Phone number<input value={contact.phone} maxLength={25} inputMode="tel" onChange={(e) => setContact({ ...contact, phone: e.target.value })} /></label>}
                {missing('address') && <label>Home address<input value={contact.address} maxLength={500} onChange={(e) => setContact({ ...contact, address: e.target.value })} /></label>}
                {missing('emergencyContact') && <label>Emergency contact (name and number)<input value={contact.emergencyContact} maxLength={150} onChange={(e) => setContact({ ...contact, emergencyContact: e.target.value })} /></label>}
                <button type="submit" disabled={savingContact || !(contact.phone.trim() || contact.address.trim() || contact.emergencyContact.trim())}>{savingContact ? 'Saving…' : 'Save details'}</button>
              </form>
            )}
          </SectionCard>
        )
      })()}

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

      {editing && <PhotoEditorModal file={editing} busy={uploading} onCancel={() => setEditing(null)} onApply={savePhoto} />}
      {viewUrl && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label="Profile photo" onMouseDown={(e) => e.target === e.currentTarget && setViewUrl(null)}>
          <div className="modal photo-view">
            <button type="button" className="modal-x" aria-label="Close" onClick={() => setViewUrl(null)}><X size={20} aria-hidden="true" /></button>
            <img src={viewUrl} alt={current.fullName} />
          </div>
        </div>
      )}
      {confirmRemove && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <h2>Remove photo?</h2>
            <p>The photo will be deleted and your initials shown instead.</p>
            <div className="actions">
              <button type="button" className="danger" onClick={removePhoto} disabled={uploading}>{uploading ? 'Removing…' : 'Yes, remove'}</button>
              <button type="button" onClick={() => setConfirmRemove(false)} disabled={uploading}>Cancel</button>
            </div>
          </div>
        </div>
      )}
      {viewing && <StoredPdfViewer document={viewing} onClose={() => setViewing(null)} />}
      <Flash message={notice?.text ?? null} kind={notice?.kind} onDone={() => setNotice(null)} />
    </main>
  )
}
