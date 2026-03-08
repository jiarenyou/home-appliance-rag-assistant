package com.appliance.repair.service;

import com.appliance.repair.parser.MarkdownParser;
import com.appliance.repair.parser.PdfParser;
import com.appliance.repair.util.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public void processDocumentFromFile(Long documentId, Path filePath, String extension, String filename) throws IOException {
        // 解析文档
        String text = parseDocumentFromFile(filePath, extension);

        // 分块
        List<String> chunks = textChunker.chunk(text);

        // 创建 Spring AI Document 对象
        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(chunk,
                        java.util.Map.of("document_id", documentId.toString(),
                                        "filename", filename)))
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

    public void deleteByDocumentId(List<String> ids) {
        vectorStore.delete(ids);
        log.info("Deleted vectors for ids count: {}", ids.size());
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

    private String parseDocumentFromFile(Path filePath, String extension) throws IOException {
        return switch (extension) {
            case "pdf" -> pdfParser.parseFromFile(filePath);
            case "md", "markdown" -> markdownParser.parseFromFile(filePath);
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }
}
