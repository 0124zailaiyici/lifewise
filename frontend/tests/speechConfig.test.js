import test from 'node:test'
import assert from 'node:assert/strict'
import { buildNativeSpeechOptions } from '../src/local/speechConfig.js'

const endpoints = {
  audioUrl: 'https://example.com/v1/audio/transcriptions',
  chatUrl: 'https://example.com/v1/chat/completions'
}

test('DashScope keeps system voice recognition first when speech is not configured', () => {
  const options = buildNativeSpeechOptions({
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    apiKey: 'test-key',
    model: 'qwen-plus'
  }, endpoints)

  assert.equal(options.transcribeMode, 'qwen-chat')
  assert.equal(options.speechModel, 'qwen3-asr-flash')
  assert.equal(options.allowAiTranscription, false)
  assert.equal(options.preferAiTranscription, false)
  assert.equal('chatModel' in options, false)
  assert.equal(options.chatUrl, endpoints.chatUrl)
  assert.equal(options.audioUrl, endpoints.audioUrl)
})

test('unknown OpenAI-compatible providers use automatic speech fallback', () => {
  const options = buildNativeSpeechOptions({
    baseUrl: 'https://grok.example/v1',
    apiKey: 'test-key',
    model: 'grok-4.5'
  }, endpoints)

  assert.equal(options.transcribeMode, 'auto')
  assert.equal(options.speechModel, 'whisper-1')
  assert.equal(options.preferAiTranscription, false)
  assert.equal(options.allowAiTranscription, false)
  assert.equal(options.chatAudioModel, '')
  assert.equal('chatModel' in options, false)
  assert.equal(options.chatUrl, endpoints.chatUrl)
  assert.equal(options.audioUrl, endpoints.audioUrl)
})

test('an explicitly configured qwen ASR model skips the transcription endpoint', () => {
  const options = buildNativeSpeechOptions({
    baseUrl: 'https://gateway.example/v1',
    apiKey: 'test-key',
    model: 'grok-4.5',
    speechModel: 'qwen3-asr-flash'
  }, endpoints)

  assert.equal(options.transcribeMode, 'qwen-chat')
  assert.equal(options.speechModel, 'qwen3-asr-flash')
  assert.equal(options.preferAiTranscription, true)
  assert.equal(options.allowAiTranscription, true)
})

test('uses a separate speech endpoint and key when configured', () => {
  const options = buildNativeSpeechOptions({
    baseUrl: 'https://chat.example/v1',
    apiKey: 'chat-key',
    model: 'gpt-5.4',
    speechBaseUrl: 'https://speech.example/v1',
    speechApiKey: 'speech-key',
    speechModel: 'whisper-1'
  }, {
    chatUrl: 'https://chat.example/v1/chat/completions',
    audioUrl: 'https://chat.example/v1/audio/transcriptions'
  }, {
    chatUrl: 'https://speech.example/v1/chat/completions',
    audioUrl: 'https://speech.example/v1/audio/transcriptions'
  })

  assert.equal(options.apiKey, 'speech-key')
  assert.equal(options.preferAiTranscription, true)
  assert.equal(options.allowAiTranscription, true)
  assert.equal(options.audioUrl, 'https://speech.example/v1/audio/transcriptions')
  assert.equal(options.chatUrl, 'https://speech.example/v1/chat/completions')
  assert.equal('chatModel' in options, false)
})

test('only an explicitly configured audio chat model may be used as chat fallback', () => {
  const options = buildNativeSpeechOptions({
    baseUrl: 'https://gateway.example/v1',
    apiKey: 'test-key',
    model: 'gpt-5.4',
    speechModel: 'gpt-4o-audio-preview'
  }, endpoints)

  assert.equal(options.preferAiTranscription, true)
  assert.equal(options.chatAudioModel, 'gpt-4o-audio-preview')
})
