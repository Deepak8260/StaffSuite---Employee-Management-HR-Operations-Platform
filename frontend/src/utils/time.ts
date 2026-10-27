/** "2026-03-11T09:10:00" -> "09:10" (the server sends wall-clock times, so no timezone conversion). */
export function timeOf(dateTime: string | null | undefined): string {
  return dateTime ? dateTime.slice(11, 16) : '—'
}

export function hoursText(hours: number | null | undefined): string {
  return hours === null || hours === undefined ? '—' : `${hours.toFixed(2)} h`
}

export function monthRange(month: string): { from: string; to: string } {
  const [y, m] = month.split('-').map(Number)
  const pad = (n: number) => String(n).padStart(2, '0')
  const last = new Date(y, m, 0).getDate()
  return { from: `${y}-${pad(m)}-01`, to: `${y}-${pad(m)}-${pad(last)}` }
}
