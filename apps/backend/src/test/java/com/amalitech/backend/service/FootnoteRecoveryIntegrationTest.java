package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.PdfExtractionServiceImpl;
import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FootnoteRecoveryIntegrationTest {

    private PdfExtractionService pdfExtractionService;

    @BeforeEach
    void setUp() {
        pdfExtractionService = new PdfExtractionServiceImpl(
                new StructureRecoveryServiceImpl()
        );
    }

    @Test
    void shouldNotTreatAFullSizeTableIdAsAFootnoteReference() throws Exception {

        byte[] pdfBytes = buildPdfWithTableIdColumnAndFootnoteArea();

        PdfExtractionResult result = pdfExtractionService.extract(pdfBytes);

        PageExtraction page = result.getPages().getFirst();

        StructuredBlock tableBlock = page.getStructuredBlocks().stream()
                .filter(block -> block.getType() == BlockType.TABLE)
                .findFirst()
                .orElseThrow();

        List<TableCell> idColumn = List.of(
                tableBlock.getTableRows().get(0).get(0),
                tableBlock.getTableRows().get(1).get(0)
        );

        for (TableCell cell : idColumn) {
            assertThat(cell.footnoteKey())
                    .as("a full-size table ID digit must not be mistaken for a footnote marker")
                    .isNull();

            assertThat(cell.text())
                    .as("the ID digit must not be silently stripped")
                    .isIn("1", "2");
        }
    }

    @Test
    void shouldStillRecoverAGenuineSmallFontFootnoteMarker() throws Exception {

        byte[] pdfBytes = buildPdfWithTableIdColumnAndFootnoteArea();

        PdfExtractionResult result = pdfExtractionService.extract(pdfBytes);

        assertThat(result.getFootnotes())
                .as("the trailing marker/text area should still become real footnotes")
                .hasSize(2);

        PageExtraction page = result.getPages().getFirst();

        boolean anyBlockCarriesTheFootnoteKey = page.getStructuredBlocks().stream()
                .anyMatch(block -> block.getFootnoteKey() != null);

        assertThat(anyBlockCarriesTheFootnoteKey)
                .as("the genuine small-font marker elsewhere on the page should still be linked")
                .isTrue();
    }

    private byte[] buildPdfWithTableIdColumnAndFootnoteArea() throws Exception {

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                String[][] cellText = {
                        {"1", "Apple"},
                        {"2", "Banana"}
                };

                float originX = 80f;
                float originTopY = 700f;
                float cellWidth = 100f;
                float cellHeight = 30f;

                for (int row = 0; row < cellText.length; row++) {
                    for (int col = 0; col < cellText[row].length; col++) {

                        float cellX = originX + (col * cellWidth);
                        float cellTopY = originTopY - (row * cellHeight);
                        float cellBottomY = cellTopY - cellHeight;

                        content.addRect(cellX, cellBottomY, cellWidth, cellHeight);
                        content.stroke();

                        content.beginText();
                        content.setFont(font, 12);
                        content.newLineAtOffset(cellX + 8, cellBottomY + 10);
                        content.showText(cellText[row][col]);
                        content.endText();
                    }
                }

                content.beginText();
                content.setFont(font, 6);
                content.newLineAtOffset(80f, 500f);
                content.showText("2");
                content.endText();

                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(90f, 500f);
                content.showText("Claim requiring a citation");
                content.endText();

                content.beginText();
                content.setFont(font, 6);
                content.newLineAtOffset(80f, 100f);
                content.showText("1");
                content.endText();

                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(90f, 100f);
                content.showText("First footnote text.");
                content.endText();

                content.beginText();
                content.setFont(font, 6);
                content.newLineAtOffset(260f, 100f);
                content.showText("2");
                content.endText();

                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(270f, 100f);
                content.showText("Second footnote text.");
                content.endText();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
