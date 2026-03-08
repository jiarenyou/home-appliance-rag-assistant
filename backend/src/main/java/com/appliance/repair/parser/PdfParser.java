package com.appliance.repair.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class PdfParser {

    public String parse(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return extractText(document);
        }
    }

    public String parseFromFile(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return extractText(document);
        }
    }

    private String extractText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);

        int pageCount = document.getNumberOfPages();
        StringBuilder text = new StringBuilder();

        // 逐页处理，保留页码信息
        for (int i = 1; i <= pageCount; i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String pageText = stripper.getText(document).trim();

            if (!pageText.isEmpty()) {
                text.append("\n--- 第 ").append(i).append(" 页 ---\n");
                text.append(pageText);
                text.append("\n");
            }
        }

        return text.toString();
    }
}
