import { expect, test } from '@playwright/test'

const scenarios = [
  {
    key: 'cooking', label: '做饭助手', prompt: '番茄炒蛋怎么做？', marker: 'E2E 做饭方案', structured: true,
    response: { title: 'E2E 做饭方案', ingredients: [{ name: '鸡蛋', amount: '2 个' }], steps: [{ step: 1, action: '将鸡蛋炒熟', time: '2 分钟' }] }
  },
  {
    key: 'shopping', label: '购物挑选', prompt: '西瓜怎么挑？', marker: 'E2E 购物方案', structured: true,
    response: { title: 'E2E 购物方案', selection_steps: [{ step_name: '看纹路', action: '选择纹路清晰的西瓜' }] }
  },
  {
    key: 'repair', label: '修理指南', prompt: '水龙头滴水怎么办？', marker: 'E2E 修理方案', structured: true,
    response: { problem: 'E2E 修理方案', severity: '轻微', tools: ['扳手'], steps: [{ step: 1, action: '关闭角阀后检查垫圈' }] }
  },
  {
    key: 'housework', label: '家务技巧', prompt: '白衬衫染色怎么办？', marker: 'E2E 家务方案', structured: true,
    response: { problem: 'E2E 家务方案', materials: ['中性洗衣液'], steps: [{ step: 1, action: '先局部测试再清洗' }] }
  },
  {
    key: 'health', label: '健康常识', prompt: '久坐后腰酸怎么办？', marker: 'E2E 健康方案', structured: true,
    response: { title: 'E2E 健康方案', category: '症状处理', symptoms: ['腰部酸胀'], advice: [{ item: '起身活动', detail: '每小时活动几分钟' }], when_to_see_doctor: '疼痛持续或加重时就医' }
  },
  {
    key: 'fashion', label: '穿搭指南', prompt: '面试怎么穿？', marker: 'E2E 穿搭方案', structured: true,
    response: { title: 'E2E 穿搭方案', occasion: '职场面试', outfits: [{ piece: '衬衫', color: '白色', description: '保持整洁' }] }
  },
  {
    key: 'etiquette', label: '社交礼仪', prompt: '第一次见长辈怎么打招呼？', marker: 'E2E 礼仪方案', structured: true,
    response: { title: 'E2E 礼仪方案', do_list: [{ action: '主动问好', reason: '表达尊重' }], dont_list: ['避免频繁看手机'] }
  },
  {
    key: 'pet', label: '宠物照顾', prompt: '猫咪不爱喝水怎么办？', marker: 'E2E 宠物方案', structured: true,
    response: { title: 'E2E 宠物方案', pet_type: '猫', steps: [{ step: 1, action: '增加流动水源' }], observations: [{ label: '饮水记录', detail: '记录近 24 小时饮水量' }], checklist: [{ item: '确认宠物信息', expected: '了解年龄和体重' }], stop_conditions: ['持续不饮水时联系兽医'], when_to_see_vet: '持续不饮水时联系兽医' }
  },
  {
    key: 'writing', label: '写作助手', prompt: '帮我写一条请假消息。', marker: 'E2E 写作回复', structured: false,
    response: 'E2E 写作回复：您好，我今天需要请假一天。'
  },
  {
    key: 'mealplan', label: '食谱推荐', prompt: '安排一份简单晚餐。', marker: 'E2E 食谱方案', structured: true,
    response: { title: 'E2E 食谱方案', weekly_plan: [{ day: '周一', meals: [{ type: '晚餐', name: '番茄炒蛋', time: '20 分钟' }] }], shopping_list: ['鸡蛋'] }
  }
]

function streamResponse(content) {
  return `data: ${JSON.stringify({ choices: [{ delta: { content } }] })}\n\ndata: [DONE]\n\n`
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'E2E 测试用户', token: 'e2e-token' }))
    localStorage.setItem('lw_local_ai_config', JSON.stringify({
      baseUrl: 'http://127.0.0.1:4173',
      apiKey: 'test',
      model: 'e2e-model',
      contextRounds: 5
    }))
    localStorage.setItem('setting_foodImage', 'off')
  })
})

