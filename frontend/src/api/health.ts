import { api } from './axiosInstance'

export async function getHealth(): Promise<{ status: string }> {
  const { data } = await api.get<{ status: string }>('/api/v1/health')
  return data
}
