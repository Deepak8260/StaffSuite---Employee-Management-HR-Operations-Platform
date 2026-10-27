import type { Employee } from '../types/employee'
import type {
  Department,
  DepartmentInput,
  Designation,
  DesignationInput,
  OrgNode,
  ReassignInput,
} from '../types/organization'
import { api } from './axiosInstance'

export async function listDepartments(includeInactive = false): Promise<Department[]> {
  const { data } = await api.get<Department[]>('/api/v1/departments', { params: { includeInactive } })
  return data
}

export async function createDepartment(input: DepartmentInput): Promise<Department> {
  const { data } = await api.post<Department>('/api/v1/departments', input)
  return data
}

export async function updateDepartment(id: number, input: DepartmentInput): Promise<Department> {
  const { data } = await api.put<Department>(`/api/v1/departments/${id}`, input)
  return data
}

export async function deleteDepartment(id: number): Promise<void> {
  await api.delete(`/api/v1/departments/${id}`)
}

export async function listDesignations(includeInactive = false): Promise<Designation[]> {
  const { data } = await api.get<Designation[]>('/api/v1/designations', { params: { includeInactive } })
  return data
}

export async function createDesignation(input: DesignationInput): Promise<Designation> {
  const { data } = await api.post<Designation>('/api/v1/designations', input)
  return data
}

export async function updateDesignation(id: number, input: DesignationInput): Promise<Designation> {
  const { data } = await api.put<Designation>(`/api/v1/designations/${id}`, input)
  return data
}

export async function getOrgChart(): Promise<OrgNode[]> {
  const { data } = await api.get<OrgNode[]>('/api/v1/org-chart')
  return data
}

export async function reassignEmployee(id: number, input: ReassignInput): Promise<Employee> {
  const { data } = await api.put<Employee>(`/api/v1/employees/${id}/reassign`, input)
  return data
}
