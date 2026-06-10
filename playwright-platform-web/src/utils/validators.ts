export function isRequired(value: string): boolean {
  return value.trim().length > 0
}

export function isPositiveId(value: number): boolean {
  return value > 0
}
