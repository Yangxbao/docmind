<template>
  <div class="search-page">
    <!-- 搜索框 + 模式切换 -->
    <el-card shadow="never">
      <el-input
        v-model="query"
        size="large"
        placeholder="用一句话描述你要找的内容，比如：向量检索是怎么实现的？"
        clearable
        @keyup.enter="doSearch"
      >
        <template #append>
          <el-button :loading="loading" @click="doSearch">搜 索</el-button>
        </template>
      </el-input>

      <div class="mode-row">
        <el-radio-group v-model="mode">
          <el-radio-button value="semantic">语义检索</el-radio-button>
          <el-radio-button value="keyword">关键词检索</el-radio-button>
        </el-radio-group>
        <span class="mode-tip">
          语义检索：按"意思"匹配（内置词频向量，装 Ollama 后为真语义）；关键词检索：按字面匹配兜底
        </span>
      </div>

      <!-- 搜索历史（只在未搜索时展示） -->
      <div v-if="history.length && !searched" class="history">
        <span class="history-label">最近搜索：</span>
        <el-tag
          v-for="h in history"
          :key="h.id"
          class="mr4"
          effect="plain"
          style="cursor: pointer"
          @click="query = h.keyword; doSearch()"
        >
          {{ h.keyword }}
        </el-tag>
      </div>
    </el-card>

    <!-- 结果列表 -->
    <div v-if="searched" v-loading="loading" class="results">
      <el-empty
        v-if="!loading && !items.length"
        description="没有找到相关文档。试试换个说法，或先上传一些文档。"
      />
      <el-card
        v-for="item in items"
        :key="item.docId"
        shadow="hover"
        class="result-card"
        @click="goPreview(item.docId)"
      >
        <div class="result-head">
          <el-link type="primary" :underline="false" class="file-name">
            {{ item.fileName }}
          </el-link>
          <el-tag size="small" effect="plain">{{ typeLabel(item.fileType) }}</el-tag>
          <span v-if="item.score != null" class="score">相关度 {{ scorePercent(item.score) }}%</span>
          <el-tag v-else size="small" type="info" effect="plain">关键词命中</el-tag>
        </div>

        <el-progress
          v-if="item.score != null"
          :percentage="scorePercent(item.score)"
          :stroke-width="4"
          :show-text="false"
          class="score-bar"
        />

        <div v-if="item.tags && item.tags.length" class="tags">
          <el-tag v-for="t in item.tags" :key="t" size="small" type="info" effect="light" class="mr4">
            {{ t }}
          </el-tag>
        </div>

        <p class="snippet">
          <HighlightText :text="item.snippet" :query="lastQuery" />
        </p>
        <el-button link type="primary">查看文档 →</el-button>
      </el-card>
    </div>

    <el-empty v-else description="输入一句话，用内容找到你的文档" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { search, searchHistory } from '../api/search'
import HighlightText from '../components/HighlightText.vue'

const router = useRouter()

const query = ref('')
const mode = ref('semantic')
const lastQuery = ref('')
const items = ref([])
const history = ref([])
const loading = ref(false)
const searched = ref(false)

async function doSearch() {
  const q = query.value.trim()
  if (!q) return
  loading.value = true
  searched.value = true
  lastQuery.value = q
  try {
    items.value = await search({ query: q, mode: mode.value })
    loadHistory()
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  try {
    history.value = await searchHistory()
  } catch (ignore) {
    history.value = []
  }
}

function goPreview(docId) {
  router.push('/preview/' + docId)
}

function scorePercent(score) {
  return Math.max(1, Math.min(100, Math.round((score || 0) * 100)))
}

function typeLabel(type) {
  return { md: 'MD', txt: 'TXT', pdf: 'PDF', image: '图片' }[type] || type
}

onMounted(loadHistory)
</script>

<style scoped>
.mode-row {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.mode-tip {
  color: #c0c4cc;
  font-size: 12px;
}

.history {
  margin-top: 12px;
  font-size: 13px;
  color: #909399;
}

.results {
  min-height: 200px;
}

.result-card {
  margin-top: 12px;
  cursor: pointer;
}

.result-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.file-name {
  font-size: 15px;
  font-weight: 600;
}

.score {
  color: #67c23a;
  font-size: 13px;
  font-weight: 600;
}

.score-bar {
  margin-top: 6px;
  width: 200px;
}

.tags {
  margin-top: 8px;
}

.snippet {
  margin: 8px 0;
  color: #606266;
  line-height: 1.7;
}
</style>
