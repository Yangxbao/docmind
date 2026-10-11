# DocMind 智档 · 接口文档

- 基础地址：`http://localhost:8080`
- 统一返回体：`{ "code": 200, "msg": "ok", "data": ... }`；`code != 200` 时 `data` 为 null
- 除「认证」模块外，所有接口需携带请求头：`Authorization: Bearer <token>`
- 在线调试：启动后端后访问 http://localhost:8080/swagger-ui/index.html

---

## 1. 认证模块 `/api/auth`

### 1.1 注册 `POST /api/auth/register`

```json
// 请求
{ "username": "zhangsan", "password": "123456", "nickname": "张三" }
// 响应 data：null（注册成功直接登录）
```

校验：用户名 4~20 位且唯一；密码 6~32 位。

### 1.2 登录 `POST /api/auth/login`

```json
// 请求
{ "username": "admin", "password": "admin123" }
// 响应 data
{ "token": "eyJhbGci...", "userId": 1, "username": "admin", "nickname": "管理员" }
```

### 1.3 退出 `POST /api/auth/logout`

请求头带 token 即可，body 为空。删除 Redis 登录态，旧 token 立即失效。

---

## 2. 文档模块 `/api/doc`

### 2.1 上传文档 `POST /api/doc/upload`

`multipart/form-data`，字段名 `files`，可多个（文件夹导入即一次传多个文件）。

```json
// 响应 data：每个文件一条独立结果
[
  { "fileName": "笔记.md",   "success": true,  "message": "已提交，后台解析中", "docId": 1 },
  { "fileName": "报告.pdf",  "success": false, "message": "内容与已有文档重复，已跳过", "docId": null }
]
```

说明：支持 md/txt/pdf/png/jpg/jpeg/bmp/webp，单文件 ≤ 50MB；上传接口**立即返回**，
解析/向量化/打标签由后台异步流水线完成，通过 2.2 列表或 2.3 详情轮询状态（0 处理中 / 1 完成 / 2 失败）。

### 2.2 分页查询 `GET /api/doc/page`

| 参数 | 说明 |
|---|---|
| pageNum / pageSize | 页码（默认 1）/ 每页（默认 10，最大 50） |
| keyword | 模糊匹配文件名或正文内容 |
| type | all / md / txt / pdf / image |
| tagId | 按标签筛选 |

```json
// 响应 data
{
  "records": [{
    "id": 1, "fileName": "向量检索笔记.md", "fileType": "md",
    "fileSize": 8421, "summary": "向量检索的核心是把文本变成向量…",
    "status": 1, "failReason": "",
    "createTime": "2026-10-11 20:15", "tags": ["笔记", "方案设计", "向量"]
  }],
  "total": 42, "current": 1, "size": 10
}
```

### 2.3 文档详情 `GET /api/doc/{id}` → data 结构同列表内单个对象

### 2.4 修改标签 `PUT /api/doc/{id}/tags`

```json
// 请求（传入的列表整体替换旧标签，空数组即清空）
{ "tags": ["会议", "2026Q4", "重要"] }
```

### 2.5 删除文档 `DELETE /api/doc/{id}`

逻辑删除文档 + 物理删除分块与磁盘文件，并使内存向量索引失效。

### 2.6 重建索引 `POST /api/doc/reindex`

切换向量模型（tfidf ↔ ollama）后必须调用。清空该用户全部向量并重新走解析流水线。
响应 data 为重建的文档数量。

### 2.7 文件预览/下载 `GET /api/doc/{id}/file?download=false`

- `download=false`（默认）：`Content-Disposition: inline`，按类型返回 `application/pdf`、`image/png`、`text/plain;charset=UTF-8` 等
- `download=true`：浏览器直接下载
- 前端预览页用 axios 以 blob 方式拉取（自动携带 token）

---

## 3. 检索模块 `/api/search`

### 3.1 智能检索 `POST /api/search`

```json
// 请求
{ "query": "向量检索是怎么实现的", "mode": "semantic" }
// mode: "semantic" 语义检索（默认） | "keyword" 关键词检索
```

```json
// 响应 data（按相关度降序，最多 10 条）
[{
  "docId": 1,
  "fileName": "向量检索笔记.md",
  "fileType": "md",
  "summary": "向量检索的核心是把文本变成向量…",
  "tags": ["笔记", "向量"],
  "score": 0.872,          // 语义模式的余弦相似度(0~1)；keyword 模式为 null
  "snippet": "把文本切成块后逐块向量化，检索时计算余弦相似度…",   // 命中片段，前端做高亮
  "createTime": "2026-10-11 20:15"
}]
```

语义检索链路：query 向量化 → 内存索引全量余弦 Top50 块 → 按文档聚合（文档得分 = 最高分块）→ 过滤相似度 < 0.10 → Top10。
同一查询 5 分钟内命中 Redis 缓存直接返回。

### 3.2 搜索历史 `GET /api/search/history`

```json
// 响应 data：按关键词去重，最近 8 条
[{ "id": 9, "keyword": "向量检索", "createTime": "2026-10-11 20:16" }]
```

---

## 4. 标签模块 `/api/tag`

### 4.1 我的标签列表 `GET /api/tag/list`

```json
// 响应 data
[{ "id": 1, "name": "笔记" }, { "id": 2, "name": "会议" }]
```

标签来源：后台解析自动生成（规则分类 + jieba 词频）+ 预览页手动添加。

---

## 5. 错误码约定

| code | 含义 | 常见场景 |
|---|---|---|
| 200 | 成功 | — |
| 400 | 业务/参数错误 | 用户名已存在、文档不存在、参数校验失败 |
| 401 | 未登录/登录过期 | token 缺失、篡改、过期、已注销（前端自动跳登录页） |
| 500 | 系统异常 | 后端日志 `系统异常` 关键字排查 |
