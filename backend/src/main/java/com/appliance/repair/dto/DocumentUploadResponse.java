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
