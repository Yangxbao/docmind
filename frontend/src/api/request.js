import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import router from '../router'

/**
 * axios 统一封装：
 *   1. baseURL 统一为 /api（开发时由 vite 代理到 8080）
 *   2. 请求拦截：自动携带 Authorization: Bearer <token>
 *   3. 响应拦截：code !== 200 统一弹错误提示；401 自动踢回登录页
 *   4. 调用方拿到的直接就是后端的 data 字段，不用每次都 .data.data
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = 'Bearer ' + store.token
  }
  return config
})

request.interceptors.response.use(
  (resp) => {
    // 文件二进制（预览/下载）：不走统一结构，原样返回整个响应
    if (resp.config.responseType === 'blob') {
      return resp
    }
    const body = resp.data
    if (body.code !== 200) {
      if (body.code === 401) {
        useUserStore().logout()
        router.push('/login')
      }
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body.data
  },
  (error) => {
    // HTTP 层错误：401 / 超时 / 网络不可达等
    if (error.response && error.response.status === 401) {
      useUserStore().logout()
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
      return Promise.reject(error)
    }
    const msg = error.code === 'ECONNABORTED'
      ? '请求超时，请稍后重试'
      : '网络异常或后端服务未启动'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
