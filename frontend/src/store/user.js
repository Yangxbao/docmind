import { defineStore } from 'pinia'

// 用户状态：token / 昵称。同步存 localStorage，刷新页面不丢失
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('docmind_token') || '',
    nickname: localStorage.getItem('docmind_nickname') || ''
  }),
  actions: {
    setLogin(token, nickname) {
      this.token = token
      this.nickname = nickname
      localStorage.setItem('docmind_token', token)
      localStorage.setItem('docmind_nickname', nickname)
    },
    logout() {
      this.token = ''
      this.nickname = ''
      localStorage.removeItem('docmind_token')
      localStorage.removeItem('docmind_nickname')
    }
  }
})
