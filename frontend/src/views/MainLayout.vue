<template>
  <el-container class="layout">
    <!-- 左侧菜单 -->
    <el-aside width="200px" class="aside">
      <div class="logo">📚 DocMind 智档</div>
      <el-menu router :default-active="activeMenu" class="menu">
        <el-menu-item index="/docs">
          <el-icon><Document /></el-icon>
          <span>文档库</span>
        </el-menu-item>
        <el-menu-item index="/upload">
          <el-icon><Upload /></el-icon>
          <span>上传文档</span>
        </el-menu-item>
        <el-menu-item index="/search">
          <el-icon><Search /></el-icon>
          <span>智能检索</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">私有部署 · 数据本地存储</div>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <span class="page-title">{{ currentTitle }}</span>
        <div class="user-area">
          <el-icon><UserFilled /></el-icon>
          <span class="nickname">{{ store.nickname || '用户' }}</span>
          <el-button link type="danger" @click="doLogout">退出</el-button>
        </div>
      </el-header>

      <!-- 页面内容 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { logout } from '../api/auth'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 刷新页面时菜单高亮：预览页归属"文档库"
const activeMenu = computed(() => {
  if (route.path.startsWith('/preview')) return '/docs'
  return route.path
})

const titles = {
  '/docs': '文档库',
  '/upload': '上传文档',
  '/search': '智能检索'
}
const currentTitle = computed(() => {
  if (route.path.startsWith('/preview')) return '文档预览'
  return titles[route.path] || 'DocMind'
})

async function doLogout() {
  try {
    await logout() // 后端删除登录态（Redis 不可用时会自动忽略）
  } catch (ignore) {
    // 无论后端结果如何，本地都退出
  }
  store.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
}

.logo {
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
  padding: 20px 0 16px;
  letter-spacing: 1px;
}

.menu {
  border-right: none;
  background: transparent;
}

.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.65);
}

.menu :deep(.el-menu-item.is-active) {
  color: #409eff;
  background: rgba(255, 255, 255, 0.08);
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.05);
}

.aside-footer {
  margin-top: auto;
  color: rgba(255, 255, 255, 0.35);
  font-size: 12px;
  text-align: center;
  padding-bottom: 16px;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
}

.nickname {
  margin-right: 6px;
}

.main {
  padding: 16px;
}
</style>
