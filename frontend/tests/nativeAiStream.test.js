import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const pluginSource = await readFile(new URL('../android/app/src/main/java/com/lifewise/app/NativeAiStreamPlugin.java', import.meta.url), 'utf8')
const clientSource = await readFile(new URL('../src/local/aiClient.js', import.meta.url), 'utf8')

test('native streaming avoids returning the full SSE transcript over the bridge', () => {
  assert.match(pluginSource, /boolean sawStreamChunk = false/)
  assert.match(pluginSource, /sawStreamChunk = true/)
  assert.match(pluginSource, /data\.put\("rawText", sawStreamChunk \? "" : raw\.toString\(\)\)/)
})

test('native completion resolves before it removes its own listener', () => {
  assert.match(clientSource, /resolve\(\{ content, rawText: event\?\.rawText \|\| '' \}\)\n\s*\/\/ Do not await/)
  assert.match(clientSource, /cleanup\(\)\.catch\(\(\) => \{\}\)/)
  assert.doesNotMatch(clientSource, /await cleanup\(\)\n\s*resolve\(\{ content, rawText: event\?\.rawText/)
})

test('Android chat uses a complete response while browsers keep streaming', () => {
  assert.match(clientSource, /NativeAiStream\.request\(/)
  assert.match(clientSource, /requestNativeChatResponse\(chatUrl, cfg\.apiKey, \{ \.\.\.body, stream: false \}\)/)
  assert.match(clientSource, /stream: true/)
})
