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

            // 根据文件类型处理并存储向量
            String extension = document.getFilename().substring(document.getFilename().lastIndexOf(".") + 1).toLowerCase();
            vectorStoreService.processDocumentFromFile(document.getId(), path, extension, document.getFilename());

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
