import type { AppConfig } from '../types/config'

// No environment-specific default is baked into the bundle: an empty base URL means "same origin".
// The real value comes from /config.js at runtime.
const DEFAULTS: AppConfig = {
  API_BASE_URL: '',
}

export function getConfig(): AppConfig {
  return { ...DEFAULTS, ...window.__APP_CONFIG__ }
}
