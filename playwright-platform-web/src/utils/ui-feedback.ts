import { ElMessage, ElMessageBox } from 'element-plus'

type AppToastType = 'success' | 'warning' | 'error' | 'info'

type ConfirmDangerOptions = {
  title: string
  message: string
  confirmButtonText?: string
}

export function showAppToast(message: string, type: AppToastType = 'success') {
  ElMessage({
    type,
    message,
    customClass: `app-toast app-toast--${type}`,
    showClose: false,
    duration: 3000,
    offset: 28,
  })
}

export async function confirmDangerAction(options: ConfirmDangerOptions) {
  try {
    await ElMessageBox.confirm(options.message, options.title, {
      type: 'warning',
      customClass: 'app-confirm-dialog',
      confirmButtonText: options.confirmButtonText ?? '确认',
      cancelButtonText: '取消',
      confirmButtonClass: 'app-confirm-dialog__confirm',
      cancelButtonClass: 'app-confirm-dialog__cancel',
      showClose: false,
      center: true,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      distinguishCancelAndClose: true,
    })
    return true
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return false
    }
    throw error
  }
}
