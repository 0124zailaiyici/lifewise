import test from 'node:test'
import assert from 'node:assert/strict'
import { buildSystemPrompt } from '../src/local/scenePrompts.js'

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

test('repair, health, and pet use distinct escalation states', () => {
  assert.match(buildSystemPrompt('repair'), /建议专业人员/)
  assert.match(buildSystemPrompt('health'), /紧急就医/)
  assert.match(buildSystemPrompt('pet'), /紧急就诊/)
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
