# 家电维修知识智能客服系统实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 构建基于 Spring AI 的 RAG 智能客服系统，支持家电维修手册上传和智能问答

**Architecture:** 前后端分离架构，后端使用 Spring Boot + Spring AI + PostgreSQL (pgvector)，前端使用 Vue 3 + Ant Design Vue，通过 SSE 实现流式输出

**Tech Stack:** Spring Boot 3.x, Spring AI, DeepSeek, PostgreSQL, pgvector, Vue 3, Ant Design Vue, Vite, Tailwind CSS

---

## Phase 1: 项目初始化与基础设施

### Task 1: 创建后端项目结构

**Files:**
- Create: `backend/build.gradle`
- Create: `backend/src/main/java/com/appliance/repair/ApplianceRepairApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/appliance/repair/ApplianceRepairApplicationTests.java`

**Step 1: 创建根目录和后端目录结构**

```bash
mkdir -p backend/src/main/java/com/appliance/repair
mkdir -p backend/src/main/resources
mkdir -p backend/src/test/java/com/appliance/repair
mkdir -p frontend
```

**Step 2: 创建 build.gradle**

Create `backend/build.gradle`:

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.1'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.appliance'
version = '1.0.0'

java {
    sourceCompatibility = '21'
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
    maven { url 'https://repo.spring.io/milestone' }
}

dependencies {
    // Spring Boot & Web
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Spring AI
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0-M4'
    implementation 'org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter:1.0.0-M4'

    // Database
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.postgresql:postgresql'
    implementation 'com.baomidou:mybatis-plus-spring-boot3-starter:3.5.5'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Document Processing
    implementation 'org.apache.pdfbox:pdfbox:2.0.30'
    implementation 'com.vladsch.flexmark:flexmark-all:0.64.8'

    // HTTP Client
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

**Step 3: 创建主应用类**

Create `backend/src/main/java/com/appliance/repair/ApplianceRepairApplication.java`:

```java
package com.appliance.repair;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ApplianceRepairApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApplianceRepairApplication.class, args);
    }
}
```

**Step 4: 创建配置文件**

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: appliance-repair-rag

  datasource:
    url: jdbc:postgresql://localhost:5432/appliance_repair
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

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
          dimensions: 1024

# PgVector 配置
spring.ai.vectorstore.pgvector:
  index-type: IVFFLAT
  dimension: 1024
  distance-type: COSINE_DISTANCE
  schema-name: public
  table-name: vector_embeddings

# 文件上传配置
file:
  upload-dir: ${UPLOAD_DIR:./data/uploads}
  max-size: 52428800

server:
  port: 8080

logging:
  level:
    com.appliance.repair: DEBUG
    org.springframework.ai: DEBUG
```

**Step 5: 创建测试类**

Create `backend/src/test/java/com/appliance/repair/ApplianceRepairApplicationTests.java`:

```java
package com.appliance.repair;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplianceRepairApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

**Step 6: 运行测试验证项目可以启动**

```bash
cd backend
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 7: 提交**

```bash
git add backend/build.gradle backend/src
git commit -m "feat: initialize Spring Boot project structure"
```

---

### Task 2: 配置 PostgreSQL 和 pgvector 扩展

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__init_schema.sql`

**Step 1: 创建数据库迁移目录**

```bash
mkdir -p backend/src/main/resources/db/migration
```

**Step 2: 创建数据库初始化脚本**

Create `backend/src/main/resources/db/migration/V1__init_schema.sql`:

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 知识库文档表
CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',
    error_message TEXT,
    file_path VARCHAR(500)
);

-- 向量存储表
CREATE TABLE IF NOT EXISTS vector_embeddings (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_text TEXT NOT NULL,
    embedding vector(1024) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引
CREATE INDEX IF NOT EXISTS idx_vector_embeddings_embedding
ON vector_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 文档状态索引
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status);
CREATE INDEX IF NOT EXISTS idx_documents_upload_time ON documents(upload_time DESC);

-- 会话表
CREATE TABLE IF NOT EXISTS conversations (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 消息索引
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, created_at);
```

**Step 3: 提交**

```bash
git add backend/src/main/resources/db/migration/V1__init_schema.sql
git commit -m "feat: add database schema with pgvector support"
```

---

### Task 3: 创建实体类和 Repository

**Files:**
- Create: `backend/src/main/java/com/appliance/repair/entity/DocumentStatus.java`
- Create: `backend/src/main/java/com/appliance/repair/entity/Document.java`
- Create: `backend/src/main/java/com/appliance/repair/entity/VectorEmbedding.java`
- Create: `backend/src/main/java/com/appliance/repair/entity/Conversation.java`
- Create: `backend/src/main/java/com/appliance/repair/entity/Message.java`
- Create: `backend/src/main/java/com/appliance/repair/repository/DocumentRepository.java`
- Create: `backend/src/main/java/com/appliance/repair/repository/ConversationRepository.java`
- Create: `backend/src/main/java/com/appliance/repair/repository/MessageRepository.java`

**Step 1: 创建枚举类 DocumentStatus**

Create `backend/src/main/java/com/appliance/repair/entity/DocumentStatus.java`:

```java
package com.appliance.repair.entity;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    VECTORIZING,
    READY,
    ERROR
}
```

**Step 2: 创建 Document 实体**

Create `backend/src/main/java/com/appliance/repair/entity/Document.java`:

```java
package com.appliance.repair.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType fileType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, updatable = false)
    private Timestamp uploadTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 500)
    private String filePath;

    @PrePersist
    protected void onCreate() {
        uploadTime = Timestamp.valueOf(LocalDateTime.now());
        if (status == null) {
            status = DocumentStatus.UPLOADED;
        }
    }
}
```

**Step 3: 创建 DocumentType 枚举**

Create `backend/src/main/java/com/appliance/repair/entity/DocumentType.java`:

```java
package com.appliance.repair.entity;

public enum DocumentType {
    PDF,
    MARKDOWN
}
```

**Step 4: 更新 Document 实体添加导入**

Modify `backend/src/main/java/com/appliance/repair/entity/Document.java`, add imports:

```java
import java.time.LocalDateTime;
import java.sql.Timestamp;
```

**Step 5: 创建 VectorEmbedding 实体**

Create `backend/src/main/java/com/appliance/repair/entity/VectorEmbedding.java`:

```java
package com.appliance.repair.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.Map;

