package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.PdfExtractionServiceImpl;
import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import com.amalitech.backend.service.impl.WordWriterServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Manual conversion utility, not part of the regular suite's coverage: converts one PDF to a
 * DOCX file on disk so the result can be opened and inspected by eye. Skipped unless pdf.path
 * is passed. Run with:
 *
 * ./mvnw test -Dtest=ManualPdfToDocxConversionTest -Dpdf.path=/absolute/path/to/file.pdf
 *
 * Optionally pass -Ddocx.out=/absolute/path/to/output.docx to control where it's written;
 * otherwise it's written next to the source PDF with a "-converted.docx" suffix.
 */
class ManualPdfToDocxConversionTest {

    private final PdfExtractionServiceImpl extractionService =
            new PdfExtractionServiceImpl(new StructureRecoveryServiceImpl());

    private final WordWriterServiceImpl wordWriterService = new WordWriterServiceImpl();

    @Test
    void shouldConvertProvidedPdfToDocxFile() throws Exception {
        String pdfPath = System.getProperty("pdf.path");
        assumeTrue(pdfPath != null && !pdfPath.isBlank(),
                "Pass a PDF with -Dpdf.path=/absolute/path/to/file.pdf");

        Path source = Path.of(pdfPath);
        assumeTrue(Files.isRegularFile(source), "PDF file does not exist: " + source);

        String outPath = System.getProperty("docx.out");
        Path destination = (outPath != null && !outPath.isBlank())
                ? Path.of(outPath)
                : source.resolveSibling(
                        source.getFileName().toString().replaceFirst("\\.pdf$", "") + "-converted.docx"
                );

        byte[] pdfBytes = Files.readAllBytes(source);
        PdfExtractionResult result = extractionService.extract(pdfBytes);
        byte[] docx = wordWriterService.write(result);

        Files.write(destination, docx);

        int totalImages = result.getPages().stream()
                .mapToInt(p -> p.getImages().size())
                .sum();

        System.out.printf(
                "Converted %s -> %s (pages=%d, images=%d, bytes=%d)%n",
                source, destination, result.getPages().size(), totalImages, docx.length
        );
    }
}
