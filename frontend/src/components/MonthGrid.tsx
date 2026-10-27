import type { ReactNode } from 'react'
import type { Holiday } from '../types/leave'
import { isWeekend, monthLabel, monthMatrix, toISO } from '../utils/dates'

interface Props {
  year: number
  month: number
  holidays: Map<string, Holiday>
  onPrev: () => void
  onNext: () => void
  /** Selecting mode: clicking a working day calls this. Weekends and holidays are not clickable. */
  onPick?: (iso: string) => void
  selection?: { start: string; end: string } | null
  /** Extra content rendered inside each day cell (used by the leave calendar). */
  renderDay?: (iso: string) => ReactNode
  tall?: boolean
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export default function MonthGrid({ year, month, holidays, onPrev, onNext, onPick, selection, renderDay, tall }: Props) {
  const todayISO = toISO(new Date())
  return (
    <div className="cal">
      <div className="cal-head">
        <button type="button" onClick={onPrev} aria-label="Previous month">‹</button>
        <strong>{monthLabel(year, month)}</strong>
        <button type="button" onClick={onNext} aria-label="Next month">›</button>
      </div>
      <div className="cal-grid">
        {WEEKDAYS.map((w) => (
          <div key={w} className="cal-dow">{w}</div>
        ))}
        {monthMatrix(year, month).flat().map((d, i) => {
          if (!d) return <div key={i} className="cal-day empty" />
          const iso = toISO(d)
          const holiday = holidays.get(iso)
          const mandatoryHoliday = holiday && !holiday.optional
          const weekend = isWeekend(d)
          const selectable = !!onPick && !weekend && !mandatoryHoliday
          const inRange = selection && iso >= selection.start && iso <= selection.end
          const classes = [
            'cal-day',
            tall ? 'tall' : '',
            weekend ? 'weekend' : '',
            mandatoryHoliday ? 'holiday' : '',
            holiday?.optional ? 'optional' : '',
            inRange ? 'range' : '',
            iso === todayISO ? 'today' : '',
            selectable ? 'selectable' : '',
          ].join(' ')
          const content = (
            <>
              <span className="cal-num">{d.getDate()}</span>
              {holiday && <span className="cal-holiday" title={holiday.name}>{holiday.name}</span>}
              {renderDay?.(iso)}
            </>
          )
          return selectable ? (
            <button type="button" key={i} className={classes} onClick={() => onPick(iso)}>{content}</button>
          ) : (
            <div key={i} className={classes}>{content}</div>
          )
        })}
      </div>
    </div>
  )
}