for (const scenario of scenarios) {
  test(`${scenario.label} can complete a chat from its home entry`, async ({ page }) => {
    const requests = []
    await page.route('**/v1/chat/completions', async route => {
      requests.push(route.request().postDataJSON())
      await route.fulfill({
        contentType: 'text/event-stream; charset=utf-8',
        body: streamResponse(typeof scenario.response === 'string' ? scenario.response : JSON.stringify(scenario.response))
      })
    })

    await page.goto('/#/home')
    await page.locator('.scene-card').filter({ hasText: scenario.label }).click()
    await expect(page).toHaveURL(/#\/chat$/)
    await expect(page.locator('.header-title')).toHaveText(scenario.label)

    await page.locator('textarea').fill(scenario.prompt)
    await page.getByRole('button', { name: '发送消息' }).click()

    const assistantCard = page.locator('article.assistant-card').last()
    await expect(page.locator('.user-bubble')).toContainText(scenario.prompt)
    await expect(assistantCard).toContainText(scenario.marker)
    await expect.poll(() => requests.length).toBe(1)

    const request = requests[0]
    expect(request.model).toBe('e2e-model')
    expect(request.messages.at(-1)).toEqual({ role: 'user', content: scenario.prompt })
    expect(Boolean(request.response_format)).toBe(scenario.structured)
    if (scenario.structured) await expect(assistantCard.locator('.s-card')).toBeVisible()
    if (scenario.key === 'pet') {
      await expect(assistantCard.locator('.repair-observation')).toHaveCSS('flex-direction', 'column')
      await expect(assistantCard.locator('.repair-check > div')).toHaveCSS('flex-direction', 'column')
      await expect(assistantCard.locator('.repair-stop')).toBeVisible()
    }
  })
}

test('guided Markdown fallback separates a bold label from its description', async ({ page }) => {
  await page.route('**/v1/chat/completions', async route => {
    await route.fulfill({
      contentType: 'text/event-stream; charset=utf-8',
      body: streamResponse('### 检查清单\n**纯色短袖T恤** 白色或灰色，单穿不紧身。')
    })
  })

  await page.goto('/#/home')
  await page.locator('.scene-card').filter({ hasText: '穿搭指南' }).click()
  await page.locator('textarea').fill('面试怎么穿？')
  await page.getByRole('button', { name: '发送消息' }).click()

  const line = page.locator('.md-labeled-line')
  await expect(line).toContainText('纯色短袖T恤')
  await expect(line).toContainText('白色或灰色')
  await expect(line).toHaveCSS('display', 'grid')
  await expect(line).toHaveCSS('column-gap', '10px')
  const labelColumnWidth = await line.evaluate(element => Number.parseFloat(getComputedStyle(element).gridTemplateColumns.split(' ')[0]))
  expect(labelColumnWidth).toBeGreaterThan(100)
})

test('AI direct configuration keeps optional speech settings collapsed by default', async ({ page }) => {
  await page.goto('/#/profile')

  const configSummary = page.locator('.ai-config-summary')
  const advancedToggle = page.getByRole('button', { name: '高级设置' })
  const speechModel = page.getByPlaceholder('语音模型，如 whisper-1 或 qwen3-asr-flash')

  await expect(configSummary).toHaveAttribute('aria-expanded', 'false')
  await expect(advancedToggle).toBeHidden()
  await expect(speechModel).toBeHidden()

  await configSummary.click()
  await expect(configSummary).toHaveAttribute('aria-expanded', 'true')
  await expect(page.getByPlaceholder('聊天模型，如 gpt-5.4-mini')).toBeVisible()
  await expect(advancedToggle).toBeVisible()
  await expect(speechModel).toBeHidden()

  await advancedToggle.click()
  await expect(speechModel).toBeVisible()
  await expect(page.getByText('默认使用手机系统语音识别，无需语音模型。填写后优先使用你的语音转写服务。')).toBeVisible()
})
