import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { buildSystemPrompt } from '../src/local/scenePrompts.js'

const backendConfig = await readFile(new URL('../../backend/src/main/resources/application.yml', import.meta.url), 'utf8')
const backendAiService = await readFile(new URL('../../backend/src/main/java/com/lifewise/service/impl/AiServiceImpl.java', import.meta.url), 'utf8')
const taskFlowFields = ['stage', 'status', 'next_action', 'observations', 'checklist', 'choices', 'stop_conditions', 'followUps']
const structuredScenes = ['cooking', 'housework', 'health', 'pet', 'shopping', 'fashion', 'mealplan', 'etiquette', 'default', 'other']

function backendSchema(scene) {
  const key = scene === 'other' ? 'default' : scene
  const header = `\n    ${key}: |`
  const start = backendConfig.indexOf(header)
  assert.notEqual(start, -1, `missing backend schema for ${key}`)
  const contentStart = start + header.length
  const next = backendConfig.slice(contentStart).search(/\n    [a-z][a-z_-]*: \|/)
  return backendConfig.slice(start, next === -1 ? undefined : contentStart + next)
}

for (const scene of ['cooking', 'housework', 'health', 'pet', 'shopping', 'fashion', 'mealplan', 'etiquette']) {
  test(`${scene} keeps the guided JSON workflow for follow-ups`, () => {
    const initial = buildSystemPrompt(scene)
    const followUp = buildSystemPrompt(scene, true)

    for (const prompt of [initial, followUp]) {
      assert.match(prompt, /stage/)
      assert.match(prompt, /status/)
      assert.match(prompt, /next_action/)
      assert.match(prompt, /choices/)
      assert.match(prompt, /直接输出JSON/)
    }
    assert.doesNotMatch(followUp, /不要输出 JSON 格式/)
  })
}

for (const scene of ['default', 'other']) {
  test(`${scene} keeps the structured task flow for follow-ups`, () => {
    for (const prompt of [buildSystemPrompt(scene), buildSystemPrompt(scene, true)]) {
      for (const field of ['stage', 'status', 'next_action', 'observations', 'checklist', 'choices', 'stop_conditions', 'followUps']) {
        assert.match(prompt, new RegExp(field))
      }
      assert.match(prompt, /直接输出JSON/)
    }
  })
}

test('frontend and backend keep the same structured task-flow contract', () => {
  for (const scene of structuredScenes) {
    const frontendPrompt = buildSystemPrompt(scene)
    const backendPrompt = backendSchema(scene)
    for (const field of taskFlowFields) {
      assert.match(frontendPrompt, new RegExp(field), `${scene} frontend missing ${field}`)
      assert.match(backendPrompt, new RegExp(field), `${scene} backend missing ${field}`)
    }
  }
})

test('repair, health, and pet use distinct escalation states', () => {
  assert.match(buildSystemPrompt('repair'), /建议专业人员/)
  assert.match(buildSystemPrompt('health'), /紧急就医/)
  assert.match(buildSystemPrompt('pet'), /紧急就诊/)
  assert.match(backendConfig, /status: 需补充信息\/居家观察\/建议就医\/紧急就医\/已缓解/)
  assert.match(backendConfig, /status: 需补充信息\/居家观察\/建议就诊\/紧急就诊\/已缓解/)
})

test('decision scenes use distinct confirmation states', () => {
  assert.match(buildSystemPrompt('shopping'), /已选好/)
  assert.match(buildSystemPrompt('fashion'), /建议试穿/)
  assert.match(buildSystemPrompt('mealplan'), /已确认/)
  assert.match(buildSystemPrompt('etiquette'), /可自行表达/)
})

test('writing keeps direct draft output for revision actions', () => {
  assert.doesNotMatch(buildSystemPrompt('writing'), /直接输出JSON/)
  assert.match(buildSystemPrompt('writing', true), /直接继续完成用户要的文字/)
})

test('normal messages can continue an existing task without a mode switch', () => {
  for (const scene of ['cooking', 'other']) {
    assert.match(buildSystemPrompt(scene), /补充条件、选择分支、替换材料或反馈上一步结果/)
    assert.match(buildSystemPrompt(scene), /只有用户明确提出新话题时才重新开始/)
  }
})

test('backend keeps every guided scene structured during follow-ups', () => {
  assert.match(backendAiService, /boolean structuredWorkflow = !"writing"\.equals\(scene\);/)
  for (const scene of ['cooking', 'housework', 'health', 'pet', 'shopping', 'fashion', 'mealplan', 'etiquette']) {
    assert.match(backendConfig, new RegExp(`\\n    ${scene}: 用户正在当前`))
  }
  assert.match(backendConfig, /所有场景都必须完成一次可继续推进的任务闭环/)
  for (const field of taskFlowFields) {
    assert.match(backendConfig, new RegExp(field))
  }
})

test('high-frequency scenes require observable, actionable guidance', () => {
  assert.match(buildSystemPrompt('cooking'), /火力、时长、用量或可观察的完成信号/)
  assert.match(buildSystemPrompt('shopping'), /可见、可比较的判断标准/)
  assert.match(buildSystemPrompt('repair'), /正常\/异常分别意味着什么/)
  assert.match(buildSystemPrompt('housework'), /浓度、作用时间、擦拭方向或冲洗方式/)
})

test('specialized scenes require domain-specific response quality', () => {
  assert.match(buildSystemPrompt('health'), /频率、持续时间或变化信号/)
  assert.match(buildSystemPrompt('pet'), /饮水、进食、排泄、精神状态/)
  assert.match(buildSystemPrompt('fashion'), /不超过三种主色/)
  assert.match(buildSystemPrompt('mealplan'), /主食、蛋白和蔬菜搭配/)
  assert.match(buildSystemPrompt('etiquette'), /可直接发送或当面说的示例表达/)
  assert.match(buildSystemPrompt('writing'), /文体、对象、语气、长度/)
  assert.match(backendConfig, /记录频率、持续时间或变化信号/)
  assert.match(backendConfig, /饮水、进食、排泄、精神状态/)
})
