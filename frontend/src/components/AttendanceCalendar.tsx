import type { AttendanceDay, AttendanceStatus } from '../types/attendance'
import { monthLabel, monthMatrix, toISO } from '../utils/dates'
import { timeOf } from '../utils/time'

interface Props {
  year: number
  /** 0-11 */
  month: number
  days: AttendanceDay[]
  selected?: string | null
  onSelect?: (day: AttendanceDay) => void
  onPrev: () => void
  onNext: () => void
  nextDisabled?: boolean
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const LABELS: Record<AttendanceStatus, string> = {
  PRESENT: 'Present',
  LATE: 'Late',
  HALF_DAY: 'Half day',
  ABSENT: 'Absent',
  ON_LEAVE: 'On leave',
}

/** Month grid colour-coded by attendance status, with check-in/out times in each day. */
export default function AttendanceCalendar({ year, month, days, selected, onSelect, onPrev, onNext, nextDisabled }: Props) {
  const todayISO = toISO(new Date())
  const byDate = new Map(days.map((d) => [d.date, d]))

  return (
    <div className="cal att-cal">
      <div className="cal-head">
        <button type="button" onClick={onPrev} aria-label="Previous month">‹</button>
        <strong>{monthLabel(year, month)}</strong>
        <button type="button" onClick={onNext} disabled={nextDisabled} aria-label="Next month">›</button>
      </div>
      <div className="cal-grid">
        {WEEKDAYS.map((w) => <div key={w} className="cal-dow">{w}</div>)}
        {monthMatrix(year, month).flat().map((d, i) => {
          if (!d) return <div key={i} className="cal-day empty" />
          const iso = toISO(d)
          const day = byDate.get(iso)
          const future = iso > todayISO
          const status = !future && day ? day.status : null
          const classes = [
            'cal-day', 'tall',
            status ? `att-${status.toLowerCase()}` : '',
            day?.dayType === 'WEEKEND' ? 'weekend' : '',
            day?.dayType === 'HOLIDAY' ? 'holiday' : '',
            iso === todayISO ? 'today' : '',
            selected === iso ? 'chosen' : '',
          ].join(' ')
          const content = (
            <>
              <span className="cal-num">{d.getDate()}</span>
              {status && <span className="att-label">{LABELS[status]}</span>}
              {!future && day?.checkInTime && (
                <span className="att-time">{timeOf(day.checkInTime)} – {timeOf(day.checkOutTime)}</span>
              )}
              {!future && day?.dayType === 'HOLIDAY' && <span className="cal-holiday">{day.holidayName ?? 'Holiday'}</span>}
              {!future && day?.pendingCorrection && <span className="att-time">Correction pending</span>}
            </>
          )
          return onSelect && day && !future && day.dayType !== 'BEFORE_JOINING' ? (
            <button type="button" key={i} className={classes} onClick={() => onSelect(day)}>{content}</button>
          ) : (
            <div key={i} className={classes}>{content}</div>
          )
        })}
      </div>
      <p className="hint legend">
        <span className="key att-present" /> present · <span className="key att-late" /> late ·{' '}
        <span className="key att-half_day" /> half day · <span className="key att-absent" /> absent ·{' '}
        <span className="key att-on_leave" /> on leave · <span className="key holiday" /> holiday ·{' '}
        <span className="key weekend" /> weekend
      </p>
    </div>
  )
}
