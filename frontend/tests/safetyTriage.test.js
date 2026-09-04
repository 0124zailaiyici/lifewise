import test from 'node:test'
import assert from 'node:assert/strict'
import { buildSafetyTriage } from '../src/local/safetyTriage.js'

test('local repair triage bypasses the model for electrical fire risk', () => {
  const response = buildSafetyTriage('插座冒烟还有焦味怎么办', 'repair')
  const content = JSON.parse(response.content)

  assert.equal(response.externalCall, false)
  assert.equal(response.source, 'safety-rule')
  assert.equal(content.status, '建议专业人员')
  assert.match(content.next_action, /不要触碰设备/)
})

test('local health triage returns an emergency card before API configuration', () => {
  const response = buildSafetyTriage('突然胸痛而且喘不上气', 'health')
  const content = JSON.parse(response.content)

  assert.equal(response.externalCall, false)
  assert.equal(content.status, '紧急就医')
  assert.match(content.next_action, /立即联系急救服务/)
})

test('local pet triage returns emergency care for toxin ingestion', () => {
  const response = buildSafetyTriage('猫误食了百合怎么办', 'pet')
  const content = JSON.parse(response.content)

  assert.equal(response.externalCall, false)
  assert.equal(content.status, '紧急就诊')
  assert.match(content.next_action, /不要自行催吐/)
})

test('local triage protects users who enter an emergency through other', () => {
  const health = JSON.parse(buildSafetyTriage('突然单侧无力而且说话含糊', 'other').content)
  assert.equal(health.status, '紧急就医')
  assert.match(health.next_action, /立即联系急救服务/)

  const repair = JSON.parse(buildSafetyTriage('厨房闻到煤气味怎么办', 'other').content)
  assert.equal(repair.status, '建议专业人员')
  assert.match(repair.next_action, /离开室内/)

  const pet = JSON.parse(buildSafetyTriage('猫误吞了巧克力怎么办', 'other').content)
  assert.equal(pet.status, '紧急就诊')
  assert.match(pet.next_action, /不要自行催吐/)
})

test('writing requests remain writing requests', () => {
  assert.equal(buildSafetyTriage('帮我写一段胸痛患者的故事', 'writing'), null)
})

test('ordinary questions continue to the configured model', () => {
  assert.equal(buildSafetyTriage('水龙头滴水怎么办', 'repair'), null)
  assert.equal(buildSafetyTriage('成人轻微喉咙痛怎么观察', 'health'), null)
  assert.equal(buildSafetyTriage('猫今天不爱喝水但精神正常', 'pet'), null)
})
