import ExpiringDocumentsWidget from '../documents/ExpiringDocumentsWidget'
import { useEffect, useMemo, useState } from 'react'
import { listDepartments } from '../../api/organization'
import { downloadReportCsv, getAttendanceTrend, getHeadcount, getLeaveUtilization, getOnboarding } from '../../api/reports'
import { errorMessage } from '../../api/axiosInstance'
import { ChartCard, ChartLegend, ChartTable } from '../../components/charts/ChartParts'
import ColumnChart, { type ChartSeries } from '../../components/charts/ColumnChart'
import HorizontalBarChart from '../../components/charts/HorizontalBarChart'
import type { Department } from '../../types/organization'
import type {
  AttendanceTrendReport,
  Granularity,
  HeadcountReport,
  LeaveUtilizationReport,
  OnboardingReport,
  ReportFilters,
  ReportType,
} from '../../types/reports'
import { addDays, formatDateOnly, fromISO, toISO } from '../../utils/dates'

type Preset = 'LAST_7' | 'LAST_30' | 'LAST_90' | 'THIS_MONTH' | 'LAST_MONTH' | 'THIS_YEAR' | 'CUSTOM'

const PRESETS: { id: Preset; label: string }[] = [
  { id: 'LAST_7', label: 'Last 7 days' },
  { id: 'LAST_30', label: 'Last 30 days' },
  { id: 'LAST_90', label: 'Last 90 days' },
  { id: 'THIS_MONTH', label: 'This month' },
  { id: 'LAST_MONTH', label: 'Last month' },
  { id: 'THIS_YEAR', label: 'This year' },
  { id: 'CUSTOM', label: 'Custom range' },
]

function rangeFor(preset: Exclude<Preset, 'CUSTOM'>, now = new Date()): { from: string; to: string } {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  switch (preset) {
    case 'LAST_7': return { from: toISO(addDays(today, -6)), to: toISO(today) }
    case 'LAST_30': return { from: toISO(addDays(today, -29)), to: toISO(today) }
    case 'LAST_90': return { from: toISO(addDays(today, -89)), to: toISO(today) }
    case 'THIS_MONTH': return { from: toISO(new Date(today.getFullYear(), today.getMonth(), 1)), to: toISO(today) }
    case 'LAST_MONTH': return {
      from: toISO(new Date(today.getFullYear(), today.getMonth() - 1, 1)),
      to: toISO(new Date(today.getFullYear(), today.getMonth(), 0)),
    }
    case 'THIS_YEAR': return { from: toISO(new Date(today.getFullYear(), 0, 1)), to: toISO(today) }
  }
}

/** Day columns for about a month, week columns for a quarter, month columns beyond that. */
function autoGranularity(from: string, to: string): Granularity {
  const days = Math.round((fromISO(to).getTime() - fromISO(from).getTime()) / 86_400_000) + 1
  return days <= 31 ? 'DAILY' : days <= 120 ? 'WEEKLY' : 'MONTHLY'
}

const GRAIN_NAME: Record<Granularity, string> = { DAILY: 'day', WEEKLY: 'week', MONTHLY: 'month' }
const shortDate = (iso: string) => fromISO(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })

// categorical slots, in their fixed order (colour follows the entity, not its rank)
const SERIES_1 = 'var(--series-1)'
const ATTENDANCE_SERIES: ChartSeries[] = [
  { key: 'present', label: 'Present', color: 'var(--series-1)' },
  { key: 'late', label: 'Late', color: 'var(--series-2)' },
  { key: 'halfDay', label: 'Half day', color: 'var(--series-3)' },
  { key: 'absent', label: 'Absent', color: 'var(--series-4)' },
  { key: 'onLeave', label: 'On leave', color: 'var(--series-5)' },
]

const csvName = (type: ReportType, f: ReportFilters) =>
  type === 'headcount' ? `${type}_${toISO(new Date())}.csv` : `${type}_${f.from}_to_${f.to}.csv`

