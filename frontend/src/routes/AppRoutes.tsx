import { useAuth } from '../hooks/useAuth'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import Layout from '../components/Layout'
import ProtectedRoute from '../components/ProtectedRoute'
import ChangePasswordPage from '../features/auth/ChangePasswordPage'
import ForgotPasswordPage from '../features/auth/ForgotPasswordPage'
import LoginPage from '../features/auth/LoginPage'
import ResetPasswordPage from '../features/auth/ResetPasswordPage'
import AttendanceReportsPage from '../features/attendance/AttendanceReportsPage'
import EmployeeAttendanceDetailPage from '../features/attendance/EmployeeAttendanceDetailPage'
import MyAttendancePage from '../features/attendance/MyAttendancePage'
import TeamAttendancePage from '../features/attendance/TeamAttendancePage'
import DocumentCategoriesPage from '../features/documents/DocumentCategoriesPage'
import EmployeeDocumentsPage from '../features/documents/EmployeeDocumentsPage'
import ExpiringDocumentsPage from '../features/documents/ExpiringDocumentsPage'
import MyDocumentsPage from '../features/documents/MyDocumentsPage'
import EmployeeDetailPage from '../features/employees/EmployeeDetailPage'
import EmployeeFormPage from '../features/employees/EmployeeFormPage'
import EmployeeListPage from '../features/employees/EmployeeListPage'
import EmployeeProfilePage from '../features/employees/EmployeeProfilePage'
import HomePage from '../features/home/HomePage'
import NotificationsPage from '../features/notifications/NotificationsPage'
import HrDashboardPage from '../features/reports/HrDashboardPage'
import HolidayCalendarPage from '../features/leave/HolidayCalendarPage'
import HolidaysPage from '../features/leave/HolidaysPage'
import LeaveApprovalsPage from '../features/leave/LeaveApprovalsPage'
import LeaveCalendarPage from '../features/leave/LeaveCalendarPage'
import LeaveTypesPage from '../features/leave/LeaveTypesPage'
import MyLeavePage from '../features/leave/MyLeavePage'
import DepartmentsPage from '../features/organization/DepartmentsPage'
import DesignationsPage from '../features/organization/DesignationsPage'
import OrgChartPage from '../features/organization/OrgChartPage'
import UserManagementPage from '../features/users/UserManagementPage'

/** "My profile" is the same unified profile page everyone else sees, with the completion bar and photo options. */
function MyProfileRedirect() {
  const { user } = useAuth()
  return <Navigate to={`/employees/${user?.employeeId}/profile`} replace />
}

function DirectoryRedirect() {
  const { search } = useLocation()
  return <Navigate to={`/employees${search}`} replace />
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<HomePage />} />
        <Route path="/change-password" element={<ChangePasswordPage />} />
        {/* the old standalone Directory page now lives in Employees; keep old links and bookmarks working */}
        <Route path="/directory" element={<DirectoryRedirect />} />
        <Route
          path="/my-profile"
          element={
            <ProtectedRoute requireEmployee>
              <MyProfileRedirect />
            </ProtectedRoute>
          }
        />
        <Route path="/employees/:id" element={<EmployeeDetailPage />} />
        <Route path="/employees/:id/profile" element={<EmployeeProfilePage />} />
        <Route path="/org-chart" element={<OrgChartPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route
          path="/admin/dashboard"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <HrDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/documents"
          element={
            <ProtectedRoute requireEmployee>
              <MyDocumentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/documents"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <EmployeeDocumentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/documents/expiring"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <ExpiringDocumentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/document-categories"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <DocumentCategoriesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/attendance"
          element={
            <ProtectedRoute requireEmployee>
              <MyAttendancePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/attendance/team"
          element={
            <ProtectedRoute roles={['MANAGER']}>
              <TeamAttendancePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/attendance/reports/employee/:id"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <EmployeeAttendanceDetailPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/attendance/reports"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <AttendanceReportsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/leave"
          element={
            <ProtectedRoute requireEmployee>
              <MyLeavePage />
            </ProtectedRoute>
          }
        />
        <Route path="/leave/calendar" element={<LeaveCalendarPage />} />
        <Route path="/holidays" element={<HolidayCalendarPage />} />
        <Route
          path="/leave/team"
          element={
            <ProtectedRoute roles={['MANAGER']}>
              <LeaveApprovalsPage scope="TEAM" />
            </ProtectedRoute>
          }
        />
        <Route
          path="/leave/all"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <LeaveApprovalsPage scope="ALL" />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/leave-types"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <LeaveTypesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/holidays"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <HolidaysPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/departments"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <DepartmentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/designations"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <DesignationsPage />
            </ProtectedRoute>
          }
        />

        <Route path="/employees" element={<EmployeeListPage />} />
        <Route
          path="/employees/new"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <EmployeeFormPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/employees/:id/edit"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN']}>
              <EmployeeFormPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/users"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN']}>
              <UserManagementPage />
            </ProtectedRoute>
          }
        />
      </Route>
    </Routes>
  )
}
