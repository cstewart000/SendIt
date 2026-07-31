export type JobSummary = {
  partCount: number
  sheetCount: number
  partsAreaMm2: number
  cycleMinutes: number | null
  cost: number | null
  currency: string | null
}

export type JobSummaryView = {
  id: number
  title?: string
  status: string
  hasGcode: boolean
  summary?: JobSummary
}
