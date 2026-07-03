import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import LoginView from '../../src/views/auth/LoginView.vue'
import { useAuthStore } from '../../src/stores/auth'

const { pushMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
  useRoute: () => ({
    query: {},
  }),
}))

const ElFormStub = defineComponent({
  template: '<form><slot /></form>',
})

const ElFormItemStub = defineComponent({
  template: '<div><slot /></div>',
})

const ElInputStub = defineComponent({
  props: {
    modelValue: {
      type: String,
      default: '',
    },
    type: {
      type: String,
      default: 'text',
    },
    placeholder: {
      type: String,
      default: '',
    },
  },
  emits: ['update:modelValue'],
  template: `
    <input
      :type="type"
      :placeholder="placeholder"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  `,
})

const ElButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

describe('LoginView', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    pushMock.mockReset()
    const store = useAuthStore()
    store.login = vi.fn().mockResolvedValue({
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: 7,
    })
    store.register = vi.fn().mockResolvedValue({
      id: 2,
      username: 'zhangsan',
      nickname: '张三',
      avatarUrl: null,
      lastSpaceId: 12,
    })
  })

  it('should submit username and password through auth store then jump to /home', async () => {
    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: {
          'el-form': ElFormStub,
          'el-form-item': ElFormItemStub,
          'el-input': ElInputStub,
          'el-button': ElButtonStub,
        },
      },
    })

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('admin')
    await inputs[1].setValue('secret')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    const store = useAuthStore()
    expect(store.login).toHaveBeenCalledWith({
      username: 'admin',
      password: 'secret',
    })
    expect(pushMock).toHaveBeenCalledWith('/home')
  })

  it('should render login page copy with product highlights', () => {
    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: {
          'el-form': ElFormStub,
          'el-form-item': ElFormItemStub,
          'el-input': ElInputStub,
          'el-button': ElButtonStub,
        },
      },
    })

    expect(wrapper.text()).toContain('测试平台')
    expect(wrapper.text()).toContain('产品亮点')
    expect(wrapper.text()).toContain('空间化协作')
    expect(wrapper.text()).toContain('任务与调度联动')
    expect(wrapper.text()).toContain('安全登录与留痕')
    expect(wrapper.text()).toContain('账号登录')
  })

  it('should switch to register mode and jump to personal space after registration', async () => {
    const wrapper = mount(LoginView, {
      global: {
        plugins: [pinia],
        stubs: {
          'el-form': ElFormStub,
          'el-form-item': ElFormItemStub,
          'el-input': ElInputStub,
          'el-button': ElButtonStub,
        },
      },
    })

    const buttons = wrapper.findAll('button')
    await buttons[1].trigger('click')

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('zhangsan')
    await inputs[1].setValue('张三')
    await inputs[2].setValue('secret123')
    await inputs[3].setValue('secret123')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    const store = useAuthStore()
    expect(store.register).toHaveBeenCalledWith({
      username: 'zhangsan',
      nickname: '张三',
      password: 'secret123',
      confirmPassword: 'secret123',
    })
    expect(pushMock).toHaveBeenCalledWith('/spaces/12/repos')
  })
})
