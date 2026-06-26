import { describe, expect, it, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import RepositoryListView from '../../src/views/repository/RepositoryListView.vue'
import { createRepositoryForm } from '../../src/types/repository'
import { useRepositoryStore } from '../../src/stores/repository'

const ListPageShellStub = defineComponent({
  template: '<div><slot name="header-right" /><slot /></div>',
})

const ElDialogStub = defineComponent({
  template: '<div><slot /><slot name="footer" /></div>',
})

const ElFormStub = defineComponent({
  template: '<form><slot /></form>',
})

const ElFormItemStub = defineComponent({
  template: '<label><slot name="label" /><slot /></label>',
})

const ElInputStub = defineComponent({
  props: {
    modelValue: {
      type: String,
      default: '',
    },
    placeholder: {
      type: String,
      default: '',
    },
  },
  template: '<input :value="modelValue" :placeholder="placeholder" />',
})

const ElButtonStub = defineComponent({
  template: '<button><slot /></button>',
})

const ElTableStub = defineComponent({
  template: '<div><slot /></div>',
})

const ElTableColumnStub = defineComponent({
  template: '<div />',
})

const ElSwitchStub = defineComponent({
  template: '<div />',
})

const ElTooltipStub = defineComponent({
  props: {
    content: {
      type: String,
      default: '',
    },
  },
  template: '<div><span class="tooltip-content">{{ content }}</span><slot /></div>',
})

describe('repository defaults', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const store = useRepositoryStore()
    store.fetchAll = vi.fn().mockResolvedValue(undefined)
  })

  it('should default install command to npm install for new repositories', () => {
    const form = createRepositoryForm()

    expect(form.installCommand).toBe('npm install')
  })

  it('should show npm install as the default install command in repository creation help text and placeholder', () => {
    const wrapper = mount(RepositoryListView, {
      global: {
        plugins: [createPinia()],
        stubs: {
          ListPageShell: ListPageShellStub,
          'el-dialog': ElDialogStub,
          'el-form': ElFormStub,
          'el-form-item': ElFormItemStub,
          'el-input': ElInputStub,
          'el-button': ElButtonStub,
          'el-table': ElTableStub,
          'el-table-column': ElTableColumnStub,
          'el-switch': ElSwitchStub,
          'el-tooltip': ElTooltipStub,
        },
      },
    })

    expect(wrapper.text()).toContain('默认使用 npm install；如仓库封装了脚本，也可以改成自定义安装命令。')
    expect(wrapper.find('input[placeholder="npm install"]').exists()).toBe(true)
  })
})
