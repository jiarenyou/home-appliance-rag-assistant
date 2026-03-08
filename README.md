# 家电维修知识智能客服系统

基于 Spring AI + DeepSeek + pgvector 的 RAG 智能客服系统，支持家电维修手册上传和智能问答。

## 系统架构

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│   Vue 3 前端     │────▶│  Spring Boot    │────▶│  PostgreSQL +   │
│   (聊天界面)     │ SSE │   后端服务      │     │   pgvector      │
│                 │     │                 │     │   向量数据库      │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌─────────────────┐
                        │  DeepSeek API   │
                        │  (大模型服务)    │
                        └─────────────────┘
```

## 主要功能

- 📄 **文档上传**: 支持 PDF 和 Markdown 格式的维修手册上传
- 🔄 **自动处理**: 文档自动解析、分块、向量化
- 🤖 **智能问答**: 基于上传文档的 RAG 问答，支持流式输出
- 💾 **向量检索**: 使用 pgvector 实现高效的语义相似度搜索
- 📊 **会话管理**: 支持多轮对话，保留上下文
- 🎨 **现代化界面**: 基于 Vue 3 + Ant Design 的友好界面

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.1
- **语言**: Java 21
- **AI框架**: Spring AI 1.0.0-M4
- **大模型**: DeepSeek (Chat/Embedding)
- **数据库**: PostgreSQL 16 + pgvector
- **ORM**: Spring Data JPA + Hibernate
- **文档解析**: Apache PDFBox 3.0.1
- **构建工具**: Gradle 8.5

### 前端
- **框架**: Vue 3.4
- **UI组件**: Ant Design Vue 4.1
- **状态管理**: Pinia
- **路由**: Vue Router 4.2
- **构建工具**: Vite 5.0
- **样式**: Tailwind CSS 3.4
- **HTTP客户端**: Axios + fetch-event-source

## 快速开始

### 前置要求

- JDK 21+
- Node.js 20+
- PostgreSQL 16+ with pgvector extension
- DeepSeek API Key

### 使用 Docker 启动 (推荐)

```bash
# 1. 克隆项目
git clone <repository-url>
cd home-appliance-rag-assistant

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，填入你的 DEEPSEEK_API_KEY

# 3. 启动所有服务
docker-compose up -d

# 4. 查看日志
docker-compose logs -f

# 5. 访问应用
# 前端: http://localhost:3000
# 后端 API: http://localhost:8080
```

### 本地开发

#### 1. 数据库设置

```bash
# 创建数据库
createdb appliance_repair

# 启用 pgvector 扩展
psql appliance_repair -c "CREATE EXTENSION vector;"
```

#### 2. 启动后端

```bash
cd backend
./gradlew bootRun
```

#### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

#### 4. 访问应用

打开浏览器访问 http://localhost:3000

## 项目结构

```
home-appliance-rag-assistant/
├── backend/                          # 后端服务
│   ├── src/main/java/com/appliance/repair/
│   │   ├── advisor/                  # RAG 增强器
│   │   ├── common/                   # 通用响应类
│   │   ├── config/                   # 配置类
│   │   ├── controller/               # REST 控制器
│   │   │   ├── ChatController.java   # 聊天接口
│   │   │   └── DocumentController.java # 文档管理接口
│   │   ├── dto/                      # 数据传输对象
│   │   ├── entity/                   # JPA 实体
│   │   ├── event/                    # 事件和监听器
│   │   ├── exception/                # 异常处理
│   │   ├── parser/                   # 文档解析器
│   │   ├── repository/               # 数据访问层
│   │   ├── service/                  # 业务逻辑
│   │   └── util/                     # 工具类
│   ├── src/main/resources/
│   │   ├── application.yml           # 应用配置
│   │   └── db/migration/             # 数据库迁移脚本
│   ├── build.gradle                  # Gradle 构建配置
│   └── Dockerfile                    # 后端 Docker 镜像
│
├── frontend/                         # 前端应用
│   ├── src/
│   │   ├── views/
│   │   │   └── Home.vue              # 主页面（聊天界面）
│   │   ├── router/                   # 路由配置
│   │   ├── App.vue                   # 根组件
│   │   └── main.js                   # 入口文件
│   ├── package.json                  # NPM 依赖
│   ├── vite.config.js               # Vite 配置
│   ├── tailwind.config.js           # Tailwind CSS 配置
│   ├── Dockerfile                    # 前端 Docker 镜像
│   └── nginx.conf                    # Nginx 配置
│
├── docs/                             # 文档
│   ├── DEPLOYMENT.md                 # 部署指南
│   └── plans/                        # 实施计划
│
├── docker-compose.yml                # Docker Compose 编排
├── .env.example                      # 环境变量示例
└── README.md                         # 本文件
```

## API 接口文档

### 文档管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/documents` | 获取所有文档列表 |
| POST | `/api/documents/upload` | 上传文档 |
| DELETE | `/api/documents/{id}` | 删除文档 |
| GET | `/api/documents/{id}/status` | 获取文档处理状态 |

### 聊天服务

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/stream` | SSE 流式聊天 |
| GET | `/api/chat/conversations` | 获取所有会话 |
| GET | `/api/chat/conversations/{id}/messages` | 获取会话消息 |

### 请求示例

```bash
# 上传文档
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@manual.pdf"

# 发送聊天消息
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"洗衣机显示E3错误代码怎么办？","topK":5}'
```

## 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_USERNAME` | 数据库用户名 | `postgres` |
| `DB_PASSWORD` | 数据库密码 | `postgres` |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | - |
| `UPLOAD_DIR` | 文件上传目录 | `./data/uploads` |

## 开发指南

### 添加新的文档解析器

1. 在 `backend/src/main/java/com/appliance/repair/parser/` 创建新的解析器类
2. 实现 `parse()` 和 `parseFromFile()` 方法
3. 在 `VectorStoreService` 中添加对应的处理逻辑

### 修改 RAG 提示词

编辑 `backend/src/main/java/com/appliance/repair/service/ChatService.java` 中的 `buildSystemPrompt()` 方法。

### 自定义分块策略

修改 `backend/src/main/java/com/appliance/repair/config/DocumentChunkConfig.java` 配置：
- `chunkSize`: 分块大小
- `chunkOverlap`: 分块重叠
- `maxChunkSize`: 最大分块大小

## 部署

详细部署指南请参考 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

### Docker 部署

```bash
docker-compose up -d
```

### 云端部署

支持部署到：
- 阿里云 ECS + RDS PostgreSQL
- 腾讯云 CVM + 云数据库
- AWS EC2 + RDS

## 故障排查

### 文档上传失败

```bash
# 检查上传目录权限
chmod -R 755 ./data/uploads
```

### 向量化失败

1. 检查 DeepSeek API Key 是否正确
2. 查看后端日志: `docker-compose logs -f backend`
3. 确认 pgvector 扩展已启用: `psql appliance_repair -c "SELECT * FROM pg_extension;"`

### SSE 连接中断

1. 检查 Nginx 配置中的 `proxy_buffering` 设置
2. 确认防火墙允许长连接

## 许可证

MIT License

## 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - Spring AI 框架
- [DeepSeek](https://www.deepseek.com/) - 大语言模型 API
- [pgvector](https://github.com/pgvector/pgvector) - PostgreSQL 向量扩展
- [Ant Design Vue](https://antdv.com/) - Vue UI 组件库
