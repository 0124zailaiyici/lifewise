import { buildSystemPrompt } from './scenePrompts.js'
import { getAiConfig, getMessages, recordAiAudit } from './db.js'
import { buildSafetyTriage } from './safetyTriage.js'
import { Capacitor, registerPlugin } from '@capacitor/core'

const NativeAiStream = registerPlugin('NativeAiStream')

function normalizeBaseUrl(url) {
  return String(url || '').trim().replace(/\/+$/, '')
}

// 开发服务器代理仅用于浏览器本地调试，避免第三方 AI 服务的 CORS 限制。
function browserRequestUrl(url) {
  if (!import.meta.env.DEV) return url
  const proxyTarget = normalizeBaseUrl(import.meta.env.VITE_AI_DEV_PROXY_TARGET)
  if (!proxyTarget) return url
  try {
    const requestUrl = new URL(url)
    const targetUrl = new URL(proxyTarget)
    if (requestUrl.origin !== targetUrl.origin) return url
    return '/__lifewise_ai' + requestUrl.pathname + requestUrl.search
  } catch {
    return url
  }
}

/** 把用户随便填的地址，规整成 chat/completions、models、images 三个 URL */
export function resolveOpenAIEndpoints(baseUrl) {
  let base = normalizeBaseUrl(baseUrl)
  if (!base) throw new Error('请先配置 AI 接口地址')
  if (!/^https?:\/\//i.test(base)) {
    base = 'https://' + base
  }

  if (/\/chat\/completions\/?$/i.test(base)) {
    const root = base.replace(/\/chat\/completions\/?$/i, '')
    return {
      chatUrl: base.replace(/\/+$/, ''),
      modelsUrl: root + '/models',
      imagesUrl: root + '/images/generations',
      audioUrl: root + '/audio/transcriptions',
      displayBase: root
    }
  }

  if (/\/v1$/i.test(base) || /\/compatible-mode\/v1$/i.test(base)) {
    return {
      chatUrl: base + '/chat/completions',
      modelsUrl: base + '/models',
      imagesUrl: base + '/images/generations',
      audioUrl: base + '/audio/transcriptions',
      displayBase: base
    }
  }

  if (/\/v1\//i.test(base)) {
    const root = base.replace(/\/v1\/.*$/i, '/v1')
    return {
      chatUrl: root + '/chat/completions',
      modelsUrl: root + '/models',
      imagesUrl: root + '/images/generations',
      audioUrl: root + '/audio/transcriptions',
      displayBase: root
    }
  }

  return {
    chatUrl: base + '/v1/chat/completions',
    modelsUrl: base + '/v1/models',
    imagesUrl: base + '/v1/images/generations',
    audioUrl: base + '/v1/audio/transcriptions',
    displayBase: base + '/v1'
  }
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 45000) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(browserRequestUrl(url), { ...options, signal: controller.signal })
  } catch (e) {
    if (e?.name === 'AbortError') throw new Error('请求超时（' + Math.round(timeoutMs / 1000) + '秒），请检查接口地址/网络')
    const msg = String(e?.message || e || '')
    if (/Failed to fetch|NetworkError|Load failed|网络/i.test(msg)) {
      throw new Error('网络请求失败：请检查接口地址、网络、HTTPS 证书或浏览器跨域配置。原错误：' + msg)
    }
    throw new Error(msg || '网络请求失败')
  } finally {
    clearTimeout(timer)
  }
}

function extractErrorMessage(status, data, rawText) {
  return (
    data?.error?.message ||
    data?.error?.code ||
    data?.message ||
    data?.msg ||
    (rawText ? String(rawText).slice(0, 240) : '') ||
    ('HTTP ' + status)
  )
}

function isStreamUnsupported(error) {
  return (error?.status === 400 || error?.status === 422) && /stream|streaming|流式/i.test(String(error?.message || ''))
}