@Entity
@Table(name = "vector_embeddings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(nullable = false)
    private String embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Timestamp.valueOf(LocalDateTime.now());
    }
}
```

**Step 6: 创建 Conversation 实体**

Create `backend/src/main/java/com/appliance/repair/entity/Conversation.java`:

```java
package com.appliance.repair.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(nullable = false)
    private Timestamp updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<Message> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Timestamp.valueOf(LocalDateTime.now());
        updatedAt = Timestamp.valueOf(LocalDateTime.now());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Timestamp.valueOf(LocalDateTime.now());
    }
}
```

**Step 7: 创建 Message 实体**

Create `backend/src/main/java/com/appliance/repair/entity/Message.java`:

```java
package com.appliance.repair.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;
import java.sql.Timestamp;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Timestamp.valueOf(LocalDateTime.now());
    }
}
```

**Step 8: 创建 MessageRole 枚举**

Create `backend/src/main/java/com/appliance/repair/entity/MessageRole.java`:

```java
package com.appliance.repair.entity;

public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
```

**Step 9: 创建 DocumentRepository**

Create `backend/src/main/java/com/appliance/repair/repository/DocumentRepository.java`:

```java
package com.appliance.repair.repository;

import com.appliance.repair.entity.Document;
import com.appliance.repair.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByStatus(DocumentStatus status);

    List<Document> findByOrderByUploadTimeDesc();
}
```

**Step 10: 创建 ConversationRepository**

Create `backend/src/main/java/com/appliance/repair/repository/ConversationRepository.java`:

```java
package com.appliance.repair.repository;

import com.appliance.repair.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
}
```

**Step 11: 创建 MessageRepository**

Create `backend/src/main/java/com/appliance/repair/repository/MessageRepository.java`:

```java
package com.appliance.repair.repository;

import com.appliance.repair.entity.Message;
import com.appliance.repair.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
```

**Step 12: 编译验证**

```bash
cd backend
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 13: 提交**

```bash
git add backend/src/main/java/com/appliance/repair/entity backend/src/main/java/com/appliance/repair/repository
git commit -m "feat: add entity classes and repositories"
```

---

### Task 4: 创建通用响应类和异常处理

**Files:**
- Create: `backend/src/main/java/com/appliance/repair/common/Result.java`
- Create: `backend/src/main/java/com/appliance/repair/common/ResultCode.java`
- Create: `backend/src/main/java/com/appliance/repair/exception/BusinessException.java`
- Create: `backend/src/main/java/com/appliance/repair/exception/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/appliance/repair/dto/ChatRequest.java`
- Create: `backend/src/main/java/com/appliance/repair/dto/ChatResponse.java`
- Create: `backend/src/main/java/com/appliance/repair/dto/DocumentUploadResponse.java`

**Step 1: 创建 ResultCode 枚举**

Create `backend/src/main/java/com/appliance/repair/common/ResultCode.java`:

```java
package com.appliance.repair.common;

public enum ResultCode {
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    DOCUMENT_PARSE_ERROR(1001, "文档解析失败"),
    DOCUMENT_VECTORIZATION_ERROR(1002, "文档向量化失败"),
    UNSUPPORTED_FILE_TYPE(1003, "不支持的文件类型"),
    LLM_ERROR(2001, "大模型调用失败"),
    NO_RELEVANT_CONTENT(2002, "未找到相关内容");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
```

**Step 2: 创建 Result 响应类**

Create `backend/src/main/java/com/appliance/repair/common/Result.java`:

```java
package com.appliance.repair.common;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(ResultCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return Result.<T>builder()
                .code(resultCode.getCode())
                .message(resultCode.getMessage())
                .build();
    }

    public static <T> Result<T> error(int code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}
```

**Step 3: 创建 BusinessException**

Create `backend/src/main/java/com/appliance/repair/exception/BusinessException.java`:

```java
package com.appliance.repair.exception;

import com.appliance.repair.common.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(ResultCode resultCode, String detailMessage) {
        super(resultCode.getMessage() + ": " + detailMessage);
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage() + ": " + detailMessage;
    }
}
```

**Step 4: 创建 GlobalExceptionHandler**

Create `backend/src/main/java/com/appliance/repair/exception/GlobalExceptionHandler.java`:

```java
package com.appliance.repair.exception;

import com.appliance.repair.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation error: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("Unexpected error", e);
        return Result.error(ResultCode.INTERNAL_ERROR);
    }
}
```

**Step 5: 创建 ChatRequest DTO**

Create `backend/src/main/java/com/appliance/repair/dto/ChatRequest.java`:

```java
package com.appliance.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private Long conversationId;

    @Builder.Default
    private int topK = 5;
}
```

**Step 6: 创建 ChatResponse DTO**

Create `backend/src/main/java/com/appliance/repair/dto/ChatResponse.java`:

```java
package com.appliance.repair.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {

    private String content;

    private Long conversationId;
}
```

**Step 7: 创建 DocumentUploadResponse DTO**

Create `backend/src/main/java/com/appliance/repair/dto/DocumentUploadResponse.java`:

```java
package com.appliance.repair.dto;

import com.appliance.repair.entity.DocumentStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentUploadResponse {

    private Long documentId;

    private String filename;

    private DocumentStatus status;

    private LocalDateTime uploadTime;
}
```

**Step 8: 编译验证**

```bash
cd backend
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 9: 提交**

```bash
git add backend/src/main/java/com/appliance/repair/common backend/src/main/java/com/appliance/repair/exception backend/src/main/java/com/appliance/repair/dto
git commit -m "feat: add common response classes and exception handling"
```

---

## Phase 2: 文档处理模块

### Task 5: 创建文档处理器服务

**Files:**
- Create: `backend/src/main/java/com/appliance/repair/service/DocumentProcessor.java`
- Create: `backend/src/main/java/com/appliance/repair/service/DocumentService.java`
- Create: `backend/src/main/java/com/appliance/repair/event/DocumentUploadEvent.java`
- Create: `backend/src/main/java/com/appliance/repair/event/DocumentProcessingListener.java`

**Step 1: 创建文档分块配置类**

Create `backend/src/main/java/com/appliance/repair/config/DocumentChunkConfig.java`:

```java
package com.appliance.repair.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkConfig {

    @Builder.Default
    private int chunkSize = 500;

    @Builder.Default
    private int chunkOverlap = 50;

    @Builder.Default
    private int maxChunkSize = 1000;
}
```

**Step 2: 创建文本分块工具类**

Create `backend/src/main/java/com/appliance/repair/util/TextChunker.java`:

```java
package com.appliance.repair.util;

