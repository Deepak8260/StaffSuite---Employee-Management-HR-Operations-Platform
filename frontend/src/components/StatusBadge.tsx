import type { LeaveStatus } from '../types/leave'

export default function StatusBadge({ status }: { status: LeaveStatus }) {
  return <span className={`badge ${status.toLowerCase()}`}>{status}</span>
}
