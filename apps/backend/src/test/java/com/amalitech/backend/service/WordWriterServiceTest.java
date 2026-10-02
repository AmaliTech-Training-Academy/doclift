package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.WordWriterServiceImpl;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WordWriterServiceTest {

    private final WordWriterService wordWriterService =
            new WordWriterServiceImpl();

    @Test
    void shouldApplyHeadingStyle() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock heading =
                new StructuredBlock(
                        0,
                        BlockType.HEADING,
                        null,
                        "Quarterly Results",
                        50,
                        50,
                        200,
                        20,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Quarterly Results",
                                        50,
                                        50,
                                        200,
                                        20,
                                        "Helvetica-Bold",
                                        16,
                                        true,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        page.getStructuredBlocks().add(heading);
        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            XWPFParagraph paragraph =
                    document.getParagraphs().getFirst();

            assertThat(paragraph.getText())
                    .isEqualTo("Quarterly Results");

            assertThat(paragraph.getStyle())
                    .isEqualTo("Heading1");
        }
    }

    @Test
    void shouldTrimWhitespaceAtParagraphBoundariesButPreserveInternalSpacing()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock paragraph =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Hello wonderful world",
                        50,
                        100,
                        250,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        " Hello",
                                        50,
                                        100,
                                        50,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        false
                                ),
                                new TextSpan(
                                        0,
                                        " wonderful ",
                                        100,
                                        100,
                                        80,
                                        12,
                                        "Helvetica-Bold",
                                        12,
                                        true,
                                        false,
                                        false,
                                        false
                                ),
                                new TextSpan(
                                        0,
                                        "world ",
                                        180,
                                        100,
                                        50,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        page.getStructuredBlocks().add(paragraph);
        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            String text =
                    document.getParagraphs()
                            .get(0)
                            .getText();

            assertThat(text)
                    .isEqualTo("Hello wonderful world");

            assertThat(text)
                    .doesNotStartWith(" ")
                    .doesNotEndWith(" ");
        }
    }

    @Test
    void shouldTrimLeadingWhitespaceAfterSeparateListMarkerSpan()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock listItem =
                new StructuredBlock(
                        0,
                        BlockType.LIST_ITEM,
                        ListType.ORDERED,
                        "1. First step",
                        50,
                        100,
                        150,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        "1.",
                                        50,
                                        100,
                                        15,
                                        12,
                                        "Helvetica-Bold",
                                        12,
                                        true,
                                        false,
                                        false,
                                        false
                                ),
                                new TextSpan(
                                        0,
                                        " First",
                                        70,
                                        100,
                                        40,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        false
                                ),
                                new TextSpan(
                                        0,
                                        " step ",
                                        115,
                                        100,
                                        35,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        page.getStructuredBlocks().add(listItem);
        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            XWPFParagraph paragraph =
                    document.getParagraphs().getFirst();

            assertThat(paragraph.getNumID())
                    .isNotNull();

            assertThat(paragraph.getText())
                    .isEqualTo("First step");

            assertThat(paragraph.getText())
                    .doesNotStartWith(" ")
                    .doesNotEndWith(" ");
        }
    }

    @Test
    void shouldPreserveCharacterFormatting() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock paragraph =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Normal Bold Italic Underline Large",
                        50,
                        100,
                        300,
                        20,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Normal ",
                                        50,
                                        100,
                                        50,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        false
                                ),

                                new TextSpan(
                                        0,
                                        "Bold ",
                                        100,
                                        100,
                                        50,
                                        12,
                                        "Helvetica-Bold",
                                        12,
                                        true,
                                        false,
                                        false,
                                        false
                                ),

                                new TextSpan(
                                        0,
                                        "Italic ",
                                        150,
                                        100,
                                        50,
                                        12,
                                        "Helvetica-Oblique",
                                        12,
                                        false,
                                        true,
                                        false,
                                        false
                                ),

                                new TextSpan(
                                        0,
                                        "Underline ",
                                        200,
                                        100,
                                        60,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        true,
                                        false
                                ),

                                new TextSpan(
                                        0,
                                        "Large",
                                        260,
                                        100,
                                        60,
                                        18,
                                        "Helvetica",
                                        18,
                                        false,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        page.getStructuredBlocks().add(paragraph);
        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            XWPFParagraph wordParagraph =
                    document.getParagraphs().getFirst();

            assertThat(wordParagraph.getRuns())
                    .hasSize(5);

            XWPFRun normal =
                    wordParagraph.getRuns().get(0);

            XWPFRun bold =
                    wordParagraph.getRuns().get(1);

            XWPFRun italic =
                    wordParagraph.getRuns().get(2);

            XWPFRun underline =
                    wordParagraph.getRuns().get(3);

            XWPFRun large =
                    wordParagraph.getRuns().get(4);

            assertThat(normal.isBold())
                    .isFalse();

            assertThat(normal.isItalic())
                    .isFalse();

            assertThat(bold.isBold())
                    .isTrue();

            assertThat(italic.isItalic())
                    .isTrue();

            assertThat(underline.getUnderline())
                    .isEqualTo(
                            org.apache.poi.xwpf.usermodel
                                    .UnderlinePatterns.SINGLE
                    );

            assertThat(large.getFontSize())
                    .isEqualTo(18);
        }
    }

    @Test
    void shouldCreateBulletList() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock first =
                listItem("• First item");

        StructuredBlock second =
                listItem("• Second item");

        page.getStructuredBlocks().add(first);
        page.getStructuredBlocks().add(second);

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            assertThat(document.getParagraphs())
                    .hasSize(2);

            XWPFParagraph firstParagraph =
                    document.getParagraphs().get(0);

            XWPFParagraph secondParagraph =
                    document.getParagraphs().get(1);

            assertThat(firstParagraph.getText())
                    .isEqualTo("First item");

            assertThat(secondParagraph.getText())
                    .isEqualTo("Second item");

            assertThat(firstParagraph.getNumID())
                    .isNotNull();

            assertThat(secondParagraph.getNumID())
                    .isEqualTo(
                            firstParagraph.getNumID()
                    );
        }
    }

    @Test
    void shouldInsertSpacesBetweenSeparateTextSpans()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock paragraph =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Hello, here is some text",
                        50,
                        100,
                        200,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Hello,",
                                        50,
                                        100,
                                        40,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        true
                                ),
                                new TextSpan(
                                        0,
                                        "here",
                                        95,
                                        100,
                                        30,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        true
                                ),
                                new TextSpan(
                                        0,
                                        "is",
                                        130,
                                        100,
                                        15,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        true
                                ),
                                new TextSpan(
                                        0,
                                        "some",
                                        150,
                                        100,
                                        35,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        true
                                ),
                                new TextSpan(
                                        0,
                                        "text",
                                        190,
                                        100,
                                        30,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        true
                                )
                        )
                );

        page.getStructuredBlocks().add(paragraph);
        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            assertThat(
                    document.getParagraphs()
                            .get(0)
                            .getText()
            ).isEqualTo(
                    "Hello, here is some text"
            );
        }
    }

    @Test
    void shouldCreateNumberedList() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                listItem("1. First step")
        );

        page.getStructuredBlocks().add(
                listItem("2. Second step")
        );

        page.getStructuredBlocks().add(
                listItem("3. Third step")
        );

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            assertThat(document.getParagraphs())
                    .hasSize(3);

            XWPFParagraph first =
                    document.getParagraphs().get(0);

            assertThat(first.getText())
                    .isEqualTo("First step");

            assertThat(
                    document.getParagraphs()
                            .get(1)
                            .getText()
            ).isEqualTo("Second step");

            assertThat(
                    document.getParagraphs()
                            .get(2)
                            .getText()
            ).isEqualTo("Third step");

            assertThat(
                    document.getParagraphs()
                            .get(0)
                            .getStyle()
            ).isEqualTo("Normal");

            assertThat(first.getNumID())
                    .isNotNull();

            assertThat(
                    document.getParagraphs()
                            .get(1)
                            .getNumID()
            ).isEqualTo(first.getNumID());

            assertThat(
                    document.getParagraphs()
                            .get(2)
                            .getNumID()
            ).isEqualTo(first.getNumID());
        }
    }

    @Test
    void shouldCreateValidDocxDocument() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        TextSpan span =
                new TextSpan(
                        0,
                        "Hello Word",
                        50,
                        100,
                        100,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                );

        StructuredBlock block =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Hello Word",
                        50,
                        100,
                        100,
                        12,
                        List.of(span)
                );

        page.getStructuredBlocks().add(block);

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        assertThat(docx)
                .isNotNull()
                .isNotEmpty();

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            assertThat(document.getParagraphs())
                    .hasSize(1);

            assertThat(
                    document.getParagraphs()
                            .getFirst()
                            .getText()
            ).isEqualTo("Hello Word");
        }
    }

    @Test
    void shouldWriteThreeByThreeTableWithCorrectRowColumnCountAndCellMapping()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        List<List<TableCell>> tableRows = List.of(
                List.of(
                        tableCell(0, 0, "R1C1"),
                        tableCell(0, 1, "R1C2"),
                        tableCell(0, 2, "R1C3")
                ),
                List.of(
                        tableCell(1, 0, "R2C1"),
                        tableCell(1, 1, "R2C2"),
                        tableCell(1, 2, "R2C3")
                ),
                List.of(
                        tableCell(2, 0, "R3C1"),
                        tableCell(2, 1, "R3C2"),
                        tableCell(2, 2, "R3C3")
                )
        );

        StructuredBlock tableBlock =
                new StructuredBlock(
                        0,
                        BlockType.TABLE,
                        "table",
                        50,
                        50,
                        300,
                        90,
                        List.of(),
                        tableRows
                );

        page.getStructuredBlocks().add(tableBlock);
        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            assertThat(document.getTables())
                    .hasSize(1);

            XWPFTable table =
                    document.getTables().getFirst();

            assertThat(table.getRows())
                    .hasSize(3);

            for (int row = 0; row < 3; row++) {

                assertThat(table.getRow(row).getTableCells())
                        .hasSize(3);

                for (int col = 0; col < 3; col++) {
                    assertThat(table.getRow(row).getCell(col).getText())
                            .isEqualTo("R" + (row + 1) + "C" + (col + 1));
                }
            }
        }
    }

    @Test
    void shouldWriteHorizontallyMergedHeaderCellWithGridSpan() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        TableCell europeAnchor =
                new TableCell(0, 1, 1, 3, "Europe", List.of(), null);

        TableCell coveredByEurope =
                new TableCell(0, 1, 0, 0, "", List.of(), null);

        List<List<TableCell>> tableRows = List.of(
                List.of(
                        tableCell(0, 0, "Continent"),
                        europeAnchor,
                        coveredByEurope,
                        coveredByEurope
                )
        );

        StructuredBlock tableBlock =
                new StructuredBlock(
                        0,
                        BlockType.TABLE,
                        "table",
                        0, 0, 400, 30,
                        List.of(),
                        tableRows
                );

        page.getStructuredBlocks().add(tableBlock);
        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            XWPFTable table =
                    document.getTables().getFirst();

            XWPFTableRow row =
                    table.getRow(0);

            assertThat(row.getTableCells())
                    .as("swallowed cells should not remain as separate physical cells")
                    .hasSize(2);

            assertThat(row.getCell(0).getText())
                    .isEqualTo("Continent");

            assertThat(row.getCell(1).getText())
                    .isEqualTo("Europe");

            assertThat(
                    row.getCell(1).getCTTc().getTcPr().getGridSpan().getVal().intValue()
            ).isEqualTo(3);
        }
    }

    @Test
    void shouldWriteVerticallyMergedCellWithVMergeRestartAndContinue() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        TableCell anchor =
                new TableCell(0, 0, 2, 1, "Spans two rows", List.of(), null);

        TableCell covered =
                new TableCell(0, 0, 0, 0, "", List.of(), null);

        List<List<TableCell>> tableRows = List.of(
                List.of(anchor, tableCell(0, 1, "Row 1")),
                List.of(covered, tableCell(1, 1, "Row 2"))
        );

        StructuredBlock tableBlock =
                new StructuredBlock(
                        0,
                        BlockType.TABLE,
                        "table",
                        0, 0, 200, 60,
                        List.of(),
                        tableRows
                );

        page.getStructuredBlocks().add(tableBlock);
        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            XWPFTable table =
                    document.getTables().getFirst();

            assertThat(table.getRows()).hasSize(2);

            assertThat(table.getRow(0).getCell(0).getText())
                    .isEqualTo("Spans two rows");

            assertThat(
                    table.getRow(0).getCell(0)
                            .getCTTc().getTcPr().getVMerge().getVal()
            ).isEqualTo(STMerge.RESTART);

            assertThat(table.getRow(1).getCell(0).getText())
                    .isEqualTo("");

            assertThat(
                    table.getRow(1).getCell(0)
                            .getCTTc().getTcPr().getVMerge().getVal()
            ).isEqualTo(STMerge.CONTINUE);

            assertThat(table.getRow(1).getCell(1).getText())
                    .isEqualTo("Row 2");
        }
    }

    private TableCell tableCell(int row, int column, String text) {
        return new TableCell(row, column, 1, 1, text, List.of(), null);
    }

    private StructuredBlock listItem(String text) {

        TextSpan span =
                new TextSpan(
                        0,
                        text,
                        50,
                        100,
                        150,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                );

        return new StructuredBlock(
                0,
                BlockType.LIST_ITEM,
                ListType.ORDERED,
                text,
                50,
                100,
                150,
                12,
                List.of(span)
        );
    }

    @Test
    void shouldNotInsertSpacesInsideWordAcrossFormattingRuns()
            throws Exception {

        List<TextSpan> spans =
                List.of(
                        new TextSpan(
                                0,
                                "im",
                                0f, 0f, 10f, 10f,
                                "Helvetica",
                                12f,
                                false,
                                false,
                                false,
                                false
                        ),
                        new TextSpan(
                                0,
                                "port",
                                10f, 0f, 20f, 10f,
                                "Helvetica-Bold",
                                12f,
                                true,
                                false,
                                false,
                                false
                        ),
                        new TextSpan(
                                0,
                                "ant",
                                30f, 0f, 15f, 10f,
                                "Helvetica",
                                12f,
                                false,
                                false,
                                false,
                                false
                        )
                );

        StructuredBlock block =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "important",
                        0f,
                        0f,
                        45f,
                        10f,
                        spans
                );

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks()
                .add(block);

        PdfExtractionResult result =
                new PdfExtractionResult();

        result.getPages()
                .add(page);

        byte[] bytes =
                wordWriterService.write(result);

        try (XWPFDocument document =
                     new XWPFDocument(
                             new ByteArrayInputStream(bytes)
                     )) {

            assertThat(
                    document.getParagraphs()
                            .getFirst()
                            .getText()
            ).isEqualTo("important");
        }
    }

    @Test
    void shouldInsertSpaceWhenWordSeparatorFlagIsPresent()
            throws Exception {

        List<TextSpan> spans =
                List.of(
                        new TextSpan(
                                0,
                                "Hello",
                                0f, 0f, 25f, 10f,
                                "Helvetica",
                                12f,
                                false,
                                false,
                                false,
                                false
                        ),
                        new TextSpan(
                                0,
                                "world",
                                30f, 0f, 25f, 10f,
                                "Helvetica-Bold",
                                12f,
                                true,
                                false,
                                false,
                                true
                        )
                );

        StructuredBlock block =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Hello world",
                        0f,
                        0f,
                        55f,
                        10f,
                        spans
                );

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks()
                .add(block);

        PdfExtractionResult result =
                new PdfExtractionResult();

        result.getPages()
                .add(page);

        byte[] bytes =
                wordWriterService.write(result);

        try (XWPFDocument document =
                     new XWPFDocument(
                             new ByteArrayInputStream(bytes)
                     )) {

            assertThat(
                    document.getParagraphs()
                            .getFirst()
                            .getText()
            ).isEqualTo("Hello world");
        }
    }

}
