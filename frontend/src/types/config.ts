export interface AppConfig {
  API_BASE_URL: string
}

declare global {
  interface Window {
    __APP_CONFIG__?: Partial<AppConfig>
  }
}