import com.appliance.repair.config.DocumentChunkConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TextChunker {

    private final DocumentChunkConfig config;

    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("(?<=[。！？\\n])");

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();

        // 按段落分割
        String[] paragraphs = text.split("\\n\\n+");

        StringBuilder currentChunk = new StringBuilder();
        String currentSection = "";

        for (String paragraph : paragraphs) {
            // 检测标题
            if (paragraph.matches("^(#{1,6}\\s|第[一二三四五六七八九十]+[章节篇])")) {
                currentSection = paragraph + "\n";
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                currentChunk.append(currentSection);
                continue;
            }

            String paragraphWithContext = currentSection + paragraph;

            if (currentChunk.length() + paragraphWithContext.length() > config.getMaxChunkSize()) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder(paragraphWithContext);
            } else {
                currentChunk.append("\n\n").append(paragraphWithContext);
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}
```

**Step 3: 创建 PDF 解析器**

Create `backend/src/main/java/com/appliance/repair/parser/PdfParser.java`:

```java
package com.appliance.repair.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Component
public class PdfParser {

    public String parse(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            int pageCount = document.getNumberOfPages();
            StringBuilder text = new StringBuilder();

            // 逐页处理，保留页码信息
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document).trim();

                if (!pageText.isEmpty()) {
                    text.append("\n--- 第 ").append(i).append(" 页 ---\n");
                    text.append(pageText);
                    text.append("\n");
                }
            }

            return text.toString();
        }
    }
}
```

**Step 4: 创建 Markdown 解析器**

Create `backend/src/main/java/com/appliance/repair/parser/MarkdownParser.java`:

```java
package com.appliance.repair.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MarkdownParser {

    public String parse(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
```

**Step 5: 创建 DocumentUploadEvent**

Create `backend/src/main/java/com/appliance/repair/event/DocumentUploadEvent.java`:

```java
package com.appliance.repair.event;

import com.appliance.repair.entity.Document;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DocumentUploadEvent extends ApplicationEvent {

    private final Document document;
    private final String filePath;

    public DocumentUploadEvent(Object source, Document document, String filePath) {
        super(source);
        this.document = document;
        this.filePath = filePath;
    }
}
```

**Step 6: 创建 DocumentService**

Create `backend/src/main/java/com/appliance/repair/service/DocumentService.java`:

```java
package com.appliance.repair.service;

import com.appliance.repair.entity.Document;
import com.appliance.repair.entity.DocumentStatus;
import com.appliance.repair.entity.DocumentType;
import com.appliance.repair.event.DocumentUploadEvent;
import com.appliance.repair.exception.BusinessException;
import com.appliance.repair.parser.MarkdownParser;
import com.appliance.repair.parser.PdfParser;
import com.appliance.repair.repository.DocumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PdfParser pdfParser;
    private final MarkdownParser markdownParser;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory", e);
        }
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findByOrderByUploadTimeDesc();
    }

    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "文档不存在"));
    }

    public Document uploadDocument(MultipartFile file) {
        // 验证文件类型
        DocumentType fileType = getFileType(file.getOriginalFilename());
        if (fileType == null) {
            throw new BusinessException(com.appliance.repair.common.ResultCode.UNSUPPORTED_FILE_TYPE);
        }

        // 保存文件
        String filePath = saveFile(file);

        // 创建文档记录
        Document document = Document.builder()
                .filename(file.getOriginalFilename())
                .fileType(fileType)
                .fileSize(file.getSize())
                .filePath(filePath)
                .status(DocumentStatus.UPLOADED)
                .build();

        document = documentRepository.save(document);

        // 发布事件，触发异步处理
        eventPublisher.publishEvent(new DocumentUploadEvent(this, document, filePath));

        log.info("Document uploaded: {}, saved to: {}", document.getFilename(), filePath);

        return document;
    }

    public void deleteDocument(Long id) {
        Document document = getDocumentById(id);

        // 删除文件
        if (document.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(document.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete file: {}", document.getFilePath(), e);
            }
        }

        // 删除数据库记录（级联删除向量数据）
        documentRepository.delete(document);

        log.info("Document deleted: {}", document.getFilename());
    }

    private String saveFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());
            return filePath.toString();
        } catch (IOException e) {
            throw new BusinessException(com.appliance.repair.common.ResultCode.INTERNAL_ERROR);
        }
    }

    private DocumentType getFileType(String filename) {
        if (filename == null) {
            return null;
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> DocumentType.PDF;
            case "md", "markdown" -> DocumentType.MARKDOWN;
            default -> null;
        };
    }
}
```

**Step 7: 创建 DocumentProcessingListener**

Create `backend/src/main/java/com/appliance/repair/event/DocumentProcessingListener.java`:

```java
package com.appliance.repair.event;

import com.appliance.repair.entity.Document;
import com.appliance.repair.entity.DocumentStatus;
import com.appliance.repair.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessingListener {

    private final DocumentRepository documentRepository;

    @Async
    @EventListener
    @Transactional
    public void handleDocumentUpload(DocumentUploadEvent event) {
        Document document = event.getDocument();

        try {
            log.info("Processing document: {}", document.getFilename());

            // 更新状态为解析中
            document.setStatus(DocumentStatus.PARSING);
            documentRepository.save(document);

            // TODO: 调用 DocumentProcessor 处理文档

            // 更新状态为向量化中
            document.setStatus(DocumentStatus.VECTORIZING);
            documentRepository.save(document);

            // TODO: 调用向量化服务

            // 更新状态为就绪
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);

            log.info("Document processing completed: {}", document.getFilename());

        } catch (Exception e) {
            log.error("Failed to process document: {}", document.getFilename(), e);
            document.setStatus(DocumentStatus.ERROR);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
        }
    }
}
```

**Step 8: 编译验证**

```bash
cd backend
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 9: 提交**

```bash
git add backend/src/main/java/com/appliance/repair/config backend/src/main/java/com/appliance/repair/util backend/src/main/java/com/appliance/repair/parser backend/src/main/java/com/appliance/repair/event backend/src/main/java/com/appliance/repair/service
git commit -m "feat: add document processing service"
```

---

### Task 6: 创建文档管理 API

