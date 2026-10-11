<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <h2 class="title">📚 DocMind 智档</h2>
      <p class="subtitle">私有化个人文档智能归档检索平台 · 数据不出本机</p>

      <el-tabs v-model="tab" stretch>
        <!-- 登录 -->
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" size="large">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="密码"
                :prefix-icon="Lock"
                @keyup.enter="doLogin"
              />
            </el-form-item>
            <el-button
              type="primary"
              style="width: 100%"
              :loading="loading"
              @click="doLogin"
            >
              登 录
            </el-button>
            <p class="tip">首次启动已自动创建测试账号：admin / admin123</p>
          </el-form>
        </el-tab-pane>

        <!-- 注册 -->
        <el-tab-pane label="注册" name="register">
          <el-form ref="regFormRef" :model="regForm" :rules="regRules" size="large">
            <el-form-item prop="username">
              <el-input v-model="regForm.username" placeholder="用户名（4~20位）" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="regForm.password"
                type="password"
                show-password
                placeholder="密码（6~32位）"
                :prefix-icon="Lock"
              />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="regForm.nickname" placeholder="昵称（可不填）" :prefix-icon="EditPen" />
            </el-form-item>
            <el-button type="primary" style="width: 100%" :loading="loading" @click="doRegister">
              注 册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, EditPen } from '@element-plus/icons-vue'
import { login, register } from '../api/auth'
import { useUserStore } from '../store/user'

const router = useRouter()
const store = useUserStore()

const tab = ref('login')
const loading = ref(false)
const loginFormRef = ref()
const regFormRef = ref()

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', nickname: '' })

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const regRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度需在 4~20 位之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6~32 位之间', trigger: 'blur' }
  ]
}

async function doLogin() {
  await loginFormRef.value.validate()
  loading.value = true
  try {
    const data = await login(loginForm)
    store.setLogin(data.token, data.nickname)
    ElMessage.success('欢迎回来，' + data.nickname)
    router.push('/')
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  await regFormRef.value.validate()
  loading.value = true
  try {
    await register(regForm)
    ElMessage.success('注册成功，请登录')
    // 注册成功后自动带入用户名，切到登录页
    loginForm.username = regForm.username
    regForm.password = ''
    tab.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f1ff 0%, #f5f7fa 60%);
}

.login-card {
  width: 400px;
  padding: 8px 12px 20px;
}

.title {
  text-align: center;
  margin: 8px 0 4px;
}

.subtitle {
  text-align: center;
  color: #909399;
  font-size: 13px;
  margin: 0 0 12px;
}

.tip {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  margin: 12px 0 0;
}
</style>