function extractContent(data) {
  const content = data?.choices?.[0]?.message?.content
  if (typeof content === 'string') return content
  if (Array.isArray(content)) {
    return content.map(p => p?.text || p?.content || '').join('')
  }
  if (content && typeof content === 'object') return JSON.stringify(content)
  if (typeof data?.output?.text === 'string') return data.output.text
  if (typeof data?.result === 'string') return data.result
  return ''
}

function toImageDataUrl(value) {
  const raw = String(value || '').trim()
  if (!raw) return ''
  if (raw.startsWith('data:image/')) return raw
  if (/^[A-Za-z0-9+/=\r\n]+$/.test(raw) && raw.replace(/\s/g, '').length > 120) {
    return 'data:image/png;base64,' + raw.replace(/\s/g, '')
  }
  return ''
}

function imageUrlFromText(value, fromImageField = false) {
  const text = String(value || '').trim()
  if (!text) return ''
  const dataUrl = text.match(/data:image\/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\s]+/)
  if (dataUrl) return dataUrl[0].replace(/\s/g, '')

  if (fromImageField) {
    const maybeData = toImageDataUrl(text)
    if (maybeData) return maybeData
  }

  const markdownUrl = text.match(/!\[[^\]]*\]\((https?:\/\/[^)\s]+)\)/i)
  if (markdownUrl) return markdownUrl[1]

  const plainUrl = text.match(/https?:\/\/[^\s)'"<>]+/i)
  if (plainUrl && (fromImageField || /\.(png|jpe?g|webp|gif)(\?|#|$)/i.test(plainUrl[0]) || /image|img|cdn|oss|dashscope|aliyuncs|openai/i.test(plainUrl[0]))) {
    return plainUrl[0]
  }

  return ''
}

function extractImageUrl(data) {
  const seen = new Set()
  const imageKeys = new Set(['url', 'image_url', 'imageUrl', 'output_url', 'asset_url', 'b64_json', 'base64', 'data'])

  function visit(node, key = '') {
    if (node == null) return ''
    if (typeof node === 'string') return imageUrlFromText(node, imageKeys.has(key) || /image|img|url|b64|base64/i.test(key))
    if (typeof node !== 'object') return ''
    if (seen.has(node)) return ''
    seen.add(node)

    if (Array.isArray(node)) {
      for (const item of node) {
        const found = visit(item, key)
        if (found) return found
      }
      return ''
    }

    // OpenAI /images/generations, DashScope compatible, and many relay formats put the image in these keys.
    for (const k of imageKeys) {
      if (Object.prototype.hasOwnProperty.call(node, k)) {
        const found = visit(node[k], k)
        if (found) return found
      }
    }

    // Chat-style image models often return choices[].message.content[] with output_image/image_url parts.
    const priorityKeys = ['data', 'images', 'output', 'result', 'choices', 'message', 'content']
    for (const k of priorityKeys) {
      if (Object.prototype.hasOwnProperty.call(node, k)) {
        const found = visit(node[k], k)
        if (found) return found
      }
    }

    for (const [k, v] of Object.entries(node)) {
      const found = visit(v, k)
      if (found) return found
    }
    return ''
  }

  return visit(data)
}

function streamDeltaFromPayload(data) {
  const choice = data?.choices?.[0]
  const delta = choice?.delta?.content ?? choice?.message?.content
  if (typeof delta === 'string') return delta
  if (Array.isArray(delta)) return delta.map(part => part?.text || part?.content || '').join('')
  if (typeof data?.delta === 'string') return data.delta
  if (typeof data?.text === 'string' && /output_text\.delta|text\.delta/i.test(String(data?.type || ''))) return data.text
  if (typeof data?.output_text === 'string') return data.output_text
  return ''
}

async function readChatResponse(resp, onChunk) {
  const contentType = String(resp.headers.get('content-type') || '').toLowerCase()
  if (!resp.body || typeof resp.body.getReader !== 'function') {
    const rawText = await resp.text()
    let data = null
    try { data = JSON.parse(rawText) } catch {}
    return { content: extractContent(data), rawText, data }
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let rawText = ''
  let content = ''
  let sawStreamEvent = false

  const push = delta => {
    if (!delta) return
    content += delta
    onChunk?.(content, delta)
  }

  const parseLine = line => {
    const trimmed = String(line || '').trim()
    if (!trimmed) return
    const payload = trimmed.startsWith('data:') ? trimmed.slice(5).trim() : trimmed
    if (!payload || payload === '[DONE]') return
    try {
      const data = JSON.parse(payload)
      const delta = streamDeltaFromPayload(data)
      if (delta) {
        sawStreamEvent = true
        push(delta)
      }
    } catch {}
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    const chunk = decoder.decode(value, { stream: true })
    rawText += chunk
    buffer += chunk
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() || ''
    lines.forEach(parseLine)
  }
  const tail = decoder.decode()
  rawText += tail
  buffer += tail
  if (buffer.trim()) parseLine(buffer)

  let data = null
  if (!sawStreamEvent || !content) {
    try { data = JSON.parse(rawText) } catch {}
    const finalContent = extractContent(data)
    if (finalContent) {
      content = finalContent
      onChunk?.(content, content)
    }
  }

  if (!content && contentType.includes('text/event-stream')) {
    const events = rawText.split(/\r?\n/)
    events.forEach(parseLine)
  }

  return { content, rawText, data }
}

async function readNativeChatResponse(chatUrl, apiKey, body, onChunk) {
  const requestId = 'chat-' + Date.now() + '-' + Math.random().toString(36).slice(2)
  let content = ''
  const handles = []
  let timeout = null

  const cleanup = async () => {
    if (timeout) clearTimeout(timeout)
    await Promise.all(handles.map(handle => handle?.remove?.()).filter(Boolean)).catch(() => {})
  }

  return new Promise(async (resolve, reject) => {
    try {
      handles.push(await NativeAiStream.addListener('streamChunk', event => {
        if (event?.requestId !== requestId) return
        try {
          const data = JSON.parse(event.payload || '{}')
          const delta = streamDeltaFromPayload(data)
          if (!delta) return
          content += delta
          onChunk?.(content, delta)
        } catch {}
      }))
      handles.push(await NativeAiStream.addListener('streamComplete', event => {
        if (event?.requestId !== requestId) return
        if (!content && event?.rawText) {
          try {
            const data = JSON.parse(event.rawText)
            content = extractContent(data)
            if (content) onChunk?.(content, content)
          } catch {}
        }
        resolve({ content, rawText: event?.rawText || '' })
        // Do not await removal of the listener from inside its own callback.
        // Capacitor can wait for this callback to return before completing removal.
        cleanup().catch(() => {})
      }))
      handles.push(await NativeAiStream.addListener('streamError', event => {
        if (event?.requestId !== requestId) return
        const error = new Error(event?.message || '原生流式请求失败')
        error.status = Number(event?.status || 0)
        reject(error)
        cleanup().catch(() => {})
      }))

      timeout = setTimeout(async () => {
        await NativeAiStream.cancel({ requestId }).catch(() => {})
        await cleanup()
        reject(new Error('AI 响应超时（120秒），请检查网络或更换模型'))
      }, 120000)

      await NativeAiStream.start({
        requestId,
        url: chatUrl,
        apiKey: String(apiKey || '').trim(),
        body: JSON.stringify(body)
      })
    } catch (e) {
      await cleanup()
      reject(e)
    }
  })
}

async function requestNativeChatResponse(chatUrl, apiKey, body) {
  const requestId = 'chat-' + Date.now() + '-' + Math.random().toString(36).slice(2)
  const result = await NativeAiStream.request({
    requestId,
    url: chatUrl,
    apiKey: String(apiKey || '').trim(),
    body: JSON.stringify(body)
  })
  const rawText = String(result?.rawText || '')
  let data = null
  try { data = JSON.parse(rawText) } catch {}
  return { content: extractContent(data), rawText }
}

export async function callLocalChat({ message, scene, conversationId, imageUrl, followUp = false, configOverride = null, onChunk = null, excludeLatestUser = false }) {
  const safetyReply = buildSafetyTriage(message, scene)
  if (safetyReply) {
    recordAiAudit({ provider: 'safety-rule', model: safetyReply.model, status: 'blocked', detail: (message || '').slice(0, 40) })
    return safetyReply
  }
  const cfg = { ...getAiConfig(), ...(configOverride || {}) }
  if (!cfg.apiKey) throw new Error('请先在「我的」页填写 AI API Key')
  if (!cfg.baseUrl) throw new Error('请先在「我的」页填写 AI 接口地址')

  const rounds = Number(cfg.contextRounds || 5)
  const history = conversationId ? getMessages(conversationId, rounds * 2) : []
  if (excludeLatestUser && history.at(-1)?.role === 'user') history.pop()
  const messages = [
    { role: 'system', content: buildSystemPrompt(scene || 'other', !!followUp) }
  ]

  for (const h of history) {
    if (!h?.content && !h?.imageUrl) continue
    let historyContent = h.content || ''
    if (scene === 'writing' && h.role === 'assistant' && historyContent) {
      try {
        const parsed = JSON.parse(historyContent)
        historyContent = parsed.sample || parsed.answer || parsed.content || parsed.text || ''
      } catch {}
    }
    if (!historyContent && !h?.imageUrl) continue
    if (h.imageUrl && String(h.imageUrl).startsWith('data:')) {
      const parts = []
      if (historyContent) parts.push({ type: 'text', text: historyContent })
      parts.push({ type: 'image_url', image_url: { url: h.imageUrl } })
      messages.push({ role: h.role === 'assistant' ? 'assistant' : 'user', content: parts })
    } else {
      messages.push({
        role: h.role === 'assistant' ? 'assistant' : 'user',
        content: historyContent
      })
    }
  }

  if (imageUrl && String(imageUrl).startsWith('data:')) {
    const parts = []
    if (message) parts.push({ type: 'text', text: message })
    parts.push({ type: 'image_url', image_url: { url: imageUrl } })
    messages.push({ role: 'user', content: parts })
  } else {
    messages.push({ role: 'user', content: message || '' })
  }

  const { chatUrl, displayBase } = resolveOpenAIEndpoints(cfg.baseUrl)
  const body = {
    model: cfg.model || 'qwen-plus',
    messages,
    temperature: 0.7,
    max_tokens: scene === 'writing' ? 8192 : 4096,
    stream: true
  }
  if (scene !== 'writing') body.response_format = { type: 'json_object' }

  recordAiAudit({
    provider: 'openai-compatible',
    model: body.model,
    status: 'calling',
    detail: (message || '').slice(0, 40)
  })

  if (Capacitor.isNativePlatform()) {
    try {
      const result = await readNativeChatResponse(chatUrl, cfg.apiKey, body, onChunk)
      if (!result.content) throw new Error('AI 返回为空，请检查当前模型是否支持流式输出')
      recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'ok', detail: (message || '').slice(0, 40) })
      return {
        content: result.content,
        source: 'openai-compatible',
        externalCall: true,
        sourceLabel: '直连 ' + body.model,
        model: body.model,
        streamed: true,
        requestUrl: chatUrl
      }
    } catch (e) {
      if (!isStreamUnsupported(e)) {
        recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'error', detail: String(e?.message || e) })
        throw e
      }
      const fallbackResult = await requestNativeChatResponse(chatUrl, cfg.apiKey, { ...body, stream: false })
      const fallbackContent = fallbackResult.content
      if (!fallbackContent) throw new Error('AI 返回为空，请检查当前模型是否支持当前接口')
      recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'ok', detail: (message || '').slice(0, 40) })
      return {
        content: fallbackContent,
        source: 'openai-compatible',
        externalCall: true,
        sourceLabel: '直连 ' + body.model,
        model: body.model,
        streamed: false,
        requestUrl: chatUrl
      }
    }
  }

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 120000)
  let resp
  try {
    resp = await fetch(browserRequestUrl(chatUrl), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream, application/json',
        Authorization: 'Bearer ' + String(cfg.apiKey || '').trim()
      },
      body: JSON.stringify(body),
      signal: controller.signal
    })

    if (!resp.ok) {
      const rawText = await resp.text()
      let data = null
      try { data = JSON.parse(rawText) } catch {}
      const errMsg = extractErrorMessage(resp.status, data, rawText)
      const streamUnsupported = (resp.status === 400 || resp.status === 422) && /stream|streaming|流式/i.test(errMsg)
      if (streamUnsupported) {
        resp = await fetch(browserRequestUrl(chatUrl), {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer ' + String(cfg.apiKey || '').trim()
          },
          body: JSON.stringify({ ...body, stream: false }),
          signal: controller.signal
        })
        if (resp.ok) {
          const fallback = await resp.json()
          const fallbackContent = extractContent(fallback)
          if (!fallbackContent) throw new Error('模型不支持流式输出，普通响应也为空')
          onChunk?.(fallbackContent, fallbackContent)
          recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'ok', detail: (message || '').slice(0, 40) })
          return {
            content: fallbackContent,
            source: 'openai-compatible',
            externalCall: true,
            sourceLabel: '直连 ' + body.model,
            model: body.model,
            streamed: false,
            requestUrl: chatUrl
          }
        }
      }
      recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'error', detail: errMsg })
      throw new Error('AI 调用失败（' + resp.status + '）：' + errMsg + '\n请求地址：' + chatUrl + '\n配置根地址：' + displayBase)
    }

    const { content, rawText } = await readChatResponse(resp, onChunk)
    if (!content) {
      recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'error', detail: '空响应' })
      throw new Error('AI 返回为空。请检查模型名是否正确。\n请求地址：' + chatUrl + '\n原始返回：' + rawText.slice(0, 180))
    }

    recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'ok', detail: (message || '').slice(0, 40) })
    return {
      content,
      source: 'openai-compatible',
      externalCall: true,
      sourceLabel: '直连 ' + body.model,
      model: body.model,
      streamed: true,
      requestUrl: chatUrl
    }
  } catch (e) {
    if (e?.name === 'AbortError') throw new Error('AI 响应超时（120秒），请检查网络或更换模型')
    const msg = String(e?.message || e || '')
    if (/Failed to fetch|NetworkError|Load failed|网络/i.test(msg)) {
      throw new Error('网络请求失败：请检查 AI 地址、网络、HTTPS 证书或浏览器跨域配置。原错误：' + msg)
    }
    throw e
  } finally {
    clearTimeout(timer)
  }
}

