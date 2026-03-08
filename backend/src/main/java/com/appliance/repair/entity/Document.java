package com.appliance.repair.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.sql.Timestamp;

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
