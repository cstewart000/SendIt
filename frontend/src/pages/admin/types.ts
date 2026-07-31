export type Machine = {
  id?: number
  name: string
  workXmm: number
  workYmm: number
  workZmm: number
  postProcessor: string
  defaultFeedMmMin: number
  defaultSpeedRpm: number
  hourlyRate: number
}

export type Tool = {
  id?: number
  machineId: number
  name: string
  type: string
  diameterMm: number
  fluteCount: number
  maxDepthMm: number
  wearCharge: number
}

export type Material = {
  id?: number
  name: string
  thicknessMm?: number
  sheetWidthMm?: number
  sheetHeightMm?: number
  costPerSheet?: number
}

export type ProcessDef = {
  id?: number
  name: string
  machineId: number
  materialId: number
  strategy: string
  surcharge: number
}

export type PricingRule = {
  id?: number
  machineId: number
  name: string
  ruleKey: string
  value: number
  description?: string
}

export const emptyMachine = (): Machine => ({
  name: '', workXmm: 2440, workYmm: 1220, workZmm: 100,
  postProcessor: 'LinuxCNC', defaultFeedMmMin: 3000, defaultSpeedRpm: 18000, hourlyRate: 60,
})

export const emptyTool = (machineId: number): Tool => ({
  machineId, name: '', type: 'ENDMILL', diameterMm: 6, fluteCount: 2, maxDepthMm: 20, wearCharge: 2.5,
})

export const emptyOp = (machineId: number, materialId = 0): ProcessDef => ({
  machineId, materialId, name: '', strategy: 'PROFILE_2_5D', surcharge: 0,
})

export const emptyRule = (machineId: number): PricingRule => ({
  machineId, name: 'Setup fee', ruleKey: 'SETUP_FEE', value: 25, description: 'Setup fee',
})
