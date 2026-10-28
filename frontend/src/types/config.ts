export interface AppConfig {
  API_BASE_URL: string
  /** Optional label for the banner chip (e.g. "Production"). Empty hides the chip. */
  APP_ENV_LABEL: string
}

declare global {
  interface Window {
    __APP_CONFIG__?: Partial<AppConfig>
  }
}
