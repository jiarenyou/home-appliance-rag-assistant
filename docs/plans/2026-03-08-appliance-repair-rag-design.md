# 家电维修知识智能客服系统设计文档

**日期**: 2026-03-08
**项目**: home-appliance-rag-assistant
**作者**: Claude

---

## 1. 项目概述

基于 Spring AI 框架的 RAG（检索增强生成）智能客服系统，面向家电维修技术人员，通过上传维修手册、技术文档等资料，构建私有知识库，提供精准的维修指导和故障诊断建议。

### 核心目标
- 帮助维修技术人员快速获取维修步骤和故障诊断信息
- 支持上传维修手册 PDF/Markdown 文档自动构建知识库
- 提供自然语言问答接口，返回结构化的维修指导

### 技术选型
- **后端**: Spring Boot 3.x + Spring AI + DeepSeek + PostgreSQL (pgvector) + MyBatis Plus
- **前端**: Vue 3 + Vite + Ant Design Vue + Tailwind CSS
- **部署**: 云端部署

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                         前端 (Vue 3)                         │
│  ┌──────────────┐  ┌─────────────────────────────────────┐  │
│  │ 文档管理面板  │  │         聊天主界面                   │  │
│  │ - 上传文档   │  │  - 模型选择                         │  │
│  │ - 文档列表   │  │  - 消息列表 (Markdown渲染)          │  │
│  │ - 状态显示   │  │  - 输入框                           │  │
│  └──────────────┘  └─────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │ SSE / HTTP
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      后端 (Spring Boot)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ChatController│  │DocController │  │DocumentProcessor │   │
│  │  - SSE接口   │  │  - 上传接口  │  │  - PDF解析       │   │
│  │  - 流式输出  │  │  - 删除接口  │  │  - Markdown解析  │   │
│  └──────────────┘  └──────────────┘  │  - 文本分块      │   │
│         │                 │          └──────────────────┘   │
│         ▼                 ▼                  │               │
│  ┌──────────────┐  ┌──────────────┐         │               │
│  │  RAGAdvisor  │  │ VectorStore  │         │               │
│  │  - 检索知识  │  │  - pgvector  │◄────────┘               │
│  │  - 构建提示  │  │  - 相似度检索│                         │
│  └──────────────┘  └──────────────┘                         │
│         │                 │                                  │
│         ▼                 ▼                                  │
│  ┌──────────────┐  ┌──────────────┐                         │
│  │ DeepSeek LLM │  │ PostgreSQL   │                         │
│  └──────────────┘  └──────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 数据模型

### 3.1 核心表设计

```sql
-- 知识库文档表
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,  -- 'pdf', 'markdown'
    file_size BIGINT NOT NULL,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',  -- UPLOADED, PARSING, VECTORIZING, READY, ERROR
    error_message TEXT,
    file_path VARCHAR(500)
);

-- 向量存储表 (pgvector)
CREATE TABLE vector_embeddings (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_text TEXT NOT NULL,
    embedding vector(1024) NOT NULL,  -- 根据embedding模型维度调整
    metadata JSONB,  -- 存储章节标题、页码、错误码、家电类型、品牌等
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引
CREATE INDEX ON vector_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- (可选) 会话表 - 用于记录维修历史
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- (可选) 消息表 - 用于审计和优化
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,  -- 'user', 'assistant', 'system'
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2 文档状态流转

```
UPLOADED → PARSING → VECTORIZING → READY
    ↓         ↓          ↓
   ERROR ←───┴──────────┘
```

---

## 4. 核心组件设计

### 4.1 DocumentProcessor

**职责**: 文档解析、分块、向量化

**支持的格式**:
- PDF: 使用 Apache PDFBox
- Markdown: 使用 flexmark-java

**分块策略**:
- 按语义边界分割（段落、章节）
- 每个chunk 保留章节上下文（带标题前缀）
- chunk 大小: ~500 tokens, 重叠 50 tokens
- 保留图片引用和表格结构

**图片处理**:
- 提取图片并保存到文件系统
- 在文本中插入图片引用标记 `![图片描述](path)`

**表格处理**:
- PDF 表格转换为 Markdown 表格格式

### 4.2 VectorStore

**职责**: 向量存储和检索

**实现**: Spring AI 的 `PgVectorStore`

**配置**:
- 嵌入模型: DeepSeek Embedding 或其他中文 embedding 模型
- 相似度计算: 余弦相似度
- 检索参数: Top-K (默认 K=5)

### 4.3 RAGAdvisor

**职责**: Spring AI Advisor，注入相关知识到 LLM 上下文

**流程**:
1. 接收用户问题
2. 问题向量化
3. 调用 VectorStore 相似度检索 Top-K
4. 构建提示词模板:
   ```
   ## 角色
   你是一位专业的家电维修技术专家助手。

   ## 参考知识
   {检索到的知识块}

   ## 用户问题
   {用户问题}

   请根据参考知识，提供清晰、详细的维修指导步骤。
   ```

### 4.4 ChatController

**职责**: 聊天接口，SSE 流式输出

**接口**:
- `POST /api/chat/stream` - SSE 流式接口
- `POST /api/chat` - 普通接口（可选）

**功能**:
- 支持多轮对话，注入历史消息
- 流式返回，逐字推送
- 错误处理和重连机制

### 4.5 DocController

**职责**: 文档管理接口

**接口**:
- `POST /api/documents/upload` - 上传文档
- `GET /api/documents` - 获取文档列表
- `DELETE /api/documents/{id}` - 删除文档
- `GET /api/documents/{id}/status` - 获取处理状态

---

## 5. 数据流设计

### 5.1 文档上传流程

```
用户上传文件
    ↓
