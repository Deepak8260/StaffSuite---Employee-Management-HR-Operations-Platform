import { Route, Routes } from 'react-router-dom'
import Layout from '../components/Layout'
import ProtectedRoute from '../components/ProtectedRoute'
import ChangePasswordPage from '../features/auth/ChangePasswordPage'
import ForgotPasswordPage from '../features/auth/ForgotPasswordPage'
import LoginPage from '../features/auth/LoginPage'
import ResetPasswordPage from '../features/auth/ResetPasswordPage'
import DirectoryPage from '../features/employees/DirectoryPage'
import EmployeeDetailPage from '../features/employees/EmployeeDetailPage'
import EmployeeFormPage from '../features/employees/EmployeeFormPage'
import EmployeeListPage from '../features/employees/EmployeeListPage'
import MyProfilePage from '../features/employees/MyProfilePage'
import HomePage from '../features/home/HomePage'
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
        <Route path="/directory" element={<DirectoryPage />} />
        <Route path="/my-profile" element={<MyProfilePage />} />
        <Route path="/employees/:id" element={<EmployeeDetailPage />} />
        <Route path="/org-chart" element={<OrgChartPage />} />
        <Route path="/leave" element={<MyLeavePage />} />
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

        <Route
          path="/employees"
          element={
            <ProtectedRoute roles={['SYSTEM_ADMIN', 'HR_ADMIN', 'MANAGER']}>
              <EmployeeListPage />
            </ProtectedRoute>
          }
        />
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
