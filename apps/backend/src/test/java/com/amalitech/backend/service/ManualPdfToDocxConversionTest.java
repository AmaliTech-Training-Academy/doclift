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

/**
 * Manual, developer-driven conversion check: point it at any PDF on disk
 * via -Dpdf.path=/path/to/file.pdf and it converts it through the real
 * extraction + Word-writing pipeline, then writes the resulting .docx
 * next to the input so it can be opened and inspected by eye.
 *
 * Skipped unless pdf.path is supplied, so it never runs as part of the
 * normal test suite or CI.
 */
class ManualPdfToDocxConversionTest {

    @Test
    @EnabledIfSystemProperty(named = "pdf.path", matches = ".+")
    void convertGivenPdfToDocxForManualInspection() throws IOException {

        Path inputPath = Path.of(System.getProperty("pdf.path"));

        assertThat(Files.exists(inputPath))
                .as("PDF file should exist at " + inputPath)
                .isTrue();

        byte[] pdfBytes = Files.readAllBytes(inputPath);

        PdfExtractionService extractionService =
                new PdfExtractionServiceImpl(new StructureRecoveryServiceImpl());

        WordWriterService wordWriterService =
                new WordWriterServiceImpl();

        PdfExtractionResult extractionResult =
                extractionService.extract(pdfBytes);

        System.out.println("Pages: " + extractionResult.getPages().size());

        for (PageExtraction page : extractionResult.getPages()) {

            long tableBlockCount = page.getStructuredBlocks().stream()
                    .filter(block -> block.getType() == BlockType.TABLE)
                    .count();

            System.out.println(
                    "Page " + page.getPageIndex()
                            + ": " + tableBlockCount + " table(s) detected"
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
                        System.out.print(
                                "[" + cell.text() + "] "
                        );
                    }
                    System.out.println();
                }
            }
        }

        byte[] docxBytes = wordWriterService.write(extractionResult);

        String outputFileName = inputPath.getFileName()
                .toString()
                .replaceAll("(?i)\\.pdf$", "")
                + "-converted.docx";

        Path outputPath = inputPath.resolveSibling(outputFileName);

        Files.write(outputPath, docxBytes);

        System.out.println("Wrote: " + outputPath.toAbsolutePath());
    }
}
