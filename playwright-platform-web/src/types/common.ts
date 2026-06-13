export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}