/** 本地直连 OpenAI 兼容生图；优先 /images/generations，失败后回退到可生图聊天模型 */
async function requestImageEndpoint(imagesUrl, headers, cleanPrompt, model) {
  const bodyVariants = [
    { model, prompt: cleanPrompt, n: 1, size: '1024x1024' },
    { model, prompt: cleanPrompt, n: 1 }
  ]
  let lastErr = ''

  for (const body of bodyVariants) {
    for (let attempt = 1; attempt <= 2; attempt++) {
      const resp = await fetchWithTimeout(imagesUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify(body)
      }, 120000)

      const rawText = await resp.text()
      let data = null
      try { data = JSON.parse(rawText) } catch {}

      if (!resp.ok) {
        const errMsg = extractErrorMessage(resp.status, data, rawText)
        lastErr = '生图接口失败（' + resp.status + '）：' + errMsg
        const retryable = resp.status === 502 || resp.status === 503 || resp.status === 504 || /upstream|temporarily|timeout|过载|繁忙/i.test(errMsg)
        const canTrySimplerBody = body.size && /size|parameter|param|字段|参数|unsupported|invalid/i.test(errMsg)
        if (retryable && attempt < 2) {
          await new Promise(r => setTimeout(r, 800 * attempt))
          continue
        }
        if (canTrySimplerBody) break
        throw new Error(lastErr)
      }

      const imageUrl = extractImageUrl(data)
      if (imageUrl) {
        return { imageUrl, rawText }
      }
      lastErr = '生图接口成功但未返回图片数据：' + rawText.slice(0, 180)
      break
    }
  }

  throw new Error(lastErr || '生图接口未返回图片')
}

