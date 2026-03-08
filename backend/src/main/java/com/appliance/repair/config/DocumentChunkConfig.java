package com.appliance.repair.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkConfig {

    private int chunkSize = 500;

    private int chunkOverlap = 50;

    private int maxChunkSize = 1000;
}
