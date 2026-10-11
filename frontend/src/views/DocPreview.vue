<template>
  <div v-loading="loading">
    <el-page-header class="mb12" @back="goBack">
      <template #content>
        <span class="doc-title">{{ doc?.fileName }}</span>
      </template>
    </el-page-header>

    <el-alert
      v-if="doc && doc.status === 0"
      type="warning"
      :closable="false"
      class="mb12"
      title="该文档正在后台解析中，可检索内容和标签尚未生成，稍后刷新可查看。"
    />

    <el-row :gutter="12">
      <!-- 左侧：文件内容预览 -->
      <el-col :span="17">
        <el-card shadow="never" class="preview-card">
          <!-- Markdown：渲染成 HTML -->
          <div v-if="fileType === 'md' && renderedHtml" class="md-body" v-html="renderedHtml"></div>
          <!-- TXT：等宽字体展示 -->
          <pre v-else-if="fileType === 'txt' && textContent" class="txt-body">{{ textContent }}</pre>
          <!-- PDF：iframe 内嵌（blob URL 不需要 token，规避了 iframe 无法带请求头的问题） -->
          <iframe v-else-if="fileType === 'pdf' && blobUrl" :src="blobUrl" class="pdf-frame"></iframe>
          <!-- 图片 -->
          <img v-else-if="fileType === 'image' && blobUrl" :src="blobUrl" class="img-view" />
          <el-empty v-else-if="!loading" description="暂无可预览内容" />
        </el-card>
      </el-col>

      <!-- 右侧：文档信息 + 标签编辑 -->
      <el-col :span="7">
        <el-card shadow="never">
          <template #header>文档信息</template>
          <p class="meta"><b>类型：</b>{{ typeLabel(fileType) }}</p>
          <p class="meta"><b>大小：</b>{{ humanSize(doc?.fileSize) }}</p>
          <p class="meta"><b>上传时间：</b>{{ doc?.createTime || '-' }}</p>
          <p class="meta">
            <b>状态：</b>
            <el-tag size="small" :type="statusType(doc?.status)">{{ statusText(doc?.status) }}</el-tag>
          </p>
          <p class="meta"><b>摘要：</b>{{ doc?.summary || '无' }}</p>

          <p class="meta"><b>标签（可手动编辑）：</b></p>
          <el-select
            v-model="tagNames"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入新标签后回车"
            style="width: 100%"
          >
            <el-option v-for="t in allTags" :key="t.id" :label="t.name" :value="t.name" />
          </el-select>
          <el-button type="primary" class="mt12" :loading="savingTags" @click="saveTags">
            保存标签
          </el-button>

          <el-divider />
          <el-button link type="primary" @click="download">下载原文件</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { getDoc, getDocFile, updateDocTags } from '../api/doc'
import { listTags } from '../api/tag'

const route = useRoute()
const router = useRouter()
const md = new MarkdownIt({ html: false, linkify: true })

const docId = route.params.id
const doc = ref(null)
const fileType = ref('')
const loading = ref(true)

const renderedHtml = ref('')
const textContent = ref('')
const blobUrl = ref('')

const allTags = ref([])
const tagNames = ref([])
const savingTags = ref(false)

async function load() {
  loading.value = true
  try {
    // 1. 文档元信息 + 已有标签
    doc.value = await getDoc(docId)
    fileType.value = doc.value.fileType
    tagNames.value = [...(doc.value.tags || [])]
    allTags.value = await listTags()

    // 2. 拉取原始文件二进制（axios 自动带 token）
    const resp = await getDocFile(docId)
    const blob = resp.data
    if (fileType.value === 'md') {
      const raw = await blob.text()
      renderedHtml.value = md.render(raw)
    } else if (fileType.value === 'txt') {
      textContent.value = await blob.text()
    } else {
      // pdf / image：转 blob URL 给 iframe / img 使用
      blobUrl.value = URL.createObjectURL(blob)
    }
  } finally {
    loading.value = false
  }
}

async function saveTags() {
  savingTags.value = true
  try {
    await updateDocTags(docId, tagNames.value)
    ElMessage.success('标签已保存')
  } finally {
    savingTags.value = false
  }
}

function download() {
  // 触发浏览器下载：把预览用的 blob 转成下载链接
  const a = document.createElement('a')
  a.href = blobUrl.value || URL.createObjectURL(new Blob([textContent.value || renderedHtml.value]))
  a.download = doc.value?.fileName || 'file'
  a.click()
}

function goBack() {
  router.push('/docs')
}

function typeLabel(type) {
  return { md: 'Markdown', txt: 'TXT', pdf: 'PDF', image: '图片' }[type] || type || '-'
}

function humanSize(size) {
  const n = Number(size) || 0
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

function statusText(status) {
  return { 0: '处理中', 1: '已完成', 2: '失败' }[status] || '-'
}

function statusType(status) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[status] || 'info'
}

onMounted(load)

// 离开页面时释放 blob URL，避免内存泄漏
onUnmounted(() => {
  if (blobUrl.value) URL.revokeObjectURL(blobUrl.value)
})
</script>

<style scoped>
.doc-title {
  font-size: 16px;
  font-weight: 600;
}

.preview-card {
  min-height: 60vh;
}

.pdf-frame {
  width: 100%;
  height: 75vh;
  border: none;
}

.img-view {
  max-width: 100%;
  max-height: 75vh;
  display: block;
  margin: 0 auto;
}

.txt-body {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-family: Consolas, Monaco, monospace;
}

.meta {
  margin: 6px 0;
  font-size: 13px;
  color: #606266;
  word-break: break-all;
}
</style>
