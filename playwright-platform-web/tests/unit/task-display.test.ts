import { describe, expect, it } from 'vitest'
import {
  caseFilterLabel,
  caseStatusText,
  caseStatusType,
  formatDuration,
  taskResultCodeText,
  taskStageText,
  taskStatusText,
  taskStatusType,
  taskDurationText,
  taskTriggerTypeText,
} from '../../src/utils/task-display'

describe('task-display', () => {
  it('should merge cancel requested state into running task status text', () => {
    expect(taskStatusText({ status: 'RUNNING', cancelRequested: true })).toBe('取消中')
    expect(taskStatusType({ status: 'RUNNING', cancelRequested: true })).toBe('warning')
  })

  it('should format task enums into Chinese labels', () => {
    expect(taskStatusText({ status: 'QUEUED', cancelRequested: false })).toBe('排队中')
    expect(taskStageText('ARCHIVING')).toBe('归档产物')
    expect(taskResultCodeText('TEST_FAILED')).toBe('测试失败')
    expect(taskResultCodeText('SYSTEM_ABORTED')).toBe('系统中断')
    expect(taskResultCodeText('SYSTEM_BUSY')).toBe('系统繁忙')
    expect(taskTriggerTypeText('SCHEDULED')).toBe('定时触发')
  })

  it('should format case labels into Chinese text', () => {
    expect(caseStatusText('PASSED')).toBe('通过')
    expect(caseStatusType('FAILED')).toBe('danger')
    expect(caseFilterLabel('ALL')).toBe('全部')
  })

  it('should render live duration for active tasks', () => {
    expect(taskDurationText({
      status: 'RUNNING',
      queuedAt: '2026-06-13T10:00:00',
      durationMs: 0,
    }, new Date('2026-06-13T10:00:10').getTime())).toBe('00min10s')

    expect(taskDurationText({
      status: 'QUEUED',
      queuedAt: '2026-06-13T10:00:00',
      durationMs: 3000,
    }, new Date('2026-06-13T10:00:05').getTime())).toBe('00min05s')
  })

  it('should fall back to persisted duration for finished tasks', () => {
    expect(taskDurationText({
      status: 'FAILED',
      durationMs: 65000,
      createdAt: '2026-06-13T10:00:00',
    }, new Date('2026-06-13T10:10:00').getTime())).toBe(formatDuration(65000))
  })
})
