# DocMind 智档 · 部署文档

## 一、环境要求

| 组件 | 版本要求 | 必须 | 说明 |
|---|---|---|---|
| JDK | 17 及以上 | ✅ | 后端运行环境 |
| Maven | 3.8+ | ✅ | 后端构建 |
| Node.js | 18+ | 开发时✅ | 前端构建（单 jar 部署时构建完可卸） |
| MySQL | 8.x | ✅ | 业务数据存储 |
| Redis | 5+ | 可选 | 登录态管理 + 检索缓存；**没装也能跑（自动降级）** |
| Ollama | 最新版 | 可选 | 真语义向量化；默认用内置词频向量，不需要 |
| Tesseract 语言包 | chi_sim | 可选 | 图片/扫描 PDF 的 OCR；不装则跳过识别 |

> Windows 下的 MySQL/Redis 推荐直接用项目自带的 `docker-compose.yml`：`docker compose up -d`。
> 不想用 Docker 也可分别安装：MySQL 官网安装包 + Redis（Windows 可用 [Memurai](https://www.memurai.com/) 或 WSL）。

## 二、快速启动（开发模式）

### 1. 初始化数据库

方式 A（命令行）：

```bash
mysql -uroot -p < sql/init.sql
```

方式 B（docker-compose）：`docker compose up -d` 首次启动会自动执行 `sql/` 下的脚本。

方式 C：用 Navicat/DataGrip 等工具直接执行 `sql/init.sql`。

### 2. 修改后端配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    username: root
    password: "你的MySQL密码"     # ← 必改
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

看到 `已自动创建测试账号：admin / admin123` 即启动成功。
接口文档：http://localhost:8080/swagger-ui/index.html

### 4. 启动前端

```bash
cd frontend
npm install        # 首次执行
npm run dev
```

打开 http://localhost:5173 ，用 **admin / admin123** 登录。

## 三、单 jar 部署（生产/演示推荐）

前端构建产物直接输出到后端静态资源目录，最终得到一个 jar：

```bash
# 1. 构建前端（产物输出到 backend/src/main/resources/static）
cd frontend && npm install && npm run build

# 2. 打包后端
cd ../backend && mvn clean package -DskipTests

# 3. 运行（首次运行会自动在工作目录创建 data/files 存储上传的文档）
java -jar target/docmind-backend-1.0.0.jar
```

访问 http://localhost:8080 即可（前后端同一个服务，无跨域问题）。

## 四、可选功能配置

### 1. 真语义检索（Ollama）

```bash
# 1) 安装 Ollama：https://ollama.com 下载 Windows 版一键安装
# 2) 拉取中文语义模型（约 1.2GB，一次性）
ollama pull bge-m3
# 3) 确认服务已启动（安装后默认自启）
curl http://localhost:11434/api/tags
```

修改 `application.yml`：

```yaml
docmind:
  embedding:
    provider: ollama    # tfidf → ollama
```

重启后端，并到前端"文档库"页面点击 **重建索引**（旧向量与新模型不在同一向量空间，必须重建）。

### 2. 图片 / 扫描 PDF 的 OCR

1. 下载 Tesseract 中文语言包：
   - [chi_sim.traineddata](https://github.com/tesseract-ocr/tessdata_best/raw/main/chi_sim.traineddata)（精度优先，推荐）
   - [eng.traineddata](https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata)（英文，可选）
2. 放到 `backend/tessdata/` 目录（与 application.yml 中 `docmind.ocr.tessdata` 对应；jar 运行时放在 jar 所在目录下的 tessdata 文件夹）。
3. 重启后端即可。未配置时**不影响其他功能**，图片/扫描 PDF 只是提取不到文本（日志有提示）。

> Windows 下 tess4j 需要系统装有 [Microsoft Visual C++ Redistributable](https://aka.ms/vs/17/release/vc_redist.x64.exe)，一般系统自带；若 OCR 报找不到 DLL，安装后重试。

### 3. Redis

有 Redis 时自动使用（登录态 + 检索缓存）；连接失败只打一条警告并降级，无需任何配置。

## 五、目录与数据说明

| 路径 | 内容 |
|---|---|
| `backend/data/files/{userId}/` | 上传的原始文档（磁盘存储，随运行目录生成） |
| `backend/tessdata/` | OCR 语言包（需手动放入） |
| MySQL `docmind` 库 | 用户/文档/分块向量/标签/搜索历史 |

## 六、常见问题（FAQ）

| 现象 | 原因与解决 |
|---|---|
| 启动报数据库连接失败 | MySQL 未启动 / 密码不对 / 未执行 init.sql |
| 登录报"网络异常" | 后端没启动，或端口被占用（改 `server.port` 后同步改 vite 代理） |
| 文档状态一直是"处理中" | 查看后端日志 `doc-process-*` 线程的报错；扫描大 PDF OCR 较慢属正常 |
| 文档状态"失败：本地向量服务(Ollama)调用失败" | 切到了 ollama 模式但 Ollama 未启动或未 pull 模型；启动 Ollama 或改回 tfidf 并重建索引 |
| 图片文档提取不到文本 | 未配置 OCR 语言包，见"四-2"，配置后删除重传或重建索引 |
| 语义检索结果不好 | 默认是词级相似度；按"四-1"开启 Ollama bge-m3 后重建索引 |
| 端口占用 | 后端改 `server.port`；前端改 `vite.config.js` 的 `server.port` |