保存文件到本地/对象存储
    ↓
创建 documents 记录 (status=UPLOADED)
    ↓
发布 DocumentUploadEvent (Spring Event)
    ↓
@Async DocumentProcessor 监听事件
    ↓
解析文档 → 提取文本
    ↓
文本分块 → 保留章节上下文
    ↓
调用 Embedding API → 向量化
    ↓
批量存储到 vector_embeddings
    ↓
更新 documents.status = READY
    ↓ (异常)
更新 documents.status = ERROR, 记录 error_message
```

### 5.2 问答流程

```
用户提问
    ↓
ChatController 接收请求
    ↓
问题向量化
    ↓
VectorStore.cosineSimilaritySearch(query, k=5)
    ↓
构建提示词 (系统角色 + 检索知识 + 用户问题)
    ↓
调用 DeepSeek LLM (stream=true)
    ↓
SSE 流式推送到前端
    ↓
前端逐字渲染 Markdown
```

---

## 6. 前端设计

### 6.1 页面布局

**左侧面板** (可折叠):
- 文档上传区域 (拖拽/点击上传)
- 文档列表 (文件名、大小、状态、上传时间)
- 删除按钮

**主区域**:
- 顶部工具栏: 模型选择、参数调整 (可选)
- 消息列表: 用户/AI 消息，Markdown 渲染
- 输入框: 多行支持，快捷键发送

### 6.2 功能特性

- SSE 流式输出，逐字显示
- Markdown 渲染 (支持代码块、表格、列表)
- 复制消息、重新生成
- 响应式设计，支持移动端

### 6.3 技术实现

- **状态管理**: Pinia (模型选择、搜索开关等)
- **HTTP 客户端**: Axios
- **SSE**: `@microsoft/fetch-event-source`
- **UI 组件**: Ant Design Vue + Tailwind CSS

---

## 7. 错误处理

### 7.1 文档处理异常

| 场景 | 处理方式 |
|------|---------|
| 文件格式不支持 | 返回明确错误，提示支持的格式 |
| 解析失败 | 标记 status=ERROR，记录日志 |
| 向量化失败 | 重试3次，失败后标记 ERROR |

### 7.2 问答异常

| 场景 | 处理方式 |
|------|---------|
| 未检索到相关内容 | 提示用户知识库无相关信息 |
| LLM 调用失败 | 返回友好错误提示 |
| SSE 连接中断 | 前端提示用户重试 |

### 7.3 全局异常处理

- `@RestControllerAdvice` 统一处理
- 返回格式: `{ code, message, data }`
- 敏感信息脱敏

---

## 8. 测试策略

### 8.1 单元测试
- DocumentProcessor: 各种文件格式解析和分块逻辑
- RAGAdvisor: 提示词构建逻辑 (Mock VectorStore)
- 工具类测试

### 8.2 集成测试
- 完整文档上传流程
- 问答接口 (真实 PostgreSQL + Embedding API)
- SSE 流式输出测试

### 8.3 功能测试
- 上传真实家电维修手册 PDF
- 测试各类问题: 故障诊断、零件更换、错误码查询
- 验证返回内容准确性

### 8.4 性能测试
- 大文档处理 (50MB+ PDF)
- 并发问答请求
- 向量检索性能

---

## 9. MVP 范围

**Phase 1 - 核心功能**:
- ✅ PDF 和 Markdown 文档上传
- ✅ 文档解析和向量化
- ✅ 基础问答功能
- ✅ SSE 流式输出
- ✅ 前端聊天界面

**Phase 2 - 增强功能** (后续迭代):
- 多用户/多租户支持
- 对话历史记录
- 元数据过滤 (按品牌/家电类型筛选)
- 文档版本管理
- 数据分析和优化建议

---

## 10. 技术依赖

### 后端
```gradle
// Spring Boot & Spring AI
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter'
implementation 'org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter'

// 数据库
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'com.baomidou:mybatis-plus-spring-boot3-starter'
implementation 'org.postgresql:postgresql'

// 文档解析
implementation 'org.apache.pdfbox:pdfbox:2.0.30'
implementation 'com.vladsch.flexmark:flexmark-all:0.64.8'

// 其他
implementation 'com.squareup.okhttp3:okhttp'
compileOnly 'org.projectlombok:lombok'
```

### 前端
```json
{
  "dependencies": {
    "vue": "^3.4.0",
    "ant-design-vue": "^4.1.0",
    "@microsoft/fetch-event-source": "^2.0.1",
    "axios": "^1.6.0",
    "pinia": "^2.1.0",
    "markdown-it": "^14.0.0",
    "tailwindcss": "^3.4.0"
  }
}
```

---

## 11. 配置示例

### application.yml
```yaml
spring:
  application:
    name: appliance-repair-rag
  datasource:
    url: jdbc:postgresql://localhost:5432/appliance_repair
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
      embedding:
        options:
          model: deepseek-embedding

spring.ai.vectorstore.pgvector:
  index-type: IVFFLAT
  dimension: 1024

# 文件上传配置
file:
  upload-dir: ${UPLOAD_DIR:/data/uploads}
  max-size: 50MB
```

---

**文档版本**: 1.0
**最后更新**: 2026-03-08
