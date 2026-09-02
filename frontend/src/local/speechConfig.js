export function buildNativeSpeechOptions(config = {}, endpoints = {}, speechEndpoints = endpoints) {
  const baseUrl = String(config.baseUrl || '')
  const speechBaseUrl = String(config.speechBaseUrl || '')
  const configuredSpeechModel = String(config.speechModel || '').trim()
  const isDashScope = /dashscope\.aliyuncs\.com/i.test(speechBaseUrl || baseUrl)
  const usesQwenAsr = isDashScope || /qwen.*asr/i.test(configuredSpeechModel)
  const hasExplicitSpeechConfig = Boolean(speechBaseUrl.trim() || String(config.speechApiKey || '').trim() || configuredSpeechModel)
  const chatAudioModel = /audio|omni|realtime/i.test(configuredSpeechModel) && !/transcrib|whisper|asr/i.test(configuredSpeechModel)
    ? configuredSpeechModel
    : ''

  return {
    language: 'zh-CN',
    preferAiTranscription: isDashScope || hasExplicitSpeechConfig,
    transcribeMode: usesQwenAsr ? 'qwen-chat' : 'auto',
    transcribeUrl: usesQwenAsr ? speechEndpoints.chatUrl : speechEndpoints.audioUrl,
    audioUrl: speechEndpoints.audioUrl || '',
    chatUrl: speechEndpoints.chatUrl || '',
    apiKey: config.speechApiKey || config.apiKey || '',
    speechModel: configuredSpeechModel || (isDashScope ? 'qwen3-asr-flash' : 'whisper-1'),
    chatAudioModel
  }
}
