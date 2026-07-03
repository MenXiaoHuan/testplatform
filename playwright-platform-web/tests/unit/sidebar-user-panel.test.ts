import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import SidebarUserPanel from '../../src/components/layout/SidebarUserPanel.vue'

const ElDropdownStub = defineComponent({
  template: '<div><slot /><slot name="dropdown" /></div>',
})

const ElDropdownMenuStub = defineComponent({
  template: '<div><slot /></div>',
})

const ElDropdownItemStub = defineComponent({
  template: '<button><slot /></button>',
})

describe('SidebarUserPanel', () => {
  it('should fallback to username and expose avatar action', async () => {
    const wrapper = mount(SidebarUserPanel, {
      props: {
        user: {
          id: 1,
          username: 'admin',
          nickname: '',
          avatarUrl: null,
          lastSpaceId: 7,
        },
      },
      global: {
        stubs: {
          'el-dropdown': ElDropdownStub,
          'el-dropdown-menu': ElDropdownMenuStub,
          'el-dropdown-item': ElDropdownItemStub,
        },
      },
    })

    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('修改昵称')
    expect(wrapper.find('img').attributes('src')).toBeTruthy()
    await wrapper.find('.sidebar-user-panel__avatar-button').trigger('click')
    expect(wrapper.emitted('command')?.[0]).toEqual(['edit-avatar'])
  })

  it('should recover from fallback avatar after user uploads a new avatar', async () => {
    const wrapper = mount(SidebarUserPanel, {
      props: {
        user: {
          id: 1,
          username: 'admin',
          nickname: '平台管理员',
          avatarUrl: 'http://localhost/expired-avatar.png',
          lastSpaceId: 7,
        },
      },
      global: {
        stubs: {
          'el-dropdown': ElDropdownStub,
          'el-dropdown-menu': ElDropdownMenuStub,
          'el-dropdown-item': ElDropdownItemStub,
        },
      },
    })

    await wrapper.find('img').trigger('error')
    const fallbackSrc = wrapper.find('img').attributes('src')

    await wrapper.setProps({
      user: {
        id: 1,
        username: 'admin',
        nickname: '平台管理员',
        avatarUrl: 'http://localhost/new-avatar.png',
        lastSpaceId: 7,
      },
    })

    expect(wrapper.find('img').attributes('src')).not.toBe(fallbackSrc)
    expect(wrapper.find('img').attributes('src')).toContain('new-avatar.png')
  })
})