export default function HrDashboardPage() {
  const [preset, setPreset] = useState<Preset>('LAST_30')
  const [range, setRange] = useState(() => rangeFor('LAST_30'))
  const [departmentId, setDepartmentId] = useState('')
  const [grouping, setGrouping] = useState<'AUTO' | Granularity>('AUTO')
  const [departments, setDepartments] = useState<Department[]>([])

  const [headcount, setHeadcount] = useState<HeadcountReport | null>(null)
  const [attendance, setAttendance] = useState<AttendanceTrendReport | null>(null)
  const [leave, setLeave] = useState<LeaveUtilizationReport | null>(null)
  const [onboarding, setOnboarding] = useState<OnboardingReport | null>(null)
  const [errors, setErrors] = useState<Record<string, string | null>>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => setDepartments([]))
  }, [])

  const rangeValid = !!range.from && !!range.to && range.to >= range.from
  const granularity: Granularity = grouping === 'AUTO' ? autoGranularity(range.from, range.to) : grouping
  const filters: ReportFilters = useMemo(
    () => ({ from: range.from, to: range.to, departmentId: departmentId ? Number(departmentId) : undefined, granularity }),
    [range.from, range.to, departmentId, granularity],
  )

  // One slice drives every widget: change a filter and all four reports are fetched again together.
  useEffect(() => {
    if (!rangeValid) return
    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true)
    Promise.allSettled([
      getHeadcount(filters.departmentId),
      getAttendanceTrend(filters),
      getLeaveUtilization(filters),
      getOnboarding(filters),
    ]).then(([h, a, l, o]) => {
      if (cancelled) return
      // a failed widget keeps its previous drawing and shows its own error; the others carry on
      const next: Record<string, string | null> = {}
      const apply = <T,>(r: PromiseSettledResult<T>, key: string, set: (v: T) => void) => {
        if (r.status === 'fulfilled') {
          set(r.value)
          next[key] = null
        } else {
          next[key] = errorMessage(r.reason, 'Could not load this report')
        }
      }
      apply(h, 'headcount', setHeadcount)
      apply(a, 'attendance', setAttendance)
      apply(l, 'leave', setLeave)
      apply(o, 'onboarding', setOnboarding)
      setErrors(next)
      setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [filters, rangeValid])

  function choosePreset(next: Preset) {
    setPreset(next)
    if (next !== 'CUSTOM') setRange(rangeFor(next))
  }

  function editDate(which: 'from' | 'to', value: string) {
    setPreset('CUSTOM')
    setRange((r) => ({ ...r, [which]: value }))
  }

  const exporter = (type: ReportType) => () => downloadReportCsv(type, filters, csvName(type, filters))
  const departmentName = departments.find((d) => String(d.id) === departmentId)?.name

  return (
    <main className="page viz-root dashboard">
      <h1>HR dashboard</h1>

      {/* the one filter row: it scopes every widget below */}
      <div className="viz-filters" role="group" aria-label="Dashboard filters">
        <label>Date range
          <select value={preset} onChange={(e) => choosePreset(e.target.value as Preset)}>
            {PRESETS.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
          </select>
        </label>
        <label>From
          <input type="date" value={range.from} max={range.to || undefined} onChange={(e) => editDate('from', e.target.value)} />
        </label>
        <label>To
          <input type="date" value={range.to} min={range.from || undefined} onChange={(e) => editDate('to', e.target.value)} />
        </label>
        <label>Department
          <select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
            <option value="">All departments</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </label>
        <label>Attendance by
          <select value={grouping} onChange={(e) => setGrouping(e.target.value as 'AUTO' | Granularity)}>
            <option value="AUTO">Auto ({GRAIN_NAME[autoGranularity(range.from, range.to)]})</option>
            <option value="DAILY">Day</option>
            <option value="WEEKLY">Week</option>
            <option value="MONTHLY">Month</option>
          </select>
        </label>
      </div>
      {!rangeValid && <p className="error">Choose a start date that is on or before the end date.</p>}

      <div className="viz-layout">
        {/* ------------------------------------------------------------ headcount */}
        <div className="viz-wide">
          <ChartCard
            title="Headcount"
            subtitle={<>Today&apos;s workforce{departmentName ? ` in ${departmentName}` : ''}. Current employees only; the date range does not apply.</>}
            loading={loading}
            error={errors.headcount}
            onExport={exporter('headcount')}
            chart={headcount && (
              <>
                <div className="viz-kpis">
                  <div className="hero">
                    <span className="hero-number">{headcount.total}</span>
                    <span className="hero-label">employees</span>
                  </div>
                  {headcount.byStatus.map((s) => (
                    <div className="stat" key={s.status}>
                      <span className="stat-number">{s.count}</span>
                      <span className="stat-label">{s.status.replace('_', ' ').toLowerCase()}</span>
                    </div>
                  ))}
                </div>
                <h3 className="viz-subhead">By department</h3>
                <HorizontalBarChart
                  items={[...headcount.byDepartment].sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
                    .map((d) => ({ key: String(d.id ?? 'none'), label: d.name, value: d.count }))}
                  color={SERIES_1}
                  unit="employees"
                  ariaLabel={`Headcount by department, ${headcount.total} employees in total`}
                />
              </>
            )}
            table={headcount && (
              <ChartTable
                caption="Headcount"
                headers={['Section', 'Name', 'Count']}
                rows={[
                  ['Total', 'All employees', headcount.total],
                  ...headcount.byDepartment.map((d): (string | number)[] => ['Department', d.name, d.count]),
                  ...headcount.byStatus.map((s): (string | number)[] => ['Status', s.status, s.count]),
                ]}
              />
            )}
          />
        </div>

        {/* ------------------------------------------------------ attendance trend */}
        <div className="viz-wide">
          <ChartCard
            title="Attendance trend"
            subtitle={<>
              {attendance ? `${attendance.employees} employees · ` : ''}{formatDateOnly(range.from)} – {formatDateOnly(range.to)} · by {GRAIN_NAME[granularity]}.
              Counts are employee-days: absent means a working day with no check-in and no approved leave; weekends and holidays are not counted, and today counts once someone has checked in.
            </>}
            loading={loading}
            error={errors.attendance}
            onExport={exporter('attendance-trend')}
            chart={attendance && (
              <>
                <ChartLegend items={ATTENDANCE_SERIES.map((s) => ({
                  label: s.label,
                  color: s.color,
                  value: attendance.totals[s.key as keyof typeof attendance.totals] as number,
                }))} />
                <ColumnChart
                  categories={attendance.points.map((p) => ({
                    label: granularity === 'MONTHLY'
                      ? fromISO(p.periodStart).toLocaleDateString(undefined, { month: 'short', year: 'numeric' })
                      : shortDate(p.periodStart),
                    title: granularity === 'DAILY'
                      ? fromISO(p.periodStart).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
                      : granularity === 'WEEKLY'
                        ? `${shortDate(p.periodStart)} – ${shortDate(p.periodEnd)}`
                        : fromISO(p.periodStart).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }),
                  }))}
                  series={ATTENDANCE_SERIES}
                  values={ATTENDANCE_SERIES.map((s) => attendance.points.map((p) => p[s.key as keyof typeof p] as number))}
                  ariaLabel={`Attendance by ${GRAIN_NAME[granularity]}, stacked present, late, half day, absent and on leave`}
                  emptyText="No attendance to show for this period."
                />
              </>
            )}
            table={attendance && (
              <ChartTable
                caption="Attendance trend (employee-days)"
                headers={['Period start', 'Period end', 'Present', 'Late', 'Half day', 'Absent', 'On leave']}
                rows={[
                  ...attendance.points.map((p): (string | number)[] => [p.periodStart, p.periodEnd, p.present, p.late, p.halfDay, p.absent, p.onLeave]),
                  ['Total', '', attendance.totals.present, attendance.totals.late, attendance.totals.halfDay, attendance.totals.absent, attendance.totals.onLeave],
                ]}
              />
            )}
          />
        </div>

        {/* ---------------------------------------------------- leave utilisation */}
        <div className="viz-wide">
          <ChartCard
            title="Leave utilisation"
            subtitle={<>
              {leave ? <><strong>{leave.totalDays}</strong> working days across {leave.totalRequests} approved request{leave.totalRequests === 1 ? '' : 's'} · </> : ''}
              {formatDateOnly(range.from)} – {formatDateOnly(range.to)}. Only the part of a request inside the range counts; weekends and public holidays do not.
            </>}
            loading={loading}
            error={errors.leave}
            onExport={exporter('leave-utilization')}
            chart={leave && (
              <div className="viz-two-col">
                <div>
                  <h3 className="viz-subhead">By leave type</h3>
                  <HorizontalBarChart
                    items={leave.byType.filter((t) => t.days > 0).sort((a, b) => b.days - a.days)
                      .map((t) => ({ key: String(t.id), label: t.name, value: t.days, detail: `${t.requests} request${t.requests === 1 ? '' : 's'}` }))}
                    color={SERIES_1}
                    unit="days"
                    ariaLabel="Leave days used by leave type"
                    emptyText="No approved leave in this period."
                  />
                </div>
                <div>
                  <h3 className="viz-subhead">By department</h3>
                  <HorizontalBarChart
                    items={leave.byDepartment.filter((d) => d.days > 0).sort((a, b) => b.days - a.days)
                      .map((d) => ({ key: String(d.id ?? 'none'), label: d.name, value: d.days, detail: `${d.requests} request${d.requests === 1 ? '' : 's'}` }))}
                    color={SERIES_1}
                    unit="days"
                    ariaLabel="Leave days used by department"
                    emptyText="No approved leave in this period."
                  />
                </div>
              </div>
            )}
            table={leave && (
              <ChartTable
                caption="Leave utilisation (working days)"
                headers={['Breakdown', 'Name', 'Leave days', 'Requests']}
                rows={[
                  ['Total', 'All approved leave', leave.totalDays, leave.totalRequests],
                  ...leave.byType.map((t): (string | number)[] => ['Leave type', t.name, t.days, t.requests]),
                  ...leave.byDepartment.map((d): (string | number)[] => ['Department', d.name, d.days, d.requests]),
                ]}
              />
            )}
          />
        </div>

        {/* ---------------------------------------------------------- onboarding */}
        <div className="viz-wide">
          <ChartCard
            title="New hires"
            subtitle={<>
              {onboarding ? <><strong>{onboarding.totalHires}</strong> hired · {onboarding.totalStillEmployed} still employed · </> : ''}
              by joining month, {formatDateOnly(range.from)} – {formatDateOnly(range.to)}. Includes people who have since left.
            </>}
            loading={loading}
            error={errors.onboarding}
            onExport={exporter('onboarding-stats')}
            chart={onboarding && (
              <ColumnChart
                categories={onboarding.months.map((m) => ({
                  label: fromISO(m.monthStart).toLocaleDateString(undefined, { month: 'short', year: 'numeric' }),
                  title: fromISO(m.monthStart).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }),
                }))}
                series={[{ key: 'hires', label: 'New hires', color: SERIES_1 }]}
                values={[onboarding.months.map((m) => m.hires)]}
                capLabels
                note={(i) => `${onboarding.months[i].stillEmployed} still employed`}
                ariaLabel="New hires per month"
                emptyText="No hires in this period."
              />
            )}
            table={onboarding && (
              <ChartTable
                caption="New hires by joining month"
                headers={['Month', 'New hires', 'Still employed']}
                rows={[
                  ...onboarding.months.map((m): (string | number)[] => [m.monthStart.slice(0, 7), m.hires, m.stillEmployed]),
                  ['Total', onboarding.totalHires, onboarding.totalStillEmployed],
                ]}
              />
            )}
          />
        </div>
      </div>
      <ExpiringDocumentsWidget />
    </main>
  )
}
