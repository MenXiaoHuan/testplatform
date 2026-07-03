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
        if (typeof code === 'string') {
            if (code === 'BAD_REQUEST' && typeof msg === 'string' && msg.trim().toLowerCase() === 'invalid encrypted credentials') {
              return '登录密钥已刷新，请重试一次'
            }
          if (code === 'USERNAME_ALREADY_EXISTS') {
            return '该用户名已被使用，请换一个'
          }
          if (code === 'NICKNAME_ALREADY_EXISTS') {
            return '该昵称已被使用，请换一个'
          }
          if (code === 'SPACE_NAME_ALREADY_EXISTS') {
            return '系统为你生成个人空间时发现名称冲突，请修改昵称后重试'
          }
          if (code === 'INVALID_PASSWORD') {
            return '密码至少 8 位，且需包含字母和数字'
          }
          if (code === 'ACCESS_REQUEST_LIST_FAILED') {
            return '审批列表加载失败，请刷新后重试'
          }
        }
      }
    }
  }

  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}

export function isPendingSpaceAccessRequestError(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) {
    return false
  }
  const response = Reflect.get(error, 'response')
  if (typeof response !== 'object' || response === null) {
    return false
  }
  const data = Reflect.get(response, 'data')
  if (typeof data !== 'object' || data === null) {
    return false
  }

  const code = Reflect.get(data, 'code')
  const msg = Reflect.get(data, 'msg')
  return code === 'CONFLICT' && typeof msg === 'string' && msg.trim().toLowerCase() === 'request already pending'
}
