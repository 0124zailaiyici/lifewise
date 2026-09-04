function hasText(value) {
  return String(value || '').trim().length > 0
}

function containsAny(text, terms) {
  return terms.some(term => text.includes(term))
}

function reply(scene, data, sourceLabel) {
  return {
    content: JSON.stringify(data),
    source: 'safety-rule',
    externalCall: false,
    sourceLabel,
    model: 'local-safety-rule',
    streamed: false
  }
}

export function buildSafetyTriage(message, scene) {
  if (!hasText(message)) return null
  const text = String(message).toLowerCase()

  if (scene !== 'writing') {
    const base = {
      stage: '安全确认', status: '建议专业人员', severity: '严重', need_professional: true,
      observations: [{ label: '是否有人受伤', detail: '如有人触电、昏迷或呼吸困难，立即联系急救服务。' }],
      checklist: [{ item: '现场是否安全', expected: '确认已远离火源、电源和泄漏点，再联系专业人员。' }],
      choices: [], tools: [], steps: [], followUps: ['我已离开危险区域，下一步联系谁？']
    }
    if (containsAny(text, ['燃气', '煤气', 'gas leak', '燃气味', '煤气味'])) {
      return reply(scene, { ...base, title: '疑似燃气泄漏，先撤离通风', problem: '检测到燃气泄漏风险信号', next_action: '不要开关电器、点火或拨打室内电话；离开室内后联系燃气抢修。仅在无风险时关闭燃气总阀。', stop_conditions: ['闻到持续燃气味', '听到燃气泄漏声', '出现头晕、恶心或人员不适'] }, '修理安全分流')
    }
    if (containsAny(text, ['漏电', '触电', '冒烟', '焦味', '烧焦', '火花', '插座发烫', '电线发烫'])) {
      return reply(scene, { ...base, title: '存在电气危险，先停止接触', problem: '检测到触电、过热或起火风险信号', next_action: '不要触碰设备、插座或积水区域；仅在远离故障点且环境干燥时关闭总电源，然后联系电工或物业。', stop_conditions: ['冒烟、火花或焦味', '插座或电线明显发烫', '设备附近有积水或人员已触电'] }, '修理安全分流')
    }
    if (containsAny(text, ['大量漏水', '喷涌', '爆管', '水管爆', '天花板漏水'])) {
      return reply(scene, { ...base, title: '大量漏水，先控制水源', problem: '检测到可能造成财产或电气风险的大量漏水', next_action: '不要靠近带电设备或积水插座；在安全可达时关闭入户总水阀，并联系物业或水管维修人员。', stop_conditions: ['水接近插座、配电箱或电器', '水流无法用容器控制', '天花板鼓包、开裂或持续滴水'] }, '修理安全分流')
    }
  }

  if (scene !== 'writing') {
    const base = {
      question: message, category: '急救知识', disclaimer: '此回答仅供参考，不能代替专业医疗建议。',
      stage: '风险筛查', status: '紧急就医', symptoms: [], causes: [], prevention: '', choices: [], checklist: [],
      observations: [{ label: '症状变化', detail: '记录开始时间、是否加重，以及是否伴随意识或呼吸变化。' }],
      stop_conditions: ['出现或加重呼吸困难、意识异常、大量出血或剧烈疼痛时，不要继续居家观察。'],
      followUps: ['我已联系急救服务，等待时还需要记录什么？']
    }
    if (containsAny(text, ['胸痛', '剧烈胸痛', '胸闷', '呼吸困难', '喘不上气', '呼吸急促', '气短', '呼吸不畅'])) {
      return reply(scene, { ...base, next_action: '立即联系急救服务或前往急诊；等待帮助时保持安全体位，不要自行驾车。', advice: [{ item: '立即寻求急救', detail: '胸痛或呼吸困难需要尽快由专业人员评估。' }], when_to_see_doctor: '现在就应就医，不要等待症状自行缓解。' }, '健康紧急分流')
    }
    if (containsAny(text, ['昏迷', '意识不清', '叫不醒', '抽搐'])) {
      return reply(scene, { ...base, next_action: '立即联系急救服务；确保周围安全，除非存在立即危险，不要强行移动对方。', advice: [{ item: '保持呼吸道周围通畅', detail: '不要把物品塞入口中，也不要强行喂水或喂药。' }], when_to_see_doctor: '现在就应就医，意识异常和抽搐需要紧急评估。' }, '健康紧急分流')
    }
    if (containsAny(text, ['单侧无力', '一侧无力', '口角歪', '嘴歪', '言语含糊', '说话含糊', '突然失语'])) {
      return reply(scene, { ...base, next_action: '立即联系急救服务或前往急诊；记录症状最早出现时间，不要自行驾车。', advice: [{ item: '优先排查急性脑血管风险', detail: '突发单侧无力、口角歪斜或言语含糊需要尽快由急诊专业人员评估。' }], when_to_see_doctor: '现在就应就医，不要等待症状自行缓解。' }, '健康紧急分流')
    }
    if (containsAny(text, ['严重过敏', '喉咙肿', '嘴唇肿', '脸肿', '全身风团'])) {
      return reply(scene, { ...base, next_action: '立即联系急救服务或前往急诊；远离可能诱因，不要独自等待症状变化。', advice: [{ item: '优先处理呼吸风险', detail: '面部或喉咙肿胀可能快速影响呼吸。' }], when_to_see_doctor: '现在就应就医，特别是伴随喘鸣、头晕或声音变化时。' }, '健康紧急分流')
    }
    if (containsAny(text, ['大量出血', '呕血', '咳血', '便血', '高烧不退', '持续高烧', '40度'])) {
      return reply(scene, { ...base, next_action: '尽快前往急诊或联系急救服务；记录症状开始时间和变化，不要自行加用药物掩盖症状。', advice: [{ item: '准备就医信息', detail: '告知专业人员出血或高热的开始时间、频率及伴随症状。' }], when_to_see_doctor: '现在就应就医，不要继续居家观察。' }, '健康紧急分流')
    }
  }

  if (scene !== 'writing' && (scene === 'pet' || containsAny(text, ['猫', '狗', '宠物', '兔', '仓鼠', '鸟']))) {
    const base = {
      pet_type: '待确认', topic: '紧急症状分流', difficulty: '入门', stage: '风险筛查', status: '紧急就诊', choices: [],
      common_mistakes: ['不要喂人用药', '不要强制灌水、催吐或喂食'],
      observations: [{ label: '转运前记录', detail: '记录宠物种类、体重、症状出现时间，以及是否进食饮水或排泄异常。' }],
      checklist: [{ item: '联系医院', expected: '确认医院可接诊并说明紧急症状。' }],
      stop_conditions: ['呼吸困难、抽搐、误食风险物或无法站立时，不要继续居家观察。'],
      followUps: ['我已联系宠物医院，转运时需要带什么？']
    }
    if (containsAny(text, ['呼吸困难', '喘不上气', '抽搐', '无法站立', '站不起来'])) {
      return reply(scene, { ...base, next_action: '立即联系附近宠物医院并尽快出发；保持宠物安静、通风，避免挤压胸腹部。', steps: [{ step: 1, action: '用航空箱或平稳垫子转运，减少移动和刺激。' }], when_to_see_vet: '现在就应紧急就诊，途中如症状加重，向医院说明情况。' }, '宠物紧急分流')
    }
    if (containsAny(text, ['误食', '误吞', '吞了', '异物', '毒物', '中毒', '巧克力', '老鼠药', '百合'])) {
      return reply(scene, { ...base, next_action: '立即联系宠物医院，带上误食物包装或拍照；不要自行催吐或灌水。', steps: [{ step: 1, action: '记录误食物、估计时间和数量，立即告知宠物医院。' }], when_to_see_vet: '现在就应紧急咨询或就诊，异物和毒物的处理时机很关键。' }, '宠物紧急分流')
    }
    if (containsAny(text, ['严重外伤', '被车撞', '大出血', '骨折', '咬伤很深'])) {
      return reply(scene, { ...base, next_action: '立即联系宠物医院；用干净纱布轻压明显出血处，避免自行复位或清理深伤口。', steps: [{ step: 1, action: '平稳限制活动后尽快转运，避免触碰疼痛部位。' }], when_to_see_vet: '现在就应紧急就诊，外伤可能存在不可见的内部损伤。' }, '宠物紧急分流')
    }
  }

  return null
}
