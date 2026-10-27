import type { AttendanceStatus } from '../types/attendance'

const LABELS: Record<AttendanceStatus, string> = {
  PRESENT: 'Present',
  ABSENT: 'Absent',
  HALF_DAY: 'Half day',
  LATE: 'Late',
  ON_LEAVE: 'On leave',
}

export default function AttendanceBadge({ status }: { status: AttendanceStatus | null }) {
  if (!status) return <span className="hint">—</span>
  return <span className={`badge ${status.toLowerCase()}`}>{LABELS[status]}</span>
}
