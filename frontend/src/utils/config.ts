import type { AppConfig } from '../types/config'

const DEFAULTS: AppConfig = {
  API_BASE_URL: 'http://localhost:8080',
}

export function getConfig(): AppConfig {
  return { ...DEFAULTS, ...window.__APP_CONFIG__ }
}
