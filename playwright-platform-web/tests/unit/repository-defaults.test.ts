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

const ElRadioGroupStub = defineComponent({
  template: '<div><slot /></div>',
})

const ElRadioButtonStub = defineComponent({
  template: '<button><slot /></button>',
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
    expect(form.authType).toBe('PUBLIC')
    expect(form.credentialSecret).toBe('')
    expect(form.runCommandTemplate).toBe('npx playwright test')
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
          'el-radio-group': ElRadioGroupStub,
          'el-radio-button': ElRadioButtonStub,
        },
      },
    })

    expect(wrapper.text()).toContain('默认 npm install；仓库有 package-lock.json 时建议改成 npm ci')
    expect(wrapper.text()).toContain('公有仓库')
    expect(wrapper.text()).toContain('私有仓库')
    expect(wrapper.find('input[placeholder="npm install"]').exists()).toBe(true)
  })
})
