import { describe, expect, it } from 'vitest'
import router from './index'

describe('platform router', () => {
  it('should expose the core platform routes', () => {
    const paths = router.getRoutes().map((route) => route.path)
    const rootRoute = router.getRoutes().find((route) => route.path === '/')

    expect(paths).toEqual(expect.arrayContaining([
      '/',
      '/repos',
      '/scenes',
      '/tasks',
      '/tasks/:id',
    ]))
    expect(rootRoute?.redirect).toBe('/repos')
  })
})
