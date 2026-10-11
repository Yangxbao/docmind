import request from './request'

/**
 * 智能检索
 * @param {{query: string, mode: 'semantic'|'keyword'}} data
 */
export const search = (data) => request.post('/search', data)

/** 搜索历史（去重后最近 8 条） */
export const searchHistory = () => request.get('/search/history')
