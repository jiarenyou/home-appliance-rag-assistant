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
