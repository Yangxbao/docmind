<template>
  <div>
    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="mb12">
      <div class="flex-row">
        <el-input
          v-model="query.keyword"
          placeholder="搜索文件名或正文内容"
          clearable
          style="width: 240px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="query.type" style="width: 130px" @change="load">
          <el-option label="全部类型" value="all" />
          <el-option label="Markdown" value="md" />
          <el-option label="TXT" value="txt" />
          <el-option label="PDF" value="pdf" />
          <el-option label="图片" value="image" />
        </el-select>
        <el-select
          v-model="query.tagId"
          clearable
          placeholder="按标签筛选"
          style="width: 150px"
          @change="load"
        >
          <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>

        <el-tooltip content="切换向量模型（内置 ↔ Ollama）后需要重建索引" placement="top">
          <el-popconfirm
            title="重建索引会对所有文档重新解析和向量化，耗时较长，确定继续吗？"
            width="300"
            @confirm="doReindex"
          >
            <template #reference>
              <el-button :loading="reindexing">重建索引</el-button>
            </template>
          </el-popconfirm>
        </el-tooltip>
      </div>
    </el-card>

    <!-- 文档列表 -->
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" size="default">
        <el-table-column label="文件名" min-width="240">
          <template #default="{ row }">
            <el-link type="primary" @click="goPreview(row.id)">{{ row.fileName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeLabel(row.fileType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="t in row.tags"
              :key="t"
              size="small"
              type="info"
              effect="light"
              class="mr4"
            >
              {{ t }}
            </el-tag>
            <span v-if="!row.tags || !row.tags.length" class="empty">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="warning" size="small">处理中…</el-tag>
            <el-tag v-else-if="row.status === 1" type="success" size="small">完成</el-tag>
            <el-tooltip v-else :content="row.failReason || '处理失败'" placement="top">
              <el-tag type="danger" size="small">失败</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ humanSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="150" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goPreview(row.id)">预览</el-button>
            <el-popconfirm title="确定删除该文档吗？" @confirm="doDelete(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        v-model:current-page="query.pageNum"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pageDocs, deleteDoc, reindexDocs } from '../api/doc'
import { listTags } from '../api/tag'

const router = useRouter()

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', type: 'all', tagId: null })
const rows = ref([])
const total = ref(0)
const tags = ref([])
const loading = ref(false)
const reindexing = ref(false)

let pollTimer = null

async function load() {
  loading.value = true
  try {
    const page = await pageDocs(query)
    rows.value = page.records || []
    total.value = Number(page.total) || 0
    schedulePoll()
  } finally {
    loading.value = false
  }
}

async function loadTags() {
  tags.value = await listTags()
}

// 有"处理中"的文档时，每 3 秒自动刷新状态，全部完成后停止轮询
function schedulePoll() {
  clearTimeout(pollTimer)
  if (rows.value.some((r) => r.status === 0)) {
    pollTimer = setTimeout(load, 3000)
  }
}

function goPreview(id) {
  router.push('/preview/' + id)
}

async function doDelete(id) {
  await deleteDoc(id)
  ElMessage.success('已删除')
  load()
  loadTags()
}

async function doReindex() {
  reindexing.value = true
  try {
    const count = await reindexDocs()
    ElMessage.success(`已开始重建 ${count} 篇文档的索引`)
    load()
  } finally {
    reindexing.value = false
  }
}

function typeLabel(type) {
  return { md: 'MD', txt: 'TXT', pdf: 'PDF', image: '图片' }[type] || type
}

function humanSize(size) {
  const n = Number(size) || 0
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

onMounted(() => {
  load()
  loadTags()
})

onUnmounted(() => clearTimeout(pollTimer))
</script>

<style scoped>
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.empty {
  color: #c0c4cc;
}
</style>
