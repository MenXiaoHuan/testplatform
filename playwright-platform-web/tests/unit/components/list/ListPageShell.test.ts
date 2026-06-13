import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ListPageShell from '../../../../src/components/list/ListPageShell.vue'

describe('ListPageShell', () => {
  it('renders left and right header slots in dedicated containers', () => {
    const wrapper = mount(ListPageShell, {
      global: {
        stubs: {
          'el-pagination': {
            template: '<div class="pagination-stub" />',
          },
        },
      },
      slots: {
        'header-left': '<button class="left-action">返回场景中心</button>',
        'header-right': '<button class="right-action">执行任务</button>',
        default: '<div class="body-slot">table</div>',
      },
    })

    expect(wrapper.find('.content-panel__header').exists()).toBe(true)
    expect(wrapper.find('.content-panel__header-side--left').exists()).toBe(true)
    expect(wrapper.find('.content-panel__header-side--right').exists()).toBe(true)
    expect(wrapper.find('.content-panel__header-side--left').text()).toContain('返回场景中心')
    expect(wrapper.find('.content-panel__header-side--right').text()).toContain('执行任务')
    expect(wrapper.find('.content-panel__body').text()).toContain('table')
  })

  it('renders a shared pagination footer when pagination props are provided', async () => {
    const wrapper = mount(ListPageShell, {
      props: {
        pagination: {
          page: 2,
          size: 10,
          total: 42,
        },
      },
      global: {
        stubs: {
          'el-pagination': {
            emits: ['current-change', 'size-change'],
            template: '<div class="pagination-stub" @click="$emit(\'current-change\', 3)" />',
          },
        },
      },
    })

    expect(wrapper.find('.content-panel__footer').exists()).toBe(true)
    await wrapper.find('.pagination-stub').trigger('click')
    expect(wrapper.emitted('page-change')?.[0]).toEqual([3])
  })
})
