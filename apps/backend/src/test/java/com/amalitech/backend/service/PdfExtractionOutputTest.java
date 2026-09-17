package com.amalitech.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfExtractionOutputTest {

    private PdfExtractionService service;

    @BeforeEach
    void setUp() {
        PdfValidationService validationService = new PdfValidationService(10485760);
        service = new PdfExtractionService(validationService);
    }

    @Test
    void shouldPrintStructuredExtractionOutput() throws Exception {
        byte[] pdfBytes = buildSamplePdf();
        printResult(service.extract(pdfBytes));
    }

    @Test
    void shouldPrintStructuredExtractionOutputForProvidedPdf() throws Exception {
        String pdfPath = System.getProperty("pdf.path");
        assumeTrue(pdfPath != null && !pdfPath.isBlank(),
                "Pass a PDF with -Dpdf.path=/absolute/path/to/file.pdf");

        Path path = Path.of(pdfPath);
        assumeTrue(Files.isRegularFile(path), "PDF file does not exist: " + path);
        printResult(service.extract(Files.readAllBytes(path)));
    }

    private void printResult(PdfExtractionResult result) {
        System.out.println("=== PDF extraction output ===");
        System.out.println("pages=" + result.getPages().size());

        for (PageExtraction page : result.getPages()) {
            System.out.println("pageIndex=" + page.getPageIndex());
            System.out.println("textSpans=" + page.getTextSpans().size());
            for (TextSpan span : page.getTextSpans()) {
                System.out.println("  text='" + span.getText() + "' x=" + span.getX() + " y=" + span.getY()
                        + " font=" + span.getFontName() + " size=" + span.getFontSize());
            }

            System.out.println("images=" + page.getImages().size());
            for (ExtractedImage image : page.getImages()) {
                System.out.println("  image=" + image.getImageName() + " size=" + image.getPixelsWidth() + "x" + image.getPixelsHeight());
            }

            System.out.println("candidateTables=" + page.getCandidateTableRegions().size());
            for (TableRegion region : page.getCandidateTableRegions()) {
                System.out.println("  table rows=" + region.getRowCount() + " cols=" + region.getColumnCount()
                        + " bounds=" + region.getX() + "," + region.getY() + "," + region.getWidth() + "," + region.getHeight());
            }
        }
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