async function requestChatImage(chatUrl, headers, cleanPrompt, model) {
  const systemPrompt = 'You are an image generation model. Generate exactly one image for the user prompt. Return the generated image URL or image data only; do not describe the image in text.'
  const bodyVariants = [
    {
      model,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: cleanPrompt }
      ],
      modalities: ['text', 'image'],
      response_modalities: ['text', 'image'],
      temperature: 0.7,
      max_tokens: 1024
    },
    {
      model,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: cleanPrompt }
      ],
      modalities: ['image', 'text'],
      temperature: 0.7,
      max_tokens: 1024
    },
    {
      model,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: cleanPrompt }
      ],
      temperature: 0.7,
      max_tokens: 1024
    }
  ]
  let lastErr = ''

  for (const body of bodyVariants) {
    const resp = await fetchWithTimeout(chatUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify(body)
    }, 120000)

    const rawText = await resp.text()
    let data = null
    try { data = JSON.parse(rawText) } catch {}

    if (!resp.ok) {
      const errMsg = extractErrorMessage(resp.status, data, rawText)
      lastErr = '聊天生图失败（' + resp.status + '）：' + errMsg
      if (/modalit|response_modalit|parameter|param|字段|参数|unsupported|invalid/i.test(errMsg)) continue
      throw new Error(lastErr)
    }

    const imageUrl = extractImageUrl(data)
    if (imageUrl) return { imageUrl, rawText }
    lastErr = '聊天模型已响应但未返回图片数据：' + rawText.slice(0, 180)
  }

  throw new Error(lastErr || '聊天生图未返回图片')
}

