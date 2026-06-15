export function toErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null) {
    const responseData = Reflect.get(error, 'response')
    if (typeof responseData === 'object' && responseData !== null) {
      const data = Reflect.get(responseData, 'data')
      if (typeof data === 'object' && data !== null) {
        const code = Reflect.get(data, 'code')
        const msg = Reflect.get(data, 'msg')
        const message = Reflect.get(data, 'message')
        const errorText = Reflect.get(data, 'error')
        const resolvedMessage = typeof msg === 'string' && msg.trim()
          ? msg
          : typeof message === 'string' && message.trim()
            ? message
            : typeof errorText === 'string' && errorText.trim()
              ? errorText
              : null
        if (typeof resolvedMessage === 'string' && resolvedMessage.trim()) {
          if (
            code === 'INTERNAL_SERVER_ERROR' &&
            resolvedMessage.trim().toLowerCase() === 'internal server error'
          ) {
            return fallback
          }
          return resolvedMessage
        }
      }
    }
  }

  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
