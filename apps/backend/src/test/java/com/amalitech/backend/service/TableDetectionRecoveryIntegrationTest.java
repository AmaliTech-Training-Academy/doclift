package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.PdfExtractionServiceImpl;
import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import com.amalitech.backend.service.impl.WordWriterServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for DOC-2-T8: a bordered table sample should be
 * detected, recovered as a real cell grid, and written out as an
 * editable Word table with cell text mapped to the correct positions.
 */
class TableDetectionRecoveryIntegrationTest {

    private PdfExtractionService pdfExtractionService;
    private WordWriterService wordWriterService;

    @BeforeEach
    void setUp() {
        pdfExtractionService = new PdfExtractionServiceImpl(
                new StructureRecoveryServiceImpl()
        );

        wordWriterService = new WordWriterServiceImpl();
    }

    @Test
    void shouldConvertThreeByThreeBorderedTableSampleToRealWordTable()
            throws Exception {

        String[][] cellText = {
                {"Name", "Age", "City"},
                {"Alice", "30", "Accra"},
                {"Bob", "25", "Kumasi"}
        };

        byte[] pdfBytes = buildBorderedTablePdf(cellText);

        PdfExtractionResult extractionResult =
                pdfExtractionService.extract(pdfBytes);

        assertThat(extractionResult.getPages()).hasSize(1);

        PageExtraction page =
                extractionResult.getPages().getFirst();

        StructuredBlock tableBlock = page.getStructuredBlocks().stream()
                .filter(block -> block.getType() == BlockType.TABLE)
                .findFirst()
                .orElse(null);

        assertThat(tableBlock)
                .as("A TABLE block should be recovered for the bordered grid")
                .isNotNull();

        assertThat(tableBlock.getTableRows())
                .as("row count")
                .hasSize(3);

        for (int row = 0; row < 3; row++) {

            assertThat(tableBlock.getTableRows().get(row))
                    .as("column count for row " + row)
                    .hasSize(3);

            for (int col = 0; col < 3; col++) {
                assertThat(
                        tableBlock.getTableRows().get(row).get(col).text()
                ).isEqualTo(cellText[row][col]);
            }
        }

        byte[] docx = wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(new ByteArrayInputStream(docx))
        ) {

            assertThat(document.getTables())
                    .as("a real editable Word table should exist")
                    .hasSize(1);

            XWPFTable wordTable =
                    document.getTables().getFirst();

            assertThat(wordTable.getRows()).hasSize(3);

            for (int row = 0; row < 3; row++) {

                assertThat(wordTable.getRow(row).getTableCells())
                        .hasSize(3);

                for (int col = 0; col < 3; col++) {
                    assertThat(wordTable.getRow(row).getCell(col).getText())
                            .isEqualTo(cellText[row][col]);
                }
            }
        }
    }

    private byte[] buildBorderedTablePdf(String[][] cellText)
            throws Exception {

        int rows = cellText.length;
        int columns = cellText[0].length;

        float originX = 80f;
        float originTopY = 700f;
        float cellWidth = 100f;
        float cellHeight = 30f;

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA
            );

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < columns; col++) {

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
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
