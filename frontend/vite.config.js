import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发模式下把 /api 请求代理到后端 8080 端口，前端无需关心跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 产物直接输出到后端静态资源目录：
    // 执行 npm run build 后，后端 mvn package 打出的 jar 就能同时提供页面和接口（单 jar 部署）
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true
  }
})
