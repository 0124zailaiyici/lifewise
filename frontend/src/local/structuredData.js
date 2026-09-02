const LIST_FIELDS = [
  'ingredients', 'materials', 'steps', 'selection_steps', 'tools',
  'color_palette', 'outfits', 'items', 'accessories', 'symptoms',
  'causes', 'advice', 'do_list', 'dont_list', 'key_principles',
  'weekly_plan', 'shopping_list', 'common_mistakes', 'common_causes',
  'observations', 'checklist', 'choices', 'stop_conditions',
  'followUps', 'suggestions', 'tips', 'avoid', 'recommendations',
  'nutrition_tips'
]

const SCALAR_FIELDS = [
  'title', 'difficulty', 'time', 'servings', 'problem', 'question',
  'occasion', 'style', 'category', 'season', 'storage_tip',
  'summary_slogan', 'severity', 'professional_advice', 'prevention',
  'principle', 'key_point', 'safety_tip', 'answer', 'disclaimer',
  'when_to_see_doctor', 'pet_type', 'topic', 'when_to_see_vet',
  'preference', 'cultural_notes', 'estimated_time', 'stage', 'status',
  'next_action', '品类'
]

const WRAPPER_KEYS = ['items', 'list', 'values', 'data', 'colors', 'options', 'entries', 'results']

function tryJson(value) {
  if (typeof value !== 'string') return null
  const text = value.trim()
  if (!text || !/^[\[{]/.test(text)) return null
  try { return JSON.parse(text) } catch { return null }
}

function tryParseObject(value) {
  const text = String(value || '').trim()
  if (!text || !text.startsWith('{')) return null
  try {
    const parsed = JSON.parse(text)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {}
  try {
    const parsed = JSON.parse(text.replace(/,([\s\n\r]*[}\]])/g, '$1'))
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {}
  try {
    const cleaned = text.replace(/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]/g, '')
    const parsed = JSON.parse(cleaned.replace(/,([\s\n\r]*[}\]])/g, '$1'))
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {}
  try {
    const flattened = text.replace(/[\r\n]+/g, '').replace(/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]/g, '')
    const parsed = JSON.parse(flattened.replace(/,([\s\n\r]*[}\]])/g, '$1'))
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {}
  return null
}

function extractJsonObjects(text) {
  const candidates = []
  let start = -1
  let depth = 0
  let quoted = false
  let escaped = false

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index]
    if (start < 0) {
      if (char === '{') {
        start = index
        depth = 1
      }
      continue
    }
    if (quoted) {
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') quoted = false
      continue
    }
    if (char === '"') quoted = true
    else if (char === '{') depth += 1
    else if (char === '}') {
      depth -= 1
      if (depth === 0) {
        candidates.push(text.slice(start, index + 1))
        start = -1
      }
    }
  }
  return candidates
}

export function parseStructuredJson(value) {
  const text = String(value || '').trim()
  if (!text) return null

  const direct = tryParseObject(text)
  if (direct) return direct

  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i)
  if (fenced) {
    const parsed = tryParseObject(fenced[1])
    if (parsed) return parsed
  }

  for (const candidate of extractJsonObjects(text)) {
    const parsed = tryParseObject(candidate) || tryParseObject(candidate.replace(/[\x00-\x1f\x7f-\x9f]/g, ''))
    if (parsed) return parsed
  }
  return null
}

export function structuredText(value) {
  if (value == null) return ''
  if (typeof value === 'string') return value.trim()
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) return value.map(structuredText).filter(Boolean).join('、')
  if (typeof value === 'object') {
    const preferred = ['text', 'name', 'title', 'label', 'value', 'description', 'detail', 'action', 'item', 'piece', 'color']
    for (const key of preferred) {
      const text = structuredText(value[key])
      if (text) return text
    }
    return Object.values(value).map(structuredText).filter(Boolean).join('、')
  }
  return String(value)
}