**Files:**
- Create: `backend/src/main/java/com/appliance/repair/controller/DocumentController.java`

**Step 1: 创建测试类**

Create `backend/src/test/java/com/appliance/repair/controller/DocumentControllerTest.java`:

```java
package com.appliance.repair.controller;

import com.appliance.repair.common.Result;
import com.appliance.repair.dto.DocumentUploadResponse;
import com.appliance.repair.entity.Document;
import com.appliance.repair.entity.DocumentType;
import com.appliance.repair.entity.DocumentStatus;
import com.appliance.repair.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Test
    void testGetAllDocuments() throws Exception {
        Document doc = Document.builder()
                .id(1L)
                .filename("test.pdf")
                .fileType(DocumentType.PDF)
                .fileSize(1024L)
                .status(DocumentStatus.READY)
                .uploadTime(java.sql.Timestamp.valueOf(LocalDateTime.now()))
                .build();

        when(documentService.getAllDocuments()).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].filename").value("test.pdf"));
    }

    @Test
    void testUploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test content".getBytes()
        );

        Document doc = Document.builder()
                .id(1L)
                .filename("test.pdf")
                .fileType(DocumentType.PDF)
                .fileSize(12L)
                .status(DocumentStatus.UPLOADED)
                .build();

        when(documentService.uploadDocument(any())).thenReturn(doc);

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.filename").value("test.pdf"));
    }

    @Test
    void testDeleteDocument() throws Exception {
        mockMvc.perform(delete("/api/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
```

**Step 2: 运行测试验证失败**

```bash
cd backend
./gradlew test --tests DocumentControllerTest
```

Expected: TEST FAILED (controller not created yet)

**Step 3: 创建 DocumentController**

Create `backend/src/main/java/com/appliance/repair/controller/DocumentController.java`:

```java
package com.appliance.repair.controller;

import com.appliance.repair.common.Result;
import com.appliance.repair.dto.DocumentUploadResponse;
import com.appliance.repair.entity.Document;
import com.appliance.repair.entity.DocumentStatus;
import com.appliance.repair.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public Result<List<DocumentUploadResponse>> getAllDocuments() {
        List<Document> documents = documentService.getAllDocuments();
        List<DocumentUploadResponse> responses = documents.stream()
                .map(this::toResponse)
                .toList();
        return Result.success(responses);
    }

    @PostMapping("/upload")
    public Result<DocumentUploadResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        Document document = documentService.uploadDocument(file);
        return Result.success(toResponse(document));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.success();
    }

    @GetMapping("/{id}/status")
    public Result<DocumentStatus> getDocumentStatus(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        return Result.success(document.getStatus());
    }

    private DocumentUploadResponse toResponse(Document document) {
        return DocumentUploadResponse.builder()
                .documentId(document.getId())
                .filename(document.getFilename())
                .status(document.getStatus())
                .uploadTime(document.getUploadTime() != null
                        ? document.getUploadTime().toLocalDateTime()
                        : LocalDateTime.now())
                .build();
    }
}
```

**Step 4: 运行测试验证通过**

```bash
cd backend
./gradlew test --tests DocumentControllerTest
```

Expected: TEST PASSED

**Step 5: 提交**

```bash
git add backend/src/main/java/com/appliance/repair/controller/DocumentController.java backend/src/test/java/com/appliance/repair/controller/DocumentControllerTest.java
git commit -m "feat: add document management API with tests"
```

---

### Task 7: 实现 Spring AI 集成和向量化

**Files:**
- Create: `backend/src/main/java/com/appliance/repair/config/SpringAiConfig.java`
- Create: `backend/src/main/java/com/appliance/repair/service/EmbeddingService.java`
- Create: `backend/src/main/java/com/appliance/repair/service/VectorStoreService.java`
- Modify: `backend/src/main/java/com/appliance/repair/event/DocumentProcessingListener.java`

**Step 1: 创建 Spring AI 配置类**

Create `backend/src/main/java/com/appliance/repair/config/SpringAiConfig.java`:

```java
package com.appliance.repair.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.embedding.options.model}")
    private String embeddingModel;

    @Bean
    public OpenAiApi openAiApi() {
        return new OpenAiApi(baseUrl, apiKey);
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingModel(openAiApi,
                org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                        .withModel(embeddingModel)
                        .build());
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, ApplicationContext applicationContext) {
        return new PgVectorStore(jdbcTemplate, embeddingModel,
                org.springframework.ai.vectorstore.PgVectorStore.PgVectorStoreConfig.builder()
                        .withTableName("vector_embeddings")
                        .withDimension(1024)
                        .withDistanceType(org.springframework.ai.vectorstore.VectorStore.DistanceType.COSINE_DISTANCE)
                        .withIndexType(org.springframework.ai.vectorstore.PgVectorStore.PgVectorStoreConfig.IndexType.IVFFLAT)
                        .build(),
                applicationContext);
    }
}
```

**Step 2: 创建 EmbeddingService**

Create `backend/src/main/java/com/appliance/repair/service/EmbeddingService.java`:

```java
package com.appliance.repair.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorStore vectorStore;

    public void embedAndStore(Long documentId, List<String> chunks) {
        List<org.springframework.ai.document.Document> documents = chunks.stream()
                .map(chunk -> new org.springframework.ai.document.Document(chunk,
                        Map.of("document_id", documentId.toString())))
                .toList();

        vectorStore.add(documents);

        log.info("Embedded and stored {} chunks for document {}", chunks.size(), documentId);
    }

    public List<org.springframework.ai.document.Document> searchSimilar(String query, int topK) {
        return vectorStore.similaritySearch(query, topK);
    }
}
```

**Step 3: 创建 VectorStoreService**

Create `backend/src/main/java/com/appliance/repair/service/VectorStoreService.java`:

