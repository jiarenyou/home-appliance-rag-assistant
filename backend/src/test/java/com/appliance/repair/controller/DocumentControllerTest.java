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
