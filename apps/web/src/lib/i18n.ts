export type UiLocale = 'zh-CN' | 'en-US'

export function normalizeUiLocale(value?: string | null): UiLocale {
  return value?.trim().toLowerCase().startsWith('en') ? 'en-US' : 'zh-CN'
}

export function currentUiLocale(): UiLocale {
  if (typeof document !== 'undefined') {
    const pageLanguage = document.documentElement.lang
    if (pageLanguage) return normalizeUiLocale(pageLanguage)
  }
  return 'zh-CN'
}