export async function generateLocalImage(prompt, configOverride = null) {
  const cfg = { ...getAiConfig(), ...(configOverride || {}) }
  if (!cfg.apiKey) throw new Error('请先在「我的」页填写 AI API Key')
  if (!cfg.baseUrl) throw new Error('请先在「我的」页填写 AI 接口地址')
  const cleanPrompt = String(prompt || '').trim()
  if (!cleanPrompt) throw new Error('生图提示词为空')

  const { imagesUrl, chatUrl, displayBase } = resolveOpenAIEndpoints(cfg.baseUrl)
  const imageModel = String(cfg.imageModel || '').trim()
  const chatModel = String(cfg.model || '').trim()
  const models = [...new Set([imageModel, chatModel, 'qwen-plus'].filter(Boolean))]
  const headers = {
    'Content-Type': 'application/json',
    Authorization: 'Bearer ' + String(cfg.apiKey || '').trim()
  }

  recordAiAudit({
    provider: 'openai-compatible-image',
    model: models[0] || '',
    status: 'calling',
    detail: cleanPrompt.slice(0, 40)
  })

  const errors = []
  for (const model of models) {
    try {
      const r = await requestImageEndpoint(imagesUrl, headers, cleanPrompt, model)
      recordAiAudit({ provider: 'openai-compatible-image', model, status: 'ok', detail: cleanPrompt.slice(0, 40) })
      return {
        imageUrl: r.imageUrl,
        model,
        source: 'openai-compatible-image',
        mode: 'images',
        requestUrl: imagesUrl
      }
    } catch (e) {
      errors.push('[' + model + ' /images] ' + (e?.message || String(e)))
    }
  }

  for (const model of models) {
    try {
      const r = await requestChatImage(chatUrl, headers, cleanPrompt, model)
      recordAiAudit({ provider: 'openai-compatible-image', model, status: 'ok', detail: cleanPrompt.slice(0, 40) })
      return {
        imageUrl: r.imageUrl,
        model,
        source: 'openai-compatible-chat-image',
        mode: 'chat',
        requestUrl: chatUrl
      }
    } catch (e) {
      errors.push('[' + model + ' chat] ' + (e?.message || String(e)))
    }
  }

  const detail = errors.slice(-4).join('\n')
  recordAiAudit({ provider: 'openai-compatible-image', model: models[0] || '', status: 'error', detail: detail.slice(0, 160) })
  throw new Error('生图失败：当前接口没有返回可显示的图片。\n配置根地址：' + displayBase + '\n' + detail)
}

