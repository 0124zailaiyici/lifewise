import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const chatSource = await readFile(new URL('../src/views/Chat.vue', import.meta.url), 'utf8')
const acceptanceSource = await readFile(new URL('../scripts/acceptance-ai.mjs', import.meta.url), 'utf8')

test('failed chat requests retain an in-place retry path', () => {
  assert.match(chatSource, /async function send\(retryMessage = null\)/)
  assert.match(chatSource, /m\._retry = \{ message: msg, scene, conversationId: convId, followUp: isFollowUp, imageUrl/)
  assert.match(chatSource, /function retryFailedMessage\(message\)/)
  assert.match(chatSource, /@click="retryFailedMessage\(msg\)"/)
  assert.match(chatSource, /function unlockGuidedCards\(\)/)
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
