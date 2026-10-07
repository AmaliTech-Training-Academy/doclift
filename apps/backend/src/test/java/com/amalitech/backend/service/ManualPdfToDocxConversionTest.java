package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.PdfExtractionServiceImpl;
import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import com.amalitech.backend.service.impl.WordWriterServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ManualPdfToDocxConversionTest {

    private final PdfExtractionServiceImpl extractionService =
            new PdfExtractionServiceImpl(new StructureRecoveryServiceImpl());

    private final WordWriterServiceImpl wordWriterService = new WordWriterServiceImpl();

    @Test
    @EnabledIfSystemProperty(named = "pdf.path", matches = ".+")
    void convertGivenPdfToDocxForManualInspection() throws IOException {

        Path inputPath = Path.of(System.getProperty("pdf.path"));

        assertThat(Files.exists(inputPath))
                .as("PDF file should exist at " + inputPath)
                .isTrue();

        byte[] pdfBytes = Files.readAllBytes(inputPath);

        PdfExtractionResult extractionResult = extractionService.extract(pdfBytes);

        System.out.println("Pages: " + extractionResult.getPages().size());

        int totalImages = 0;

        for (PageExtraction page : extractionResult.getPages()) {

            totalImages += page.getImages().size();

            long tableBlockCount = page.getStructuredBlocks().stream()
                    .filter(block -> block.getType() == BlockType.TABLE)
                    .count();

            System.out.println(
                    "Page " + page.getPageIndex()
                            + ": " + tableBlockCount + " table(s), "
                            + page.getImages().size() + " image(s)"
            );

            for (StructuredBlock block : page.getStructuredBlocks()) {

                if (block.getType() != BlockType.TABLE) {
                    continue;
                }

                int rowCount = block.getTableRows().size();

                int columnCount = rowCount == 0
                        ? 0
                        : block.getTableRows().getFirst().size();

                System.out.println(
                        "  table: " + rowCount + " rows x "
                                + columnCount + " cols"
                );

                for (var row : block.getTableRows()) {
                    for (var cell : row) {
                        System.out.print("[" + cell.text() + "] ");
                    }
                    System.out.println();
                }
            }
        }

        byte[] docxBytes = wordWriterService.write(extractionResult);

        String outPath = System.getProperty("docx.out");

        Path outputPath = (outPath != null && !outPath.isBlank())
                ? Path.of(outPath)
                : inputPath.resolveSibling(
                        inputPath.getFileName().toString().replaceAll("(?i)\\.pdf$", "") + "-converted.docx"
                );

        Files.write(outputPath, docxBytes);

        System.out.printf(
                "Converted %s -> %s (pages=%d, images=%d, bytes=%d)%n",
                inputPath, outputPath, extractionResult.getPages().size(), totalImages, docxBytes.length
        );
    }
}