import { describe, expect, it } from 'vitest'
import { formatArtifactType, formatFileSize, getArtifactFileName } from './artifact'

describe('artifact utils', () => {
  it('formats artifact type labels', () => {
    expect(formatArtifactType('REPORT_FILE')).toBe('报告文件')
    expect(formatArtifactType('TRACE')).toBe('TRACE')
  })

  it('formats file sizes', () => {
    expect(formatFileSize(null)).toBe('-')
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(1048576)).toBe('1.0 MB')
  })

  it('extracts file names from object keys', () => {
    expect(getArtifactFileName('runs/2/artifacts/data/trace.zip')).toBe('trace.zip')
    expect(getArtifactFileName('index.html')).toBe('index.html')
    expect(getArtifactFileName('')).toBe('-')
  })
})
