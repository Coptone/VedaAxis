const XIVAPI_ASSET_ENDPOINT = 'https://v2.xivapi.com/api/asset'

/**
 * ClassJob icons live in the 062000 sheet and use the ClassJob row id.
 * Example: SGE row 40 -> ui/icon/062000/062040.tex.
 */
export function jobIconUrl(jobId: number | null | undefined): string | null {
  if (!jobId || !Number.isFinite(jobId)) return null
  const iconNumber = `062${Math.trunc(jobId).toString().padStart(3, '0')}`
  const path = `ui/icon/062000/${iconNumber}.tex`
  return `${XIVAPI_ASSET_ENDPOINT}?path=${encodeURIComponent(path)}&format=png`
}
