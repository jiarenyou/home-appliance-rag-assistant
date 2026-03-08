# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RAG (Retrieval-Augmented Generation) intelligent customer service system for appliance repair knowledge. Users upload PDF/Markdown repair manuals, and the system processes them into vector embeddings for intelligent Q&A using Spring AI + DeepSeek + pgvector.

**Architecture Flow:**
1. Frontend uploads document → Backend saves to file system
2. Async event triggers: Parse document → Chunk text → Vectorize via DeepSeek embedding → Store in pgvector
3. User asks question → Search pgvector for relevant chunks → Build RAG prompt → Stream response via SSE

## Commands

### Backend (Spring Boot + Gradle)

```bash
cd backend

# Build (compilation succeeds even without DB; tests fail without PostgreSQL)
./gradlew build

# Run application (requires PostgreSQL + pgvector + DEEPSEEK_API_KEY)
./gradlew bootRun

# Run specific test
./gradlew test --tests DocumentControllerTest
./gradlew test --tests ChatControllerTest

# Clean build
./gradlew clean
```

### Frontend (Vue 3 + Vite)

```bash
cd frontend

# Install dependencies
npm install

# Dev server (proxies /api to backend:8080)
npm run dev

# Production build
npm run build

# Preview production build
npm run preview
```

### Docker

```bash
# Start all services (PostgreSQL, Backend, Frontend)
docker-compose up -d

# View logs
docker-compose logs -f backend

# Stop all services
docker-compose down

# Stop and delete volumes
docker-compose down -v
```

## Architecture

### Backend Layer Structure

- **Controller**: REST endpoints (`ChatController`, `DocumentController`)
- **Service**: Business logic (`ChatService`, `DocumentService`, `VectorStoreService`)
- **Event-Driven Processing**: `DocumentUploadEvent` → `DocumentProcessingListener` (async) → parsing and vectorization
- **Repository**: JPA repositories for entities
- **Parser**: PDF (`PdfParser` with PDFBox 3.x Loader), Markdown (`MarkdownParser`)
- **Util**: `TextChunker` for intelligent document splitting
- **Entity**: JPA entities with `@PrePersist` lifecycle callbacks

### Key Integration Points

1. **Spring AI**: Auto-configured via `application.yml` properties
   - `spring.ai.openai.api-key` → DeepSeek API key
   - `spring.ai.openai.base-url` → https://api.deepseek.com
   - VectorStore auto-configured for pgvector

2. **Document Processing Flow**:
   - `DocumentService.uploadDocument()` → saves file → publishes `DocumentUploadEvent`
   - `DocumentProcessingListener` (async) → updates status → calls `VectorStoreService.processDocumentFromFile()`
   - Parser → TextChunker → VectorStore.add() → stores embeddings in pgvector

3. **RAG Chat Flow**:
   - `ChatService.chatStream()` → builds system prompt from retrieved docs → `ChatClient.stream()`
   - System prompt includes relevant chunks from `VectorStoreService.searchSimilar()`

### Frontend Structure

- Single page application with Vue Router
- `Home.vue`: Main chat interface with document sidebar
- SSE streaming via `@microsoft/fetch-event-source`
- Markdown rendering via `markdown-it`

## Configuration Notes

- **Spring AI Version**: 1.0.0-M4 (milestone) - APIs may differ from stable versions
- **PDFBox 3.x**: Uses `Loader.loadPDF()` instead of deprecated `PDDocument.load()`
- **PostgreSQL**: Requires pgvector extension: `CREATE EXTENSION vector;`
- **SSE**: Frontend expects events: `user-message`, `content`, `done`, `error`

## Important Files

- `backend/src/main/resources/application.yml`: DeepSeek API config, database, file upload settings
- `backend/src/main/java/com/appliance/repair/service/ChatService.java`: RAG prompt in `buildSystemPrompt()`
- `backend/src/main/java/com/appliance/repair/config/DocumentChunkConfig.java`: Chunking strategy (chunkSize, chunkOverlap)
- `frontend/nginx.conf`: SSE proxy configuration (proxy_buffering off)

## Development Notes

- Tests fail without PostgreSQL running - this is expected
- Backend uses `@EnableAsync` for document processing
- Frontend dev server proxies `/api` to `http://localhost:8080`
- Vector embeddings stored in `vector_embeddings` table with `vector(1024)` type
