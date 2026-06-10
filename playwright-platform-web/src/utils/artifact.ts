export function formatArtifactType(type: string): string {
  if (type === 'REPORT_FILE') {
    return '报告文件'
  }
  return type
}

export function formatFileSize(size?: number | null): string {
  if (size == null || Number.isNaN(size)) {
    return '-'
  }

  if (size < 1024) {
    return `${size} B`
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }

  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

export function getArtifactFileName(objectKey: string): string {
  if (!objectKey) {
    return '-'
  }

  const segments = objectKey.split('/').filter(Boolean)
  return segments.length > 0 ? segments[segments.length - 1] : '-'
}
