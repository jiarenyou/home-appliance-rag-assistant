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
