export function toErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null) {
    const responseData = Reflect.get(error, 'response')
    if (typeof responseData === 'object' && responseData !== null) {
      const data = Reflect.get(responseData, 'data')
      if (typeof data === 'object' && data !== null) {
        const code = Reflect.get(data, 'code')
        const msg = Reflect.get(data, 'msg')
        if (typeof msg === 'string' && msg.trim()) {
          if (
            code === 'INTERNAL_SERVER_ERROR' &&
            msg.trim().toLowerCase() === 'internal server error'
          ) {
            return fallback
          }
          return msg
        }
      }
    }
  }

  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