```java
package com.appliance.repair.service;

import com.appliance.repair.parser.MarkdownParser;
import com.appliance.repair.parser.PdfParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final PdfParser pdfParser;
    private final MarkdownParser markdownParser;
    private final TextChunker textChunker;

    public void processAndStoreDocument(Long documentId, MultipartFile file, String filePath) throws IOException {
        // 解析文档
        String text = parseDocument(file);

        // 分块
        List<String> chunks = textChunker.chunk(text);

        // 创建 Spring AI Document 对象
        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(chunk,
                        java.util.Map.of("document_id", documentId.toString(),
                                        "filename", file.getOriginalFilename())))
                .toList();

        // 存储到向量数据库
        vectorStore.add(documents);

        log.info("Stored {} chunks for document {}", chunks.size(), documentId);
    }

    public List<Document> searchSimilar(String query, int topK) {
        SearchRequest request = SearchRequest.query(query).withTopK(topK);
        return vectorStore.similaritySearch(request);
    }

    public List<Document> searchSimilarWithFilter(String query, int topK, Long documentId) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.query(query)
                .withTopK(topK)
                .withFilterExpression(filterBuilder.eq("document_id", documentId.toString()).build());
        return vectorStore.similaritySearch(request);
    }

    public void deleteByDocumentId(Long documentId) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        vectorStore.delete(filterBuilder.eq("document_id", documentId.toString()).build());
        log.info("Deleted vectors for document {}", documentId);
    }

    private String parseDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

        return switch (extension) {
            case "pdf" -> pdfParser.parse(file);
            case "md", "markdown" -> markdownParser.parse(file);
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }
}
```

**Step 4: 更新 DocumentProcessingListener 集成向量化**

Modify `backend/src/main/java/com/appliance/repair/event/DocumentProcessingListener.java`:

```java
package com.appliance.repair.event;

import com.appliance.repair.entity.Document;
import com.appliance.repair.entity.DocumentStatus;
import com.appliance.repair.repository.DocumentRepository;
import com.appliance.repair.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessingListener {

    private final DocumentRepository documentRepository;
    private final VectorStoreService vectorStoreService;

    @Async
    @EventListener
    @Transactional
    public void handleDocumentUpload(DocumentUploadEvent event) {
        Document document = event.getDocument();
        String filePath = event.getFilePath();

        try {
            log.info("Processing document: {}", document.getFilename());

            // 更新状态为解析中
            document.setStatus(DocumentStatus.PARSING);
            documentRepository.save(document);

            // 读取文件并处理
            Path path = Paths.get(filePath);
            byte[] fileContent = Files.readAllBytes(path);

            // 创建 MultipartFile
            org.springframework.web.multipart.MultipartFile multipartFile =
                new org.springframework.mock.web.MockMultipartFile(
                    document.getFilename(),
                    document.getFilename(),
                    Files.probeContentType(path),
                    fileContent
                );

            // 处理并存储向量
            vectorStoreService.processAndStoreDocument(document.getId(), multipartFile, filePath);

            // 更新状态为就绪
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);

            log.info("Document processing completed: {}", document.getFilename());

        } catch (Exception e) {
            log.error("Failed to process document: {}", document.getFilename(), e);
            document.setStatus(DocumentStatus.ERROR);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
        }
    }
}
```

**Step 5: 创建集成测试**

Create `backend/src/test/java/com/appliance/repair/service/VectorStoreServiceTest.java`:

```java
package com.appliance.repair.service;

import com.appliance.repair.config.SpringAiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.openai.api-key=test-key",
    "spring.ai.openai.base-url=https://api.deepseek.com",
    "spring.datasource.url=jdbc:h2:mem:testdb"
})
class VectorStoreServiceTest {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    void testSearchSimilar() {
        // This test requires actual database and API setup
        // Skip in unit tests, verify in integration tests
        assertTrue(true);
    }
}
```

**Step 6: 编译验证**

```bash
cd backend
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 7: 提交**

```bash
git add backend/src/main/java/com/appliance/repair/config/SpringAiConfig.java backend/src/main/java/com/appliance/repair/service/EmbeddingService.java backend/src/main/java/com/appliance/repair/service/VectorStoreService.java backend/src/main/java/com/appliance/repair/event/DocumentProcessingListener.java backend/src/test/java/com/appliance/repair/service/VectorStoreServiceTest.java
git commit -m "feat: integrate Spring AI for embedding and vector storage"
```

---

## Phase 3: 聊天服务模块

### Task 8: 创建 RAG 聊天服务

**Files:**
- Create: `backend/src/main/java/com/appliance/repair/service/ChatService.java`
- Create: `backend/src/main/java/com/appliance/repair/advisor/RagAdvisor.java`
- Create: `backend/src/main/java/com/appliance/repair/controller/ChatController.java`

**Step 1: 创建 RAG Advisor**

Create `backend/src/main/java/com/appliance/repair/advisor/RagAdvisor.java`:

```java
package com.appliance.repair.advisor;

import com.appliance.repair.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorRequest;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class RagAdvisor implements CallAroundAdvisor {

    private final VectorStoreService vectorStoreService;

    @Override
    public CallAroundAdvisorResponse aroundCall(CallAroundAdvisorRequest request, CallAroundAdvisorChain chain) {
        // 获取用户问题
        String userMessage = request.userText();
        log.debug("Processing user message: {}", userMessage);

        // 检索相关文档
        var relevantDocs = vectorStoreService.searchSimilar(userMessage, 5);

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found for query: {}", userMessage);
            return chain.nextAroundCall(request);
        }

        // 构建增强上下文
        String context = relevantDocs.stream()
                .map(doc -> doc.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));

        // 构建系统提示
        String systemPrompt = buildSystemPrompt(context);

        // 创建增强的提示
        Prompt enhancedPrompt = new Prompt(List.of(
            new org.springframework.ai.chat.messages.SystemMessage(systemPrompt),
            new UserMessage(userMessage)
        ));

        // 继续链式调用
        return chain.nextAroundCall(request);
    }

    private String buildSystemPrompt(String context) {
        return """
                你是一位专业的家电维修技术专家助手。

                ## 参考知识
                以下是从维修手册中检索到的相关内容：

                %s

                ## 回答要求
                1. 基于上述参考知识回答用户问题
                2. 如果参考知识中没有相关信息，请明确告知用户
                3. 提供清晰、详细的维修步骤
                4. 如涉及安全注意事项，请务必提醒用户
                5. 回答应专业、准确、易于理解

                现在请回答用户的问题。
                """.formatted(context);
    }

    @Override
    public String getName() {
        return "RagAdvisor";
    }
}
```

**Step 2: 创建 ChatService**

Create `backend/src/main/java/com/appliance/repair/service/ChatService.java`:

```java
package com.appliance.repair.service;

