import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  addFavorite: vi.fn(),
  addKnowledge: vi.fn(),
  generateFoodImage: vi.fn(),
  getAiConfigStatus: vi.fn(),
  getConversation: vi.fn(),
  getFoodImageStatus: vi.fn(),
  loadLocalAiConfig: vi.fn(),
  lookupFoodImage: vi.fn(),
  previewAiEndpoints: vi.fn(),
  removeFavorite: vi.fn(),
  renameConversation: vi.fn(),
  saveFoodImageCache: vi.fn(),
  sendChat: vi.fn(),
  updateFavoriteCategory: vi.fn(),
  uploadImage: vi.fn()
}))

vi.mock('../src/api', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {} }),
  useRouter: () => ({ resolve: ({ path }) => ({ href: '#' + path }), back: vi.fn(), push: vi.fn() })
}))
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => false },
  registerPlugin: () => ({})
}))
vi.mock('@capacitor/filesystem', () => ({ Filesystem: {}, Directory: {} }))
vi.mock('@capacitor/share', () => ({ Share: {} }))

import Chat from '../src/views/Chat.vue'

const mountedWrappers = []

const cookingReply = JSON.stringify({
  title: '番茄炒蛋',
  ingredients: [{ name: '鸡蛋', amount: '2 个' }],
  steps: [{ content: '打散鸡蛋后炒熟', time: '2 分钟' }]
})

function mountChat() {
  const wrapper = mount(Chat, {
    global: {
      stubs: {
        'el-button': { template: '<button><slot /></button>' },
        'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' }
      }
    }
  })
  mountedWrappers.push(wrapper)
  return wrapper
}

describe('Chat', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    HTMLDivElement.prototype.scrollTo = vi.fn()
    api.getAiConfigStatus.mockResolvedValue({ data: { model: 'test-model' } })
    api.sendChat.mockReset()
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach(wrapper => wrapper.unmount())
    vi.clearAllMocks()
  })

  it('sends a composer message and renders a structured assistant card', async () => {
    api.sendChat.mockResolvedValue({ data: { id: 22, conversationId: 7, content: cookingReply } })
    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('textarea').setValue('番茄炒蛋怎么做？')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()

    expect(api.sendChat).toHaveBeenCalledWith(
      '番茄炒蛋怎么做？', 'other', null, '', expect.objectContaining({ persistUser: true })
    )
    expect(wrapper.find('.user-bubble').text()).toContain('番茄炒蛋怎么做？')
    expect(wrapper.find('.s-card').text()).toContain('番茄炒蛋')
    expect(wrapper.text()).not.toContain(cookingReply)
  })

  it('keeps the failed assistant card in place and retries without duplicating the user message', async () => {
    api.sendChat.mockRejectedValueOnce(new Error('网络暂不可用')).mockResolvedValueOnce({
      data: { id: 23, conversationId: 7, content: '连接已恢复。' }
    })
    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('textarea').setValue('帮我检查一下')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('.request-retry').text()).toContain('网络暂不可用')

    await wrapper.get('.request-retry button').trigger('click')
    await flushPromises()
    expect(api.sendChat).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('.user-bubble')).toHaveLength(1)
    expect(wrapper.find('.assistant-card').text()).toContain('连接已恢复。')
  })

  it('locks a selected guided choice before dispatching the request', async () => {
    api.sendChat.mockResolvedValue({ data: { id: 24, conversationId: 7, content: '已收到。' } })
    const wrapper = mountChat()
    await flushPromises()

    const card = document.createElement('div')
    card.className = 's-card'
    const choice = document.createElement('button')
    choice.className = 'repair-choice is-selected'
    card.appendChild(choice)
    document.body.appendChild(card)

    await wrapper.find('textarea').setValue('插座没电')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()

    expect(choice.disabled).toBe(true)
    expect(choice.classList.contains('is-locked')).toBe(true)
    card.remove()
  })
})
