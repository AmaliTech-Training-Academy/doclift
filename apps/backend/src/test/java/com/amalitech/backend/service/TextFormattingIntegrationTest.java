package com.amalitech.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class TextFormattingIntegrationTest {

    @Autowired
    private PdfExtractionService pdfExtractionService;

    @Autowired
    private WordWriterService wordWriterService;

    @Test
    void preservesTextColorFromPdfToWord() throws Exception {

        byte[] pdfBytes;

        try (
                PDDocument document = new PDDocument();
                ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream()
        ) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                content.beginText();

                content.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA
                        ),
                        18
                );

                content.setNonStrokingColor(
                        new Color(255, 0, 0)
                );

                content.newLineAtOffset(100, 700);
                content.showText("Red text");

                content.endText();
            }

            document.save(pdfOutput);
            pdfBytes = pdfOutput.toByteArray();
        }

        PdfExtractionResult extractionResult;

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            extractionResult =
                    pdfExtractionService.extract(document);
        }

        TextSpan extractedSpan =
                extractionResult.getPages().getFirst()
                        .getTextSpans().stream()
                        .filter(span ->
                                span.getText() != null
                                        && span.getText().contains("Red text")
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                "FF0000",
                extractedSpan.getColorHex(),
                "PDF extraction should preserve the source text color"
        );

        byte[] docxBytes =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument wordDocument =
                        new XWPFDocument(
                                new ByteArrayInputStream(docxBytes)
                        )
        ) {

            XWPFRun matchingRun =
                    wordDocument.getParagraphs().stream()
                            .flatMap(paragraph ->
                                    paragraph.getRuns().stream()
                            )
                            .filter(run ->
                                    run.text().contains("Red text")
                            )
                            .findFirst()
                            .orElse(null);

            assertNotNull(
                    matchingRun,
                    "Expected converted Word document to contain 'Red text'"
            );

            assertEquals(
                    "FF0000",
                    matchingRun.getColor()
            );
        }
    }
}