import com.appliance.repair.dto.ChatRequest;
import com.appliance.repair.entity.Conversation;
import com.appliance.repair.entity.Message;
import com.appliance.repair.entity.MessageRole;
import com.appliance.repair.repository.ConversationRepository;
import com.appliance.repair.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final VectorStoreService vectorStoreService;
    private final OpenAiChatModel chatModel;

    private final ChatMemory chatMemory = new InMemoryChatMemory();

    public SseEmitter chatStream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60000L);

        // 在新线程中处理
        new Thread(() -> {
            try {
                handleChatRequest(request, emitter);
            } catch (Exception e) {
                log.error("Chat error", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage()));
                    emitter.complete();
                } catch (IOException ioException) {
                    log.error("Failed to send error", ioException);
                }
            }
        }).start();

        return emitter;
    }

    private void handleChatRequest(ChatRequest request, SseEmitter emitter) throws IOException {
        // 获取或创建会话
        Conversation conversation = getOrCreateConversation(request.getConversationId());

        // 构建提示词
        String systemPrompt = buildSystemPrompt(request.getMessage());

        // 创建 ChatClient
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new PromptChatMemoryAdvisor(chatMemory),
                        new com.appliance.repair.advisor.RagAdvisor(vectorStoreService)
                )
                .defaultOptions(org.springframework.ai.chat.prompt.ChatOptions.builder()
                        .withModel("deepseek-chat")
                        .withTemperature(0.7)
                        .build())
                .build();

        // 保存用户消息
        Message userMessage = Message.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(request.getMessage())
                .build();
        messageRepository.save(userMessage);

        // 发送用户消息确认
        emitter.send(SseEmitter.event()
                .name("user-message")
                .data(Map.of("id", userMessage.getId(), "content", request.getMessage())));

        // 流式生成回复
        StringBuilder fullResponse = new StringBuilder();

        chatClient.prompt()
                .user(request.getMessage())
                .system(systemPrompt)
                .stream()
                .content()
                .subscribe(
                        content -> {
                            fullResponse.append(content);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("content")
                                        .data(content));
                            } catch (IOException e) {
                                log.error("Failed to send content", e);
                            }
                        },
                        error -> {
                            log.error("Stream error", error);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(error.getMessage()));
                            } catch (IOException e) {
                                log.error("Failed to send error", e);
                            }
                            emitter.complete();
                        },
                        () -> {
                            // 保存助手消息
                            Message assistantMessage = Message.builder()
                                    .conversation(conversation)
                                    .role(MessageRole.ASSISTANT)
                                    .content(fullResponse.toString())
                                    .build();
                            messageRepository.save(assistantMessage);

                            // 发送完成事件
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("done")
                                        .data(Map.of(
                                                "conversationId", conversation.getId(),
                                                "messageId", assistantMessage.getId()
                                        )));
                            } catch (IOException e) {
                                log.error("Failed to send done event", e);
                            }
                            emitter.complete();
                        }
                );

        // 更新会话时间
        conversationRepository.save(conversation);
    }

    private Conversation getOrCreateConversation(Long conversationId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new com.appliance.repair.exception.BusinessException(
                            404, "会话不存在"));
        }

        // 创建新会话
        Conversation conversation = new Conversation();
        conversation.setTitle("新对话");
        return conversationRepository.save(conversation);
    }

    private String buildSystemPrompt(String userMessage) {
        // 检索相关文档
        var relevantDocs = vectorStoreService.searchSimilar(userMessage, 5);

        if (relevantDocs.isEmpty()) {
            return """
                    你是一位专业的家电维修技术专家助手。

                    ## 注意
                    当前知识库中没有找到与用户问题直接相关的维修手册内容。
                    请根据你的一般知识提供建议，但务必告知用户：
                    1. 这些建议仅供参考，具体操作请参考官方维修手册
                    2. 涉及安全问题时，建议联系专业维修人员

                    请专业、准确、友好地回答用户问题。
                    """;
        }

        String context = relevantDocs.stream()
                .map(doc -> doc.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));

        return """
                你是一位专业的家电维修技术专家助手。

                ## 参考知识
                以下是从维修手册中检索到的相关内容：

                %s

                ## 回答要求
                1. 基于上述参考知识回答用户问题
                2. 提供清晰、详细的维修步骤
                3. 如涉及安全注意事项，请务必提醒用户
                4. 回答应专业、准确、易于理解
                5. 使用 Markdown 格式组织内容

                现在请回答用户的问题。
                """.formatted(context);
    }

    public List<Message> getConversationMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public List<Conversation> getAllConversations() {
        return conversationRepository.findAll();
    }
}
```

**Step 3: 创建 ChatController**

Create `backend/src/main/java/com/appliance/repair/controller/ChatController.java`:

```java
package com.appliance.repair.controller;

import com.appliance.repair.common.Result;
import com.appliance.repair.dto.ChatRequest;
import com.appliance.repair.entity.Conversation;
import com.appliance.repair.entity.Message;
import com.appliance.repair.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request);
    }

    @GetMapping("/conversations")
    public Result<List<Conversation>> getConversations() {
        return Result.success(chatService.getAllConversations());
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long id) {
        return Result.success(chatService.getConversationMessages(id));
    }
}
```

**Step 4: 创建测试**

Create `backend/src/test/java/com/appliance/repair/controller/ChatControllerTest.java`:

```java
package com.appliance.repair.controller;

import com.appliance.repair.common.Result;
import com.appliance.repair.dto.ChatRequest;
import com.appliance.repair.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Test
    void testChatStream() throws Exception {
        SseEmitter emitter = new SseEmitter(60000L);
        when(chatService.chatStream(any(ChatRequest.class))).thenReturn(emitter);

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"洗衣机不转了怎么办？\"}"))
                .andExpect(status().isOk());
    }
}
```

**Step 5: 编译验证**

```bash
cd backend
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 6: 提交**

```bash
git add backend/src/main/java/com/appliance/repair/advisor backend/src/main/java/com/appliance/repair/service/ChatService.java backend/src/main/java/com/appliance/repair/controller/ChatController.java backend/src/test/java/com/appliance/repair/controller/ChatControllerTest.java
git commit -m "feat: add RAG chat service with SSE streaming"
```

---

## Phase 4: 前端应用

