import type {
  AttendanceTrendReport,
  HeadcountReport,
  LeaveUtilizationReport,
  OnboardingReport,
  ReportFilters,
  ReportType,
} from '../types/reports'
import { api } from './axiosInstance'

const BASE = '/api/v1/reports'

export async function getHeadcount(departmentId?: number): Promise<HeadcountReport> {
  const { data } = await api.get<HeadcountReport>(`${BASE}/headcount`, { params: { departmentId } })
  return data
}

export async function getAttendanceTrend(f: ReportFilters): Promise<AttendanceTrendReport> {
  const { data } = await api.get<AttendanceTrendReport>(`${BASE}/attendance-trend`, { params: f })
  return data
}

export async function getLeaveUtilization(f: ReportFilters): Promise<LeaveUtilizationReport> {
  const { data } = await api.get<LeaveUtilizationReport>(`${BASE}/leave-utilization`, {
    params: { from: f.from, to: f.to, departmentId: f.departmentId },
  })
  return data
}

export async function getOnboarding(f: ReportFilters): Promise<OnboardingReport> {
  const { data } = await api.get<OnboardingReport>(`${BASE}/onboarding-stats`, {
    params: { from: f.from, to: f.to, departmentId: f.departmentId },
  })
  return data
}

/** Turns a failed blob response back into the server's JSON error message. */
async function blobError(err: unknown): Promise<Error> {
  const data = (err as { response?: { data?: unknown } })?.response?.data
  if (data instanceof Blob) {
    try {
      const body = JSON.parse(await data.text()) as { message?: string }
      if (body.message) return new Error(body.message)
    } catch {
      /* fall through */
    }
  }
  return err instanceof Error ? err : new Error('Could not export the report')
}

function fileNameFrom(disposition: string | undefined, fallback: string): string {
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
  return match ? decodeURIComponent(match[1]) : fallback
}

/**
 * Downloads the CSV for a report using exactly the filters the dashboard is showing. Fetched through
 * axios so the sign-in token is sent, then saved under the name the server suggests.
 */
export async function downloadReportCsv(type: ReportType, f: ReportFilters, fallbackName: string): Promise<void> {
  try {
    // headcount is a snapshot of today, so only the department applies to it
    const params = type === 'headcount' ? { departmentId: f.departmentId } : f
    const res = await api.get<Blob>(`${BASE}/${type}/export`, { params, responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([res.data], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = fileNameFrom(res.headers['content-disposition'] as string | undefined, fallbackName)
    a.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    throw await blobError(err)
  }
}
