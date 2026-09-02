import { buildSystemPrompt } from '../src/local/scenePrompts.js'
import { resolveOpenAIEndpoints } from '../src/local/aiClient.js'

const apiKey = String(process.env.AI_API_KEY || '').trim()
const baseUrl = String(process.env.AI_BASE_URL || 'https://zz.caijiyun.top/v1').trim()
const model = String(process.env.AI_MODEL || 'gpt-5.6-terra').trim()
const requestedScenes = new Set(String(process.env.AI_SCENES || '').split(',').map(value => value.trim()).filter(Boolean))

if (!apiKey) {
  console.error('Missing AI_API_KEY')
  process.exit(2)
}

const cases = [
  ['cooking', '两个人做番茄鸡蛋面，只有20分钟。'],
  ['shopping', '买西瓜，今天吃，怎么挑？'],
  ['repair', '厨房水龙头一直滴水，怎么先检查？'],
  ['housework', '白色棉T恤沾到咖啡渍，怎么处理？'],
  ['health', '成人今天轻微喉咙痛，没有发热，先怎么观察？'],
  ['fashion', '明天20度去朋友婚礼，怎么穿得得体？'],
  ['etiquette', '怎么礼貌拒绝同事周末加班邀请？'],
  ['pet', '成年猫今天不太爱喝水，但精神正常，怎么观察？'],
  ['mealplan', '两个人三天晚餐，预算150元，安排菜单。'],
  ['writing', '写一条简短、礼貌的延期交付说明给客户。']
]

const selectedCases = requestedScenes.size ? cases.filter(([scene]) => requestedScenes.has(scene)) : cases
if (!selectedCases.length) {
  console.error('No matching AI_SCENES')
  process.exit(2)
}

const { chatUrl } = resolveOpenAIEndpoints(baseUrl)
let failures = 0

for (const [scene, message] of selectedCases) {
  const body = {
    model,
    messages: [
      { role: 'system', content: buildSystemPrompt(scene) },
      { role: 'user', content: message }
    ],
    temperature: 0,
    max_tokens: 4096,
    stream: false
  }
  if (scene !== 'writing') body.response_format = { type: 'json_object' }

  try {
    const response = await fetch(chatUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify(body)
    })
    const raw = await response.text()
    if (!response.ok) throw new Error(`HTTP ${response.status}: ${raw.slice(0, 160)}`)
    const content = JSON.parse(raw)?.choices?.[0]?.message?.content || ''
    if (!content) throw new Error('empty response')
    if (scene !== 'writing') {
      const parsed = JSON.parse(content)
      const missing = ['stage', 'status', 'next_action', 'choices'].filter(key => !(key in parsed))
      if (missing.length) throw new Error(`missing fields: ${missing.join(', ')}`)
    }
    console.log(`PASS ${scene}`)
  } catch (error) {
    failures += 1
    console.error(`FAIL ${scene}: ${error.message}`)
  }
}

process.exitCode = failures ? 1 : 0