function splitStringList(value, field) {
  const parsed = tryJson(value)
  if (parsed != null) return normalizeList(parsed, field)
  const text = String(value || '').trim()
  if (!text) return []

  let parts = text.split(/\r?\n+/).map(item => item.replace(/^\s*(?:[-*•·]|\d+[.)、])\s*/, '').trim()).filter(Boolean)
  if (parts.length === 1 && (field === 'color_palette' || /[、；;|]/.test(text))) {
    parts = text.split(field === 'color_palette' ? /[、，,；;|]+/ : /[、；;|]+/).map(item => item.trim()).filter(Boolean)
  }
  return parts
}

function objectMapToList(value, field) {
  for (const key of WRAPPER_KEYS) {
    if (value[key] != null && value[key] !== value) return normalizeList(value[key], field)
  }

  const itemKeys = ['name', 'piece', 'item', 'action', 'step', 'step_name', 'day', 'category', 'color', 'text']
  if (itemKeys.some(key => value[key] != null)) return [value]

  if (field === 'color_palette') return Object.values(value).flatMap(item => normalizeList(item, field))
  if (field === 'weekly_plan') {
    return Object.entries(value).map(([day, meals]) => ({ day, meals: normalizeList(meals, 'meals') }))
  }
  if (field === 'outfits' || field === 'items' || field === 'accessories') {
    return Object.entries(value).map(([name, description]) => ({
      [field === 'outfits' ? 'piece' : 'name']: name,
      description: structuredText(description)
    }))
  }
  if (field === 'advice') {
    return Object.entries(value).map(([item, detail]) => ({ item, detail: structuredText(detail) }))
  }
  return [value]
}

export function normalizeList(value, field = '') {
  if (value == null || value === '') return []
  if (Array.isArray(value)) {
    return value.flatMap(item => {
      if (typeof item === 'string') return splitStringList(item, field)
      if (item == null) return []
      return [item]
    })
  }
  if (typeof value === 'string') return splitStringList(value, field)
  if (typeof value === 'object') return objectMapToList(value, field)
  return [value]
}

export function normalizeStructuredData(input) {
  const data = input && typeof input === 'object' && !Array.isArray(input) ? { ...input } : { answer: structuredText(input) }

  if (data.color_palette == null && data.colorPalette != null) data.color_palette = data.colorPalette
  if (data.selection_steps == null && data.selectionSteps != null) data.selection_steps = data.selectionSteps
  if (data.followUps == null && (data.follow_ups != null || data.followup_questions != null)) {
    data.followUps = data.follow_ups ?? data.followup_questions
  }

  for (const field of LIST_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(data, field)) data[field] = normalizeList(data[field], field)
  }
  for (const field of SCALAR_FIELDS) {
    if (data[field] != null) data[field] = structuredText(data[field])
  }

  if (Array.isArray(data.weekly_plan)) {
    data.weekly_plan = data.weekly_plan.map(day => {
      if (typeof day === 'string') return { day: '', meals: [day] }
      if (!day || typeof day !== 'object') return { day: '', meals: [structuredText(day)].filter(Boolean) }
      return { ...day, day: structuredText(day.day), meals: normalizeList(day.meals, 'meals') }
    })
  }
  if (Array.isArray(data.shopping_list)) {
    data.shopping_list = data.shopping_list.map(group => {
      if (!group || typeof group !== 'object' || Array.isArray(group)) return group
      return { ...group, category: structuredText(group.category), items: normalizeList(group.items, 'items') }
    })
  }

  return data
}

export function structuredFallbackText(input) {
  const data = normalizeStructuredData(input)
  const values = []
  const seen = new Set()

  function collect(value) {
    if (value == null) return
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      const text = String(value).trim()
      if (text && !seen.has(text)) {
        seen.add(text)
        values.push(text)
      }
      return
    }
    if (Array.isArray(value)) value.forEach(collect)
    else if (typeof value === 'object') Object.values(value).forEach(collect)
  }

  collect(data.title)
  Object.entries(data).forEach(([key, value]) => { if (key !== 'title') collect(value) })
  return values.join('\n') || '回答已生成，但暂时无法显示结构化卡片。'
}
