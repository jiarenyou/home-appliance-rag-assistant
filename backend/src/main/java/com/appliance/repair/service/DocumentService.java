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
