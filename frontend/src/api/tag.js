import request from './request'

/** 当前用户的标签列表 */
export const listTags = () => request.get('/tag/list')
