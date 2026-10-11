import request from './request'

/** 注册 */
export const register = (data) => request.post('/auth/register', data)

/** 登录 */
export const login = (data) => request.post('/auth/login', data)

/** 退出登录 */
export const logout = () => request.post('/auth/logout')
