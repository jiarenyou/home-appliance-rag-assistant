package com.appliance.repair.util;

import com.appliance.repair.config.DocumentChunkConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TextChunker {

    private final DocumentChunkConfig config;

    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("(?<=[。！？\\n])");

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();

        // 按段落分割
        String[] paragraphs = text.split("\\n\\n+");

        StringBuilder currentChunk = new StringBuilder();
        String currentSection = "";

        for (String paragraph : paragraphs) {
            // 检测标题
            if (paragraph.matches("^(#{1,6}\\s|第[一二三四五六七八九十]+[章节篇])")) {
                currentSection = paragraph + "\n";
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                currentChunk.append(currentSection);
                continue;
            }

            String paragraphWithContext = currentSection + paragraph;

            if (currentChunk.length() + paragraphWithContext.length() > config.getMaxChunkSize()) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder(paragraphWithContext);
            } else {
                currentChunk.append("\n\n").append(paragraphWithContext);
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}
