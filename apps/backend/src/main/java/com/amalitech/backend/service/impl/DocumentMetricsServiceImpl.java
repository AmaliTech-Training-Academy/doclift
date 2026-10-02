package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.DocumentMetricsService;
import com.amalitech.backend.service.PageExtraction;
import com.amalitech.backend.service.PdfExtractionResult;
import com.amalitech.backend.service.StructuredBlock;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentMetricsServiceImpl implements DocumentMetricsService {

    private static final Pattern WORD_PATTERN =
            Pattern.compile(
                    "[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*"
            );

    @Override
    public int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        Matcher matcher = WORD_PATTERN.matcher(text);

        int count = 0;

        while (matcher.find()) {
            count++;
        }

        return count;
    }

    @Override
    public int countSourceWords(PdfExtractionResult extractionResult) {
        if (extractionResult == null) {
            return 0;
        }

        int totalWords = 0;

        for (PageExtraction page : extractionResult.getPages()) {
            for (StructuredBlock block : page.getStructuredBlocks()) {
                totalWords += countWords(block.getText());
            }
        }

        return totalWords;
    }

    @Override
    public int countOutputWords(byte[] docxBytes) {
        if (docxBytes == null || docxBytes.length == 0) {
            return 0;
        }

        try (
                ByteArrayInputStream inputStream =
                        new ByteArrayInputStream(docxBytes);

                XWPFDocument document =
                        new XWPFDocument(inputStream)
        ) {
            int totalWords = 0;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                totalWords += countWords(paragraph.getText());
            }

            for (XWPFTable table : document.getTables()) {
                totalWords += countWords(table.getText());
            }

            return totalWords;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read generated Word document.",
                    e
            );
        }
    }
}