# 部署指南

## 本地开发

### 前置要求

- JDK 21+
- Node.js 20+
- PostgreSQL 16+ with pgvector extension
- DeepSeek API Key

### 数据库设置

```bash
# 创建数据库
createdb appliance_repair

# 启用 pgvector 扩展
psql appliance_repair
\c appliance_repair
CREATE EXTENSION vector;
```

### 后端启动

```bash
cd backend
./gradlew bootRun
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

## Docker 部署

### 快速启动

```bash
# 复制环境变量
cp .env.example .env

# 编辑 .env 文件，填入 API Key 等配置
vim .env

# 启动服务
docker-compose up -d
```

### 服务访问

- 前端: http://localhost:3000
- 后端 API: http://localhost:8080
- PostgreSQL: localhost:5432

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
```

### 停止服务

```bash
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

## 云端部署

### 推荐平台

- 阿里云 ECS + RDS PostgreSQL
- 腾讯云 CVM + 云数据库
- AWS EC2 + RDS

### 配置调整

1. 修改 `application.yml` 中的数据库连接
2. 设置环境变量 `DEEPSEEK_API_KEY`
3. 配置反向代理（Nginx）
4. 设置 HTTPS（Let's Encrypt）

## 故障排查

### 文档上传失败

检查上传目录权限：
```bash
chmod -R 755 ./data/uploads
```

### 向量化失败

1. 检查 DeepSeek API Key 是否正确
2. 查看后端日志: `docker-compose logs -f backend`
3. 确认 pgvector 扩展已启用

### SSE 连接中断

1. 检查 Nginx 配置中的 proxy_buffering 设置
2. 确认防火墙允许长连接
