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
