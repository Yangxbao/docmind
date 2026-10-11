<template>
  <div>
    <!-- 拖拽上传 -->
    <el-card shadow="never">
      <el-upload
        drag
        multiple
        :auto-upload="false"
        v-model:file-list="fileList"
        class="uploader"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把文件拖到这里，或 <em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 MD / TXT / PDF / PNG / JPG / BMP / WEBP，单个文件 ≤ 50MB；
            上传后自动在后台完成"解析 → 分块 → 向量化 → 打标签"
          </div>
        </template>
      </el-upload>

      <div class="flex-row mt12">
        <el-button @click="pickFolder">
          <el-icon class="mr4"><FolderOpened /></el-icon>整个文件夹导入
        </el-button>
        <!-- 浏览器原生目录选择：webkitdirectory 会递归带出文件夹里的所有文件 -->
        <input
          ref="folderInput"
          type="file"
          webkitdirectory
          multiple
          style="display: none"
          @change="onFolderPicked"
        />
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="totalCount === 0"
          @click="doUpload"
        >
          开始上传（{{ totalCount }} 个文件）
        </el-button>
        <el-button v-if="totalCount > 0" @click="clearAll">清空列表</el-button>
        <span v-if="skippedCount > 0" class="skip-tip">已自动忽略 {{ skippedCount }} 个不支持的文件</span>
      </div>
    </el-card>

    <!-- 上传结果 -->
    <el-card v-if="results.length" shadow="never" class="mt12">
      <template #header>本次上传结果</template>
      <el-table :data="results" size="small" max-height="360">
        <el-table-column prop="fileName" label="文件名" min-width="220" />
        <el-table-column label="结果" width="110">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'warning'" size="small">
              {{ row.success ? '已提交' : '未上传' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="200" />
      </el-table>
      <el-alert
        class="mt12"
        type="info"
        :closable="false"
        title="文件已提交后台解析（提取正文 → 分块 → 向量化 → 自动打标签）。可到「文档库」查看处理状态，解析完成的文档即可被智能检索命中。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadDocs } from '../api/doc'

const SUPPORTED = ['md', 'txt', 'pdf', 'png', 'jpg', 'jpeg', 'bmp', 'webp']

const fileList = ref([])          // el-upload 拖拽/点击选择的文件
const folderFiles = ref([])       // 文件夹导入的文件
const folderInput = ref()
const uploading = ref(false)
const results = ref([])
const skippedCount = ref(0)

const totalCount = computed(() => fileList.value.length + folderFiles.value.length)

function pickFolder() {
  folderInput.value.click()
}

function onFolderPicked(event) {
  const files = Array.from(event.target.files || [])
  folderFiles.value.push(...files)
  event.target.value = '' // 允许再次选择同一个文件夹
}

function extOf(name) {
  const i = name.lastIndexOf('.')
  return i < 0 ? '' : name.slice(i + 1).toLowerCase()
}

async function doUpload() {
  // 合并两个来源，按"文件名+大小"去重（避免拖拽和文件夹导入重复）
  const rawList = [...fileList.value.map((f) => f.raw).filter(Boolean), ...folderFiles.value]
  const seen = new Map()
  for (const f of rawList) {
    seen.set(f.name + '_' + f.size, f)
  }
  const files = [...seen.values()]
  const supported = []
  let skipped = 0
  for (const f of files) {
    if (SUPPORTED.includes(extOf(f.name))) {
      supported.push(f)
    } else {
      skipped++
    }
  }
  skippedCount.value = skipped
  if (!supported.length) {
    ElMessage.warning('没有可上传的支持文件')
    return
  }

  const formData = new FormData()
  for (const f of supported) {
    formData.append('files', f)
  }
  uploading.value = true
  try {
    results.value = await uploadDocs(formData)
    const okCount = results.value.filter((r) => r.success).length
    ElMessage.success(`已提交 ${okCount} 个文件，后台解析中`)
    clearAll()
  } finally {
    uploading.value = false
  }
}

function clearAll() {
  fileList.value = []
  folderFiles.value = []
  skippedCount.value = 0
}
</script>

<style scoped>
.uploader {
  width: 100%;
}

.skip-tip {
  color: #e6a23c;
  font-size: 13px;
}
</style>