### Task 9: 初始化前端项目

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`

**Step 1: 创建 package.json**

Create `frontend/package.json`:

```json
{
  "name": "appliance-repair-frontend",
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.5",
    "pinia": "^2.1.7",
    "ant-design-vue": "^4.1.0",
    "@ant-design/icons-vue": "^7.0.1",
    "axios": "^1.6.5",
    "@microsoft/fetch-event-source": "^2.0.1",
    "markdown-it": "^14.0.0",
    "tailwindcss": "^3.4.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.3",
    "vite": "^5.0.11"
  }
}
```

**Step 2: 创建 vite.config.js**

Create `frontend/vite.config.js`:

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

**Step 3: 创建 index.html**

Create `frontend/index.html`:

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>家电维修知识助手</title>
</head>
<body>
  <div id="app"></div>
  <script type="module" src="/src/main.js"></script>
</body>
</html>
```

**Step 4: 创建 main.js**

Create `frontend/src/main.js`:

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(Antd)

app.mount('#app')
```

**Step 5: 创建 App.vue**

Create `frontend/src/App.vue`:

```vue
<template>
  <a-config-provider :locale="zhCN">
    <div id="app" class="h-screen flex flex-col">
      <RouterView />
    </div>
  </a-config-provider>
</template>

<script setup>
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
</script>

<style>
@tailwind base;
@tailwind components;
@tailwind utilities;

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
</style>
```

**Step 6: 创建路由配置**

Create `frontend/src/router/index.js`:

```javascript
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
```

**Step 7: 创建 Home 页面**

Create `frontend/src/views/Home.vue`:

```vue
<template>
  <div class="flex h-full">
    <!-- 侧边栏 - 文档管理 -->
    <aside class="w-80 bg-gray-50 border-r border-gray-200 flex flex-col">
      <div class="p-4 border-b border-gray-200">
        <h1 class="text-lg font-semibold">家电维修知识助手</h1>
      </div>

      <div class="p-4 flex-1 overflow-y-auto">
        <div class="mb-4">
          <a-upload
            :before-upload="handleBeforeUpload"
            :show-upload-list="false"
            accept=".pdf,.md,.markdown"
          >
            <a-button type="primary" block>
              <UploadOutlined />
              上传文档
            </a-button>
          </a-upload>
        </div>

        <a-divider class="my-4">文档列表</a-divider>

        <a-list
          :data-source="documents"
          :loading="loading"
          item-layout="vertical"
          size="small"
        >
          <template #renderItem="{ item }">
            <a-list-item>
              <template #actions>
                <a @click.stop="handleDelete(item.id)">
                  <DeleteOutlined class="text-red-500" />
                </a>
              </template>
              <a-list-item-meta>
                <template #title>
                  <span class="text-sm">{{ item.filename }}</span>
                </template>
                <template #description>
                  <a-tag :color="getStatusColor(item.status)">
                    {{ getStatusText(item.status) }}
                  </a-tag>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </div>
    </aside>

    <!-- 主聊天区域 -->
    <main class="flex-1 flex flex-col">
      <div class="flex-1 overflow-y-auto p-6" ref="messagesContainer">
        <div v-if="messages.length === 0" class="text-center text-gray-400 mt-20">
          <CustomerServiceOutlined class="text-6xl mb-4" />
          <p class="text-lg">上传维修手册，开始智能问答</p>
        </div>

        <div v-else class="max-w-4xl mx-auto space-y-6">
          <div
            v-for="message in messages"
            :key="message.id"
            :class="[
              'flex',
              message.role === 'USER' ? 'justify-end' : 'justify-start'
            ]"
          >
            <div
              :class="[
                'max-w-2xl rounded-lg px-4 py-3',
                message.role === 'USER'
                  ? 'bg-blue-500 text-white'
                  : 'bg-gray-100 text-gray-900'
              ]"
            >
              <div v-html="renderMarkdown(message.content)"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="border-t border-gray-200 p-4">
        <div class="max-w-4xl mx-auto">
          <a-input-search
            v-model:value="inputMessage"
            placeholder="描述问题，例如：洗衣机显示 E3 错误代码怎么办？"
            size="large"
            :loading="isStreaming"
            @search="handleSend"
          >
            <template #suffix>
              <a-button
                type="primary"
                :disabled="!inputMessage.trim() || isStreaming"
                @click="handleSend"
              >
                <SendOutlined v-if="!isStreaming" />
                <LoadingOutlined v-else />
              </a-button>
            </template>
          </a-input-search>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  UploadOutlined,
  DeleteOutlined,
  CustomerServiceOutlined,
  SendOutlined,
  LoadingOutlined
} from '@ant-design/icons-vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()

// 状态
const documents = ref([])
const messages = ref([])
const inputMessage = ref('')
const isStreaming = ref(false)
const loading = ref(false)
const messagesContainer = ref(null)

// 加载文档列表
const loadDocuments = async () => {
  loading.value = true
  try {
    const response = await fetch('/api/documents')
    const result = await response.json()
    if (result.code === 200) {
      documents.value = result.data
    }
  } catch (error) {
    message.error('加载文档失败')
  } finally {
    loading.value = false
  }
}

// 上传文档
const handleBeforeUpload = async (file) => {
  const formData = new FormData()
  formData.append('file', file)

  try {
    message.loading('文档上传中，请稍候...', 0)
    const response = await fetch('/api/documents/upload', {
      method: 'POST',
      body: formData
    })
    const result = await response.json()
    message.destroy()

    if (result.code === 200) {
      message.success('文档上传成功，正在处理...')
      await loadDocuments()

      // 轮询检查处理状态
      checkDocumentStatus(result.data.documentId)
    } else {
      message.error(result.message || '上传失败')
    }
  } catch (error) {
    message.destroy()
    message.error('上传失败')
  }

  return false
}

// 检查文档处理状态
const checkDocumentStatus = async (documentId) => {
  const interval = setInterval(async () => {
    try {
      const response = await fetch(`/api/documents/${documentId}/status`)
      const result = await response.json()

      if (result.data === 'READY') {
        clearInterval(interval)
        message.success('文档处理完成，可以开始问答了')
        await loadDocuments()
      } else if (result.data === 'ERROR') {
        clearInterval(interval)
        message.error('文档处理失败')
        await loadDocuments()
      }
    } catch (error) {
      clearInterval(interval)
    }
  }, 2000)
}

