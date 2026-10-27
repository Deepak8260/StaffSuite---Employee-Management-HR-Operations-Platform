// Date-only helpers. Dates travel as "YYYY-MM-DD" strings and are always parsed in local time,
// because new Date("2026-03-02") is UTC midnight and shows as the previous day west of UTC.

const pad = (n: number) => String(n).padStart(2, '0')

export function toISO(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function fromISO(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function isWeekend(d: Date): boolean {
  const day = d.getDay()
  return day === 0 || day === 6
}

export function addDays(d: Date, n: number): Date {
  const copy = new Date(d)
  copy.setDate(copy.getDate() + n)
  return copy
}

/** Working days in [startISO, endISO]: Monday-Friday minus the given (mandatory) holiday dates. */
export function countWorkingDays(startISO: string, endISO: string, holidayDates: Set<string>): number {
  let days = 0
  const end = fromISO(endISO)
  for (let d = fromISO(startISO); d <= end; d = addDays(d, 1)) {
    if (!isWeekend(d) && !holidayDates.has(toISO(d))) days++
  }
  return days
}

/** Weeks (Monday first) covering a month; days outside the month are null. */
export function monthMatrix(year: number, month: number): (Date | null)[][] {
  const first = new Date(year, month, 1)
  const offset = (first.getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: (Date | null)[] = Array(offset).fill(null)
  for (let d = 1; d <= daysInMonth; d++) cells.push(new Date(year, month, d))
  while (cells.length % 7 !== 0) cells.push(null)
  const weeks: (Date | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  return weeks
}

export function formatDateOnly(iso: string | null | undefined): string {
  return iso ? fromISO(iso.slice(0, 10)).toLocaleDateString() : '—'
}

export function monthLabel(year: number, month: number): string {
  return new Date(year, month, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}
