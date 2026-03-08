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
