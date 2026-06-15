import { beforeEach, describe, expect, it, vi } from 'vitest'

const { messageMock, messageBoxConfirmMock } = vi.hoisted(() => ({
  messageMock: vi.fn(),
  messageBoxConfirmMock: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: messageMock,
  ElMessageBox: {
    confirm: messageBoxConfirmMock,
  },
}))

import { confirmDangerAction, showAppToast } from '../../src/utils/ui-feedback'

describe('ui-feedback', () => {
  beforeEach(() => {
    messageMock.mockReset()
    messageBoxConfirmMock.mockReset()
  })

  it('should show app toast with unified options', () => {
    showAppToast('任务已触发', 'success')

    expect(messageMock).toHaveBeenCalledWith({
      type: 'success',
      message: '任务已触发',
      customClass: 'app-toast app-toast--success',
      showClose: false,
      duration: 3000,
      offset: 28,
    })
  })

  it('should open unified danger confirm dialog', async () => {
    messageBoxConfirmMock.mockResolvedValue(undefined)

    await expect(confirmDangerAction({
      title: '删除场景',
      message: '确认删除场景“回归测试”吗？',
      confirmButtonText: '删除',
    })).resolves.toBe(true)

    expect(messageBoxConfirmMock).toHaveBeenCalledWith('确认删除场景“回归测试”吗？', '删除场景', {
      type: 'warning',
      customClass: 'app-confirm-dialog',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'app-confirm-dialog__confirm',
      cancelButtonClass: 'app-confirm-dialog__cancel',
      showClose: false,
      center: true,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      distinguishCancelAndClose: true,
    })
  })

  it('should return false when danger confirm dialog is canceled', async () => {
    messageBoxConfirmMock.mockRejectedValue('cancel')

    await expect(confirmDangerAction({
      title: '删除仓库',
      message: '确认删除仓库“平台仓库”吗？',
      confirmButtonText: '删除',
    })).resolves.toBe(false)
  })
})
