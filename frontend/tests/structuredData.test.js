import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeStructuredData, parseStructuredJson, structuredFallbackText } from '../src/local/structuredData.js'

test('parses direct, fenced, and embedded structured JSON replies', () => {
  assert.deepEqual(parseStructuredJson('{"title":"直接回复"}'), { title: '直接回复' })
  assert.deepEqual(parseStructuredJson('```json\n{"title":"代码块回复",}\n```'), { title: '代码块回复' })
  assert.deepEqual(
    parseStructuredJson('说明文字 {"title":"第一段","detail":"花括号 { 不影响解析"} 后面还有 {not json}'),
    { title: '第一段', detail: '花括号 { 不影响解析' }
  )
})

test('normalizes unstable list fields used by every assistant', () => {
  const data = normalizeStructuredData({
    color_palette: '白色、浅蓝、灰色',
    steps: { step: 1, action: '先检查' },
    advice: '多喝水\n充分休息',
    dont_list: { action: '不要催促' },
    followUps: '还要注意什么？',
    weekly_plan: { day: '周一', meals: '番茄鸡蛋面' }
  })

  assert.deepEqual(data.color_palette, ['白色', '浅蓝', '灰色'])
  assert.deepEqual(data.steps, [{ step: 1, action: '先检查' }])
  assert.deepEqual(data.advice, ['多喝水', '充分休息'])
  assert.deepEqual(data.dont_list, [{ action: '不要催促' }])
  assert.deepEqual(data.followUps, ['还要注意什么？'])
  assert.deepEqual(data.weekly_plan, [{ day: '周一', meals: ['番茄鸡蛋面'] }])
})

test('normalizes scalar fields before renderer string operations', () => {
  const data = normalizeStructuredData({
    occasion: { name: '职场面试' },
    severity: ['轻微', '可自行处理'],
    title: 123
  })

  assert.equal(data.occasion, '职场面试')
  assert.equal(data.severity, '轻微、可自行处理')
  assert.equal(data.title, '123')
})

test('does not create fields belonging to unrelated assistants', () => {
  const data = normalizeStructuredData({ title: '普通建议', answer: '先观察情况' })

  assert.equal('ingredients' in data, false)
  assert.equal('outfits' in data, false)
  assert.equal('weekly_plan' in data, false)
})

test('creates readable fallback text without exposing JSON syntax', () => {
  const text = structuredFallbackText({
    title: '小白鞋搭配',
    color_palette: { primary: '白色', secondary: '牛仔蓝' },
    outfits: [{ piece: '上衣', description: '浅蓝衬衫' }]
  })

  assert.match(text, /小白鞋搭配/)
  assert.match(text, /白色/)
  assert.match(text, /浅蓝衬衫/)
  assert.doesNotMatch(text, /[{}\[\]"]/)
})
