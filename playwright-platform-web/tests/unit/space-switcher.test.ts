import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import SpaceSwitcher from '../../src/components/layout/SpaceSwitcher.vue'
import { useSpaceStore } from '../../src/stores/space'

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

const ElButtonStub = defineComponent({
  template: '<button><slot /></button>',
})

const ElSelectStub = defineComponent({
  template: '<div class="el-select-stub"><slot /></div>',
})

const ElOptionStub = defineComponent({
  props: {
    label: {
      type: String,
      required: true,
    },
  },
  template: '<div class="el-option-stub">{{ label }}</div>',
})

describe('SpaceSwitcher', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should show the most recently used space first', () => {
    const store = useSpaceStore()
    store.items = [
      { id: 7, name: 'Alpha', description: 'alpha' },
      { id: 8, name: 'Beta', description: 'beta' },
    ]
    store.currentSpaceId = 8

    const wrapper = mount(SpaceSwitcher, {
      global: {
        stubs: {
          'el-button': ElButtonStub,
          'el-select': ElSelectStub,
          'el-option': ElOptionStub,
        },
      },
    })

    const options = wrapper.findAll('.el-option-stub')
    expect(options).toHaveLength(2)
    expect(options[0].text()).toBe('Beta')
    expect(options[1].text()).toBe('Alpha')
  })
})