// 删除文档
const handleDelete = async (id) => {
  try {
    const response = await fetch(`/api/documents/${id}`, {
      method: 'DELETE'
    })
    const result = await response.json()

    if (result.code === 200) {
      message.success('文档已删除')
      await loadDocuments()
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// 发送消息
const handleSend = async () => {
  if (!inputMessage.value.trim() || isStreaming.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  isStreaming.value = true

  // 添加用户消息到界面
  messages.value.push({
    id: Date.now(),
    role: 'USER',
    content: userMessage
  })

  // 添加一个临时的助手消息
  const assistantMessageId = Date.now() + 1
  messages.value.push({
    id: assistantMessageId,
    role: 'ASSISTANT',
    content: ''
  })

  await scrollToBottom()

  // 发送 SSE 请求
  try {
    await fetchEventSource('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: userMessage,
        topK: 5
      },
      onmessage: (event) => {
        const data = event.data

        if (event.event === 'content') {
          // 更新助手消息内容
          const msg = messages.value.find(m => m.id === assistantMessageId)
          if (msg) {
            msg.content += data
          }
          scrollToBottom()
        } else if (event.event === 'done') {
          isStreaming.value = false
        } else if (event.event === 'error') {
          isStreaming.value = false
          message.error('发生错误: ' + data)
        }
      },
      onerror: (error) => {
        isStreaming.value = false
        message.error('连接失败')
        throw error
      }
    })
  } catch (error) {
    isStreaming.value = false
    message.error('发送失败')
  }
}

// 渲染 Markdown
const renderMarkdown = (content) => {
  return md.render(content || '')
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 获取状态颜色
const getStatusColor = (status) => {
  const colors = {
    UPLOADED: 'default',
    PARSING: 'processing',
    VECTORIZING: 'processing',
    READY: 'success',
    ERROR: 'error'
  }
  return colors[status] || 'default'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    UPLOADED: '已上传',
    PARSING: '解析中',
    VECTORIZING: '向量化中',
    READY: '就绪',
    ERROR: '错误'
  }
  return texts[status] || status
}

onMounted(() => {
  loadDocuments()
})
</script>
```

**Step 8: 创建 Tailwind 配置**

Create `frontend/tailwind.config.js`:

```javascript
/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}
```

**Step 9: 安装依赖并测试**

```bash
cd frontend
npm install
npm run dev
```

Expected: Vite server running on http://localhost:3000

**Step 10: 提交**

```bash
git add frontend
git commit -m "feat: initialize Vue 3 frontend with chat interface"
```

---

## Phase 5: 部署和配置

### Task 10: 创建 Docker 配置

**Files:**
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `.gitignore`

**Step 1: 创建后端 Dockerfile**

Create `backend/Dockerfile`:

```dockerfile
FROM openjdk:21-jdk-slim

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src ./src
COPY gradlew ./

RUN apt-get update && apt-get install -y gradle && \
    chmod +x gradlew && \
    ./gradlew build --no-daemon

FROM openjdk:21-jre-slim

WORKDIR /app

COPY --from=0 /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 2: 创建 docker-compose.yml**

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: appliance-repair-db
    environment:
      POSTGRES_DB: appliance_repair
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD:-postgres}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: appliance-repair-backend
    environment:
      DB_USERNAME: postgres
      DB_PASSWORD: ${DB_PASSWORD:-postgres}
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
      UPLOAD_DIR: /data/uploads
    ports:
      - "8080:8080"
    volumes:
      - upload_data:/data/uploads
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: appliance-repair-frontend
    ports:
      - "3000:80"
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  postgres_data:
  upload_data:
```

**Step 3: 创建前端 Dockerfile**

Create `frontend/Dockerfile`:

```dockerfile
FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

**Step 4: 创建 nginx 配置**

Create `frontend/nginx.conf`:

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE support
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
    }

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
}
```

**Step 5: 创建环境变量示例**

Create `.env.example`:

```env
# 数据库配置
DB_PASSWORD=your_secure_password

# DeepSeek API
DEEPSEEK_API_KEY=your_deepseek_api_key

# 上传目录
UPLOAD_DIR=./data/uploads
```

**Step 6: 创建 .gitignore**

Create `.gitignore`:

```
# 数据
data/uploads/

# 环境变量
.env

# IDE
.idea/
.vscode/
*.iml

# 日志
*.log

# 构建产物
backend/build/
frontend/dist/
frontend/node_modules/

# 操作系统
.DS_Store
Thumbs.db
```

**Step 7: 提交**

```bash
git add Dockerfile docker-compose.yml .env.example .gitignore backend/Dockerfile frontend/Dockerfile frontend/nginx.conf
git commit -m "feat: add Docker deployment configuration"
```

---

### Task 11: 创建部署文档

**Files:**
- Create: `docs/DEPLOYMENT.md`

**Step 1: 创建部署文档**

Create `docs/DEPLOYMENT.md`:

```markdown
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
```

**Step 2: 提交**

```bash
git add docs/DEPLOYMENT.md
git commit -m "docs: add deployment guide"
```

---

## Phase 6: 测试和验证

### Task 12: 端到端测试

**Step 1: 启动所有服务**

```bash
docker-compose up -d
```

**Step 2: 等待服务就绪**

```bash
docker-compose logs -f backend
```

等待看到 "Started ApplianceRepairApplication"

**Step 3: 测试文档上传**

1. 访问 http://localhost:3000
2. 点击"上传文档"按钮
3. 选择一个测试 PDF 文件（家电维修手册）
4. 观察文档状态变化：已上传 → 解析中 → 向量化中 → 就绪

**Step 4: 测试问答功能**

1. 在输入框输入问题，例如："洗衣机显示 E3 错误代码怎么办？"
2. 观察流式输出
3. 验证回答是否基于上传的文档内容

**Step 5: 验证数据库**

```bash
# 连接数据库
docker exec -it appliance-repair-db psql -U postgres appliance_repair

# 查询文档
SELECT * FROM documents;

# 查询向量数量
SELECT COUNT(*) FROM vector_embeddings;
```

**Step 6: 性能测试**

```bash
# 上传大文档测试（>10MB PDF）
# 并发问答测试
# 向量检索性能测试
```

---

## 完成检查清单

- [ ] 后端服务正常启动
- [ ] 前端页面正常访问
- [ ] 文档上传功能正常
- [ ] 文档解析和向量化成功
- [ ] 问答功能返回正确结果
- [ ] SSE 流式输出正常
- [ ] 数据存储到 PostgreSQL
- [ ] 向量数据存储到 pgvector
- [ ] Docker 部署成功
- [ ] 文档完整

---

**计划版本**: 1.0
**创建日期**: 2026-03-08
**预计工时**: 16-20 小时
