package com.amalitech.backend.service;

import com.amalitech.backend.service.implementation.PdfExtractionServiceImpl;
import com.amalitech.backend.service.implementation.PdfValidationServiceImpl;
import com.amalitech.backend.service.interfaces.PdfExtractionService;
import com.amalitech.backend.service.interfaces.PdfValidationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.springframework.mock.web.MockMultipartFile;

class PdfExtractionOutputTest {

    private final PdfExtractionService service;
    private final PdfValidationService validationService;

    {
        service = new PdfExtractionServiceImpl();
        validationService = new PdfValidationServiceImpl(10 * 1024 * 1024);
    }

    @Test
    void shouldPrintStructuredExtractionOutput() throws Exception {
        byte[] pdfBytes = buildSamplePdf();
        validatePdf(pdfBytes, "sample.pdf");
        printResult(service.extract(pdfBytes));
    }

    @Test
    void shouldPrintStructuredExtractionOutputForProvidedPdf() throws Exception {
        String pdfPath = System.getProperty("pdf.path");
        assumeTrue(pdfPath != null && !pdfPath.isBlank(),
                "Pass a PDF with -Dpdf.path=/absolute/path/to/file.pdf");

        Path path = Path.of(pdfPath);
        assumeTrue(Files.isRegularFile(path), "PDF file does not exist: " + path);
        byte[] pdfBytes = Files.readAllBytes(path);
        validatePdf(pdfBytes, path.getFileName().toString());
        printResult(service.extract(pdfBytes));
    }

    private void validatePdf(byte[] pdfBytes, String fileName) {
        int pageCount = validationService.validateAndGetPageCount(
                new MockMultipartFile(
                        "file",
                        fileName,
                        "application/pdf",
                        pdfBytes
                )
        );

        System.out.printf("Validated PDF: %s page(s)%n", pageCount);
    }

    private void printResult(PdfExtractionResult result) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("=".repeat(80));
        System.out.println();

        for (PageExtraction page : result.getPages()) {

            // Text Spans
            System.out.println("│ TEXT SPANS (" + page.getTextSpans().size() + ")");
            if (page.getTextSpans().isEmpty()) {
                System.out.println("│   (none)");
            } else {
                for (TextSpan span : page.getTextSpans()) {
                    System.out.printf("│   • \"%s\"%n", span.getText());
                    System.out.printf("│     Position: (%.2f, %.2f) | Size: %.2f x %.2f%n",
                            span.getX(), span.getY(), span.getWidth(), span.getHeight());
                    System.out.printf("│     Font: %s | Size: %.1fpt%n", span.getFontName(), span.getFontSize());
                }
            }
            System.out.println("│");

            // Images
            System.out.println("│ IMAGES (" + page.getImages().size() + ")");
            if (page.getImages().isEmpty()) {
                System.out.println("│   (none)");
            } else {
                for (ExtractedImage image : page.getImages()) {
                    System.out.printf("│   • %s%n", image.getImageName());
                    System.out.printf("│     Resolution: %dx%d pixels%n",
                            image.getPixelsWidth(), image.getPixelsHeight());
                }
            }
            System.out.println("│");

            // Tables
            System.out.println("│ CANDIDATE TABLE REGIONS (" + page.getCandidateTableRegions().size() + ")");
            if (page.getCandidateTableRegions().isEmpty()) {
                System.out.println("│   (none)");
            } else {
                for (TableRegion region : page.getCandidateTableRegions()) {
                    System.out.printf("│   • Table: %d rows × %d columns%n",
                            region.getRowCount(), region.getColumnCount());
                    System.out.printf("│     Bounds: (%.2f, %.2f) | Size: %.2f x %.2f%n",
                            region.getX(), region.getY(), region.getWidth(), region.getHeight());
                }
            }
            System.out.println();
        }

        System.out.println();
    }

    private byte[] buildSamplePdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            addPage(document, "Quarterly Results", true);
            addPage(document, "Summary Page", false);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private void addPage(PDDocument document, String title, boolean includeImage) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false)) {
            PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            content.beginText();
            content.setFont(headerFont, 16);
            content.newLineAtOffset(80, 760);
            content.showText(title);
            content.endText();

            content.beginText();
            content.setFont(bodyFont, 12);
            content.newLineAtOffset(90, 700);
            content.showText("Jan");
            content.endText();

            content.beginText();
            content.setFont(bodyFont, 12);
            content.newLineAtOffset(220, 700);
            content.showText("Feb");
            content.endText();

            content.beginText();
            content.setFont(bodyFont, 12);
            content.newLineAtOffset(350, 700);
            content.showText("Mar");
            content.endText();

            if (includeImage) {
                content.drawImage(createSampleImage(document), 390, 610, 90, 45);
            }
        }
    }

    private PDImageXObject createSampleImage(PDDocument document) throws IOException {
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 100, 50);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(10, 10, 80, 30);
        graphics.dispose();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", stream);
        return PDImageXObject.createFromByteArray(document, stream.toByteArray(), "sample-chart");
    }
}
