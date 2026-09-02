import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const chatSource = await readFile(new URL('../src/views/Chat.vue', import.meta.url), 'utf8')
const acceptanceSource = await readFile(new URL('../scripts/acceptance-ai.mjs', import.meta.url), 'utf8')
const apiSource = await readFile(new URL('../src/api/index.js', import.meta.url), 'utf8')
const aiClientSource = await readFile(new URL('../src/local/aiClient.js', import.meta.url), 'utf8')

test('failed chat requests retain an in-place retry path', () => {
  assert.match(chatSource, /async function send\(retryMessage = null\) \{\n\s*if \(loading\.value\) return/)
  assert.match(chatSource, /async function send\(retryMessage = null\)/)
  assert.match(chatSource, /m\._retry = \{ message: msg, scene, conversationId: convId, followUp: isFollowUp, imageUrl/)
  assert.match(chatSource, /function retryFailedMessage\(message\)/)
  assert.match(chatSource, /@click="retryFailedMessage\(msg\)"/)
  assert.match(chatSource, /function unlockGuidedCards\(\)/)
  assert.match(chatSource, /finally \{\n\s*flushStreamUpdate\(\)\n\s*loading\.value = false\n\s*currentTyping\.value = false/)
  assert.match(chatSource, /onConversationCreated\(id\) \{\n\s*currentConvId\.value = id/)
  assert.match(chatSource, /function handleConversationUpdated\(event\)/)
  assert.match(chatSource, /if \(!conversationId \|\| loading\.value \|\| currentConvId\.value !== conversationId\) return/)
  assert.match(chatSource, /inputText\.value = ''\n\s*promptPrefilled\.value = false/)
  assert.match(chatSource, /window\.addEventListener\('lifewise-conversation-updated', handleConversationUpdated\)/)
})

test('scene image tasks expose recovery and always stop polling', () => {
  assert.match(chatSource, /message\._sceneImageRequest = \{ prompt, scene, cacheName \}/)
  assert.match(chatSource, /function retrySceneImage\(message\)/)
  assert.match(chatSource, /if \(attempts >= 60\)/)
  assert.match(chatSource, /if \(m\._sceneImagePoll\) clearInterval\(m\._sceneImagePoll\)/)
  assert.match(chatSource, /function failSceneImage\(message, error\)/)
})

test('chat controls remain keyboard reachable and live acceptance retries transient failures', () => {
  assert.doesNotMatch(chatSource, /<div class="export-item" @click=/)
  assert.doesNotMatch(chatSource, /<div v-for="wm in writingModes"[^>]*@click=/)
  assert.match(chatSource, /aria-label="上传图片"/)
  assert.match(chatSource, /aria-label="发送消息"/)
  assert.match(acceptanceSource, /const maxAttempts = 3/)
  assert.match(acceptanceSource, /status === 429 \|\| status >= 500/)
  assert.match(acceptanceSource, /requestWithRetry\(body\)/)
})

test('stream updates are batched while structured replies retain a readable preview and fallback body', () => {
  assert.match(chatSource, /const STREAM_RENDER_INTERVAL = 100/)
  assert.match(chatSource, /function queueStreamUpdate\(target, content, scene, isFollowUp\)/)
  assert.match(chatSource, /setTimeout\(flushStreamUpdate, STREAM_RENDER_INTERVAL\)/)
  assert.match(chatSource, /function flushStreamUpdate\(\)/)
  assert.match(chatSource, /function renderStructuredStreamPreview\(content\)/)
  assert.match(chatSource, /html \+ '<div class="structured-fallback">/)
  assert.match(chatSource, /requestAnimationFrame\(\(\) =>/)
})

test('questions are persisted before a chat request and excluded from its own AI context', () => {
  assert.match(apiSource, /const persistUser = options\.persistUser !== false/)
  assert.match(apiSource, /if \(persistUser\) addMessage\(convId, 'user', message \|\| '', imageUrl \|\| ''\)/)
  assert.match(apiSource, /if \(createdConversation\) options\.onConversationCreated\?\.\(convId\)/)
  assert.match(apiSource, /excludeLatestUser: persistUser/)
  assert.match(chatSource, /persistUser: !retryMessage/)
  assert.match(aiClientSource, /if \(excludeLatestUser && history\.at\(-1\)\?\.role === 'user'\) history\.pop\(\)/)
})

test('ordinary click and Enter sends do not pass browser events as retry messages', () => {
  assert.match(chatSource, /@keydown\.enter\.exact\.prevent="send\(\)"/)
  assert.match(chatSource, /aria-label="发送消息"[^>]*@click="send\(\)"/)
})

test('user messages never enter the assistant structured-card fallback renderer', () => {
  assert.match(chatSource, /<template v-if="msg\.role === 'user'">[\s\S]*?renderMarkdown\(msg\.content\)/)
})

test('active chat recovery is isolated to the selected scene', () => {
  assert.match(chatSource, /getActiveConversationRecovery\(localStorage\.getItem\('currentScene'\) \|\| 'other'\)/)
  assert.match(chatSource, /function rememberActiveConversation\(id, scene\)/)
  assert.match(chatSource, /JSON\.stringify\(\{ id, scene, createdAt: Date\.now\(\) \}\)/)
  assert.match(chatSource, /function getActiveConversationRecovery\(scene\)/)
  assert.match(chatSource, /if \(value\.scene !== scene\) return null/)
})

test('a created conversation anchors the URL without interrupting the active stream', () => {
  assert.match(chatSource, /anchorConversationRoute\(res\.data\?\.conversationId\)/)
  assert.match(chatSource, /function anchorConversationRoute\(id\)/)
  assert.match(chatSource, /router\.resolve\(\{ path: '\/chat\/' \+ id \}\)\.href/)
  assert.match(chatSource, /window\.history\.replaceState\(window\.history\.state, '', href\)/)
})