/**
 * 测试 AI 配置：
 * 1) 尝试拉 /models
 * 2) 发一条最小 chat 测试
 * 3) 验证修理场景所需的 JSON 输出
 * 4) 仅检查生图接口路径/模型配置（不强制真生图，省费用）
 */
export async function testAiConnection(configOverride = null) {
  const cfg = { ...getAiConfig(), ...(configOverride || {}) }
  if (!cfg.apiKey) throw new Error('请先填写 API Key')
  if (!cfg.baseUrl) throw new Error('请先填写接口地址')

  const { chatUrl, modelsUrl, imagesUrl, displayBase } = resolveOpenAIEndpoints(cfg.baseUrl)
  const nativePlatform = Capacitor.isNativePlatform()
  const headers = {
    'Content-Type': 'application/json',
    Authorization: 'Bearer ' + String(cfg.apiKey || '').trim()
  }

  const result = {
    ok: false,
    displayBase,
    chatUrl,
    modelsUrl,
    imagesUrl,
    models: [],
    model: cfg.model || 'qwen-plus',
    imageModel: cfg.imageModel || cfg.model || 'qwen-plus',
    reply: '',
    steps: []
  }

  if (nativePlatform) {
    result.steps.push({ name: '拉取模型列表', ok: true, detail: 'Android 使用原生网络直连；跳过可能受网页跨域限制的模型列表检测' })
  } else {
    try {
      const resp = await fetchWithTimeout(modelsUrl, { method: 'GET', headers }, 20000)
      const raw = await resp.text()
      let data = null
      try { data = JSON.parse(raw) } catch {}
      if (resp.ok) {
        const list = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : [])
        result.models = list
          .map(m => m?.id || m?.name || m?.model || '')
          .filter(Boolean)
          .slice(0, 80)
        result.steps.push({ name: '拉取模型列表', ok: true, detail: '成功，共 ' + result.models.length + ' 个' })
        if (result.models.length && cfg.model && !result.models.includes(cfg.model)) {
          result.steps.push({
            name: '模型名检查',
            ok: false,
            detail: '当前模型「' + cfg.model + '」不在列表中。可点选下方模型，或确认服务商模型名'
          })
        } else if (cfg.model) {
          result.steps.push({ name: '模型名检查', ok: true, detail: '当前模型「' + cfg.model + '」' })
        }
      } else {
        result.steps.push({
          name: '拉取模型列表',
          ok: false,
          detail: 'HTTP ' + resp.status + '：' + extractErrorMessage(resp.status, data, raw) + '（可忽略，继续测聊天）'
        })
      }
    } catch (e) {
      result.steps.push({ name: '拉取模型列表', ok: false, detail: (e.message || String(e)) + '（可忽略，继续测聊天）' })
    }
  }

  try {
    const body = {
      model: cfg.model || 'qwen-plus',
      messages: [
        { role: 'system', content: '你是连通性测试助手。只回复：OK' },
        { role: 'user', content: 'ping' }
      ],
      temperature: 0,
      max_tokens: 16,
      stream: nativePlatform
    }
    let content = ''
    if (nativePlatform) {
      let nativeResult
      try {
        nativeResult = await readNativeChatResponse(chatUrl, cfg.apiKey, body)
      } catch (error) {
        if (!isStreamUnsupported(error)) throw error
        nativeResult = await readNativeChatResponse(chatUrl, cfg.apiKey, { ...body, stream: false })
      }
      content = nativeResult.content || ''
      if (!content) throw new Error('原生聊天测试未收到有效回复，请确认模型支持当前接口')
    } else {
      const resp = await fetchWithTimeout(chatUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify(body)
      }, 45000)
      const raw = await resp.text()
      let data = null
      try { data = JSON.parse(raw) } catch {}
      if (!resp.ok) {
        const err = extractErrorMessage(resp.status, data, raw)
        result.steps.push({ name: '聊天测试', ok: false, detail: 'HTTP ' + resp.status + '：' + err })
        recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'error', detail: 'test: ' + err })
        throw new Error('聊天测试失败（' + resp.status + '）：' + err)
      }
      content = extractContent(data) || raw.slice(0, 80)
    }
    result.reply = content
    result.ok = true
    result.steps.push({ name: '聊天测试', ok: true, detail: '成功，回复：' + String(content).slice(0, 60) })
    recordAiAudit({ provider: 'openai-compatible', model: body.model, status: 'ok', detail: 'test ping' })
  } catch (e) {
    if (!result.steps.some(s => s.name === '聊天测试')) {
      result.steps.push({ name: '聊天测试', ok: false, detail: e.message || String(e) })
    }
    recordAiAudit({ provider: 'openai-compatible', model: cfg.model || '', status: 'error', detail: 'test: ' + (e.message || '') })
    const err = new Error(e.message || '测试失败')
    err.testResult = result
    throw err
  }

  try {
    const repairBody = {
      model: cfg.model || 'qwen-plus',
      messages: [
        { role: 'system', content: '只输出合法 JSON 对象：{"status":"ok"}' },
        { role: 'user', content: 'ping' }
      ],
      temperature: 0,
      max_tokens: 32,
      stream: false,
      response_format: { type: 'json_object' }
    }
    let repairContent = ''
    if (nativePlatform) {
      const nativeResult = await readNativeChatResponse(chatUrl, cfg.apiKey, repairBody)
      repairContent = nativeResult.content || ''
    } else {
      const resp = await fetchWithTimeout(chatUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify(repairBody)
      }, 45000)
      const raw = await resp.text()
      let data = null
      try { data = JSON.parse(raw) } catch {}
      if (!resp.ok) throw new Error('HTTP ' + resp.status + '：' + extractErrorMessage(resp.status, data, raw))
      repairContent = extractContent(data)
    }
    const repairData = JSON.parse(repairContent)
    if (!repairData || typeof repairData !== 'object' || Array.isArray(repairData)) throw new Error('模型没有返回 JSON 对象')
    result.steps.push({ name: '修理 JSON 测试', ok: true, detail: '成功，模型支持结构化修理回答' })
  } catch (e) {
    result.ok = false
    result.steps.push({ name: '修理 JSON 测试', ok: false, detail: e.message || String(e) })
    const err = new Error('修理 JSON 测试失败：' + (e.message || '模型不支持 JSON 输出'))
    err.testResult = result
    throw err
  }

  result.steps.push({
    name: '生图接口',
    ok: true,
    detail: '已配置 images：' + imagesUrl + '；生图模型「' + (cfg.imageModel || cfg.model || 'qwen-plus') + '」（生图模型留空时使用聊天模型；本测试不实际生图）'
  })

  return result
}
