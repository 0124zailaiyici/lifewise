import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const chatSource = await readFile(new URL('../src/views/Chat.vue', import.meta.url), 'utf8')

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
