<template>
  <div class="page-container">
    <div class="search-header">
      <el-button text @click="$router.back()">←</el-button>
      <div class="search-input-wrap">
        <el-input v-model="keyword" placeholder="搜索历史对话或知识库..." size="large"
                  @keyup.enter="doSearch" ref="inputRef" clearable />
      </div>
      <el-button text type="success" @click="doSearch">搜索</el-button>
    </div>

    <div class="content">
      <div v-if="searched && total === 0" class="empty">
        <div class="empty-icon">🔍</div>
        <div class="empty-text">没有找到相关内容</div>
      </div>

      <div v-if="conversations.length > 0" class="result-group">
        <div class="group-title">💬 对话 ({{ conversations.length }})</div>
        <div v-for="item in conversations" :key="'c'+item.id" class="result-item"
             @click="$router.push('/chat/' + item.id)">
          <span class="r-icon">{{ item.sceneIcon }}</span>
          <div class="r-info">
            <div class="r-title">{{ item.title }}</div>
            <div class="r-meta">{{ item.sceneLabel }}</div>
          </div>
        </div>
      </div>

      <div v-if="messages.length > 0" class="result-group">
        <div class="group-title">📝 消息 ({{ messages.length }})</div>
        <div v-for="item in messages" :key="'m'+item.id" class="result-item"
             @click="$router.push('/chat/' + item.conversationId)">
          <span class="r-icon">{{ item.role === 'user' ? '👤' : '🤖' }}</span>
          <div class="r-info">
            <div class="r-title">{{ item.convTitle }}</div>
            <div class="r-snippet">{{ item.snippet }}</div>
          </div>
        </div>
      </div>

      <div v-if="knowledge.length > 0" class="result-group">
        <div class="group-title">📚 知识库 ({{ knowledge.length }})</div>
        <div v-for="item in knowledge" :key="'k'+item.id" class="result-item">
          <span class="r-icon">📖</span>
          <div class="r-info">
            <div class="r-title">{{ item.question }}</div>
            <div class="r-meta">有用 {{ item.helpfulCount }} 次</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { search } from '../api'

const keyword = ref('')
const inputRef = ref(null)
const searched = ref(false)
const total = ref(0)
const conversations = ref([])
const messages = ref([])
const knowledge = ref([])

onMounted(async () => {
  await nextTick()
  inputRef.value?.focus()
})

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) return
  searched.value = true
  try {
    const res = await search(q)
    const data = res.data
    conversations.value = data.conversations || []
    messages.value = data.messages || []
    knowledge.value = data.knowledge || []
    total.value = data.total || 0
  } catch (e) {
    console.error(e)
  }
}
</script>

<style scoped>
.search-header { display: flex; align-items: center; gap: 8px; padding: 12px 12px; border-bottom: 1px solid #e0e0e0; }
.search-input-wrap { flex: 1; }
.content { padding: 16px 16px 80px; }
.result-group { margin-bottom: 24px; }
.group-title { font-size: 13px; font-weight: 600; color: #666; margin-bottom: 10px; }
.result-item { display: flex; align-items: center; padding: 12px; background: #f5f5f5; border-radius: 12px; margin-bottom: 8px; cursor: pointer; }
.result-item:active { transform: scale(.98); }
.r-icon { font-size: 22px; margin-right: 12px; }
.r-info { flex: 1; min-width: 0; }
.r-title { font-size: 14px; font-weight: 500; color: #222; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.r-snippet { font-size: 12px; color: #888; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.r-meta { font-size: 12px; color: #888; margin-top: 2px; }
.empty { text-align: center; padding: 80px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: 14px; color: #999; }
</style>
