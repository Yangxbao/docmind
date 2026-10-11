import request from './request'

/** 批量上传文档（FormData，字段名必须为 files） */
export const uploadDocs = (formData) =>
  request.post('/doc/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 文件夹批量导入可能很大，给足超时时间
    timeout: 600000
  })

/** 分页查询文档列表 */
export const pageDocs = (params) => request.get('/doc/page', { params })

/** 文档详情 */
export const getDoc = (id) => request.get(`/doc/${id}`)

/** 修改文档标签（整体替换） */
export const updateDocTags = (id, tags) => request.put(`/doc/${id}/tags`, { tags })

/** 删除文档 */
export const deleteDoc = (id) => request.delete(`/doc/${id}`)

/** 重建向量索引（切换向量模型后使用） */
export const reindexDocs = () => request.post('/doc/reindex')

/**
 * 拉取文档原始文件（blob），用于预览页：
 * - md/txt：blob.text() 拿文本
 * - pdf：URL.createObjectURL 后放进 <iframe>
 * - image：URL.createObjectURL 后放进 <img>
 */
export const getDocFile = (id) => request.get(`/doc/${id}/file`, { responseType: 'blob' })
