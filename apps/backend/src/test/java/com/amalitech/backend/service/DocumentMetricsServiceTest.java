package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.DocumentMetricsServiceImpl;
import com.amalitech.backend.service.impl.WordWriterServiceImpl;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentMetricsServiceTest {

    private DocumentMetricsService documentMetricsService;
    private WordWriterService wordWriterService;

    private PdfExtractionResult extractionResultWithBlocks(
            StructuredBlock... blocks
    ) {
        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks()
                .addAll(List.of(blocks));

        result.getPages().add(page);

        return result;
    }

    private StructuredBlock listBlock(
            String text,
            ListType listType
    ) {
        return new StructuredBlock(
                0,
                BlockType.LIST_ITEM,
                listType,
                text,
                0,
                0,
                100,
                20,
                List.of()
        );
    }

    private StructuredBlock headingBlock(
            String text,
            float fontSize
    ) {
        return new StructuredBlock(
                0,
                BlockType.HEADING,
                text,
                0,
                0,
                100,
                20,
                List.of(
                        new TextSpan(
                                0,
                                text,
                                0,
                                0,
                                100,
                                20,
                                "Helvetica",
                                fontSize,
                                false,
                                false,
                                false,
                                false
                        )
                )
        );
    }

    @BeforeEach
    void setUp() {
        documentMetricsService =
                new DocumentMetricsServiceImpl();
        wordWriterService =
                new WordWriterServiceImpl();
    }

    @Test
    void shouldReturnZeroForNullText() {

        assertEquals(
                0,
                documentMetricsService.countWords(null)
        );
    }

    @Test
    void shouldReturnZeroForBlankText() {

        assertEquals(
                0,
                documentMetricsService.countWords("   ")
        );
    }

    @Test
    void shouldCountSimpleWords() {

        assertEquals(
                3,
                documentMetricsService.countWords(
                        "Hello from DocLift"
                )
        );
    }

    @Test
    void shouldIgnorePunctuation() {

        assertEquals(
                5,
                documentMetricsService.countWords(
                        "Hello, world! How are you?"
                )
        );
    }

    @Test
    void shouldHandleMultipleWhitespaceCharacters() {

        assertEquals(
                4,
                documentMetricsService.countWords(
                        "One   two\nthree\tfour"
                )
        );
    }

    @Test
    void shouldTreatHyphenatedWordAsOneWord() {

        assertEquals(
                2,
                documentMetricsService.countWords(
                        "PDF-to-Word converter"
                )
        );
    }

    @Test
    void shouldTreatApostropheWordAsOneWord() {

        assertEquals(
                2,
                documentMetricsService.countWords(
                        "don't stop"
                )
        );
    }

    @Test
    void shouldCountWordsAcrossStructuredBlocks() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock firstBlock =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Hello from DocLift",
                        0,
                        0,
                        100,
                        20,
                        List.of()
                );

        StructuredBlock secondBlock =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "PDF-to-Word conversion works.",
                        0,
                        30,
                        100,
                        20,
                        List.of()
                );

        page.getStructuredBlocks().add(firstBlock);
        page.getStructuredBlocks().add(secondBlock);

        result.getPages().add(page);

        assertEquals(
                6,
                documentMetricsService.countSourceWords(result)
        );
    }

    @Test
    void shouldCountOrderedListAsSingleList() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                listBlock(
                        "1. First",
                        ListType.ORDERED
                )
        );

        page.getStructuredBlocks().add(
                listBlock(
                        "2. Second",
                        ListType.ORDERED
                )
        );

        page.getStructuredBlocks().add(
                listBlock(
                        "3. Third",
                        ListType.ORDERED
                )
        );

        result.getPages().add(page);

        ListCountResult counts =
                documentMetricsService.countSourceLists(result);

        assertEquals(1, counts.ordered());
        assertEquals(0, counts.unordered());
    }

    @Test
    void shouldCountSeparateOrderedAndUnorderedLists() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                listBlock("1. First", ListType.ORDERED)
        );

        page.getStructuredBlocks().add(
                listBlock("2. Second", ListType.ORDERED)
        );

        page.getStructuredBlocks().add(
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Some paragraph",
                        0,
                        0,
                        100,
                        20,
                        List.of()
                )
        );

        page.getStructuredBlocks().add(
                listBlock("• Java", ListType.UNORDERED)
        );

        page.getStructuredBlocks().add(
                listBlock("• Spring", ListType.UNORDERED)
        );

        result.getPages().add(page);

        ListCountResult counts =
                documentMetricsService.countSourceLists(result);

        assertEquals(1, counts.ordered());
        assertEquals(1, counts.unordered());
    }

    @Test
    void shouldReturnZeroForNullExtractionResult() {

        assertEquals(
                0,
                documentMetricsService.countSourceWords(null)
        );
    }

    @Test
    void shouldReturnZeroWhenExtractionHasNoPages() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        assertEquals(
                0,
                documentMetricsService.countSourceWords(result)
        );
    }

    @Test
    void shouldCountWordsInGeneratedDocx() throws Exception {

        byte[] docxBytes;

        try (
                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream();

                XWPFDocument document =
                        new XWPFDocument()
        ) {
            XWPFParagraph paragraph =
                    document.createParagraph();

            paragraph.createRun()
                    .setText("Hello from DocLift");

            document.write(outputStream);

            docxBytes = outputStream.toByteArray();
        }

        assertEquals(
                3,
                documentMetricsService.countOutputWords(docxBytes)
        );
    }

    @Test
    void shouldCountWordsInDocxTables() throws Exception {

        byte[] docxBytes;

        try (
                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream();

                XWPFDocument document =
                        new XWPFDocument()
        ) {
            XWPFTable table =
                    document.createTable(1, 2);

            table.getRow(0)
                    .getCell(0)
                    .setText("Hello world");

            table.getRow(0)
                    .getCell(1)
                    .setText("DocLift works");

            document.write(outputStream);

            docxBytes = outputStream.toByteArray();
        }

        assertEquals(
                4,
                documentMetricsService.countOutputWords(docxBytes)
        );
    }

    @Test
    void shouldReturnZeroForEmptyDocxBytes() {

        assertEquals(
                0,
                documentMetricsService.countOutputWords(
                        new byte[0]
                )
        );
    }

    @Test
    void shouldCountOneReconstructedOrderedList() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                listBlock(
                        "1. First item",
                        ListType.ORDERED
                )
        );

        page.getStructuredBlocks().add(
                listBlock(
                        "2. Second item",
                        ListType.ORDERED
                )
        );

        page.getStructuredBlocks().add(
                listBlock(
                        "3. Third item",
                        ListType.ORDERED
                )
        );

        result.getPages().add(page);

        byte[] docx =
                wordWriterService.write(result);

        ListCountResult counts =
                documentMetricsService.countOutputLists(docx);

        assertEquals(1, counts.ordered());
        assertEquals(0, counts.unordered());
    }

    @Test
    void shouldCountOneReconstructedUnorderedList() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                listBlock(
                        "• Java",
                        ListType.UNORDERED
                )
        );

        page.getStructuredBlocks().add(
                listBlock(
                        "• Spring Boot",
                        ListType.UNORDERED
                )
        );

        page.getStructuredBlocks().add(
                listBlock(
                        "• PostgreSQL",
                        ListType.UNORDERED
                )
        );

        result.getPages().add(page);

        byte[] docx =
                wordWriterService.write(result);

        ListCountResult counts =
                documentMetricsService.countOutputLists(docx);

        assertEquals(0, counts.ordered());
        assertEquals(1, counts.unordered());
    }

    @Test
    void shouldCountSeparateReconstructedOrderedAndUnorderedLists() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                listBlock("1. First", ListType.ORDERED)
        );

        page.getStructuredBlocks().add(
                listBlock("2. Second", ListType.ORDERED)
        );

        page.getStructuredBlocks().add(
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Some paragraph",
                        0,
                        0,
                        100,
                        20,
                        List.of()
                )
        );

        page.getStructuredBlocks().add(
                listBlock("• Java", ListType.UNORDERED)
        );

        page.getStructuredBlocks().add(
                listBlock("• Spring Boot", ListType.UNORDERED)
        );

        result.getPages().add(page);

        byte[] docx =
                wordWriterService.write(result);

        ListCountResult counts =
                documentMetricsService.countOutputLists(docx);

        assertEquals(1, counts.ordered());
        assertEquals(1, counts.unordered());
    }

    @Test
    void countSourceWordsShouldIgnoreDecimalListMarker() {

        PdfExtractionResult extractionResult =
                extractionResultWithBlocks(
                        new StructuredBlock(
                                0,
                                BlockType.LIST_ITEM,
                                ListType.ORDERED,
                                "1. First item",
                                0,
                                0,
                                100,
                                12,
                                List.of()
                        )
                );

        assertThat(
                documentMetricsService
                        .countSourceWords(
                                extractionResult
                        )
        ).isEqualTo(2);
    }

    @Test
    void sourceAndOutputWordCountsShouldMatchForOrderedList()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                new StructuredBlock(
                        0,
                        BlockType.LIST_ITEM,
                        ListType.ORDERED,
                        "1. First item",
                        0,
                        0,
                        100,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        "1. First item",
                                        0,
                                        0,
                                        100,
                                        12,
                                        "Helvetica",
                                        12,
                                        false,
                                        false,
                                        false,
                                        false
                                )
                        )
                )
        );

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        int sourceCount =
                documentMetricsService
                        .countSourceWords(
                                extractionResult
                        );

        int outputCount =
                documentMetricsService
                        .countOutputWords(
                                docx
                        );

        assertThat(sourceCount)
                .isEqualTo(2);

        assertThat(outputCount)
                .isEqualTo(2);
    }

    @Test
    void countSourceWordsShouldIgnoreRomanListMarker() {

        PdfExtractionResult result =
                extractionResultWithBlocks(
                        listBlock(
                                "iv. Final results",
                                ListType.ORDERED
                        )
                );

        assertThat(
                documentMetricsService
                        .countSourceWords(result)
        ).isEqualTo(2);
    }

    @Test
    void countSourceWordsShouldIgnoreParenthesizedListMarker() {

        PdfExtractionResult result =
                extractionResultWithBlocks(
                        listBlock(
                                "(a) First item",
                                ListType.ORDERED
                        )
                );

        assertThat(
                documentMetricsService
                        .countSourceWords(result)
        ).isEqualTo(2);
    }

    @Test
    void countSourceWordsShouldIgnoreBulletMarker() {

        PdfExtractionResult result =
                extractionResultWithBlocks(
                        listBlock(
                                "✓ Blue diamond",
                                ListType.UNORDERED
                        )
                );

        assertThat(
                documentMetricsService
                        .countSourceWords(result)
        ).isEqualTo(2);
    }

    @Test
    void countSourceWordsShouldNotStripMarkerFromParagraph() {

        PdfExtractionResult result =
                extractionResultWithBlocks(
                        new StructuredBlock(
                                0,
                                BlockType.PARAGRAPH,
                                null,
                                "1. First item",
                                0,
                                0,
                                100,
                                12,
                                List.of()
                        )
                );

        assertThat(
                documentMetricsService
                        .countSourceWords(result)
        ).isEqualTo(3);
    }

    @Test
    void shouldCountHeadingsAmongOtherBlocks() {

        PdfExtractionResult result =
                extractionResultWithBlocks(
                        headingBlock("Title", 24f),
                        new StructuredBlock(
                                0,
                                BlockType.PARAGRAPH,
                                null,
                                "Body text",
                                0,
                                0,
                                100,
                                20,
                                List.of()
                        ),
                        headingBlock("Subtitle", 18f)
                );

        assertEquals(
                2,
                documentMetricsService.countHeadings(result)
        );
    }

    @Test
    void countHeadingsShouldReturnZeroForNullExtractionResult() {

        assertEquals(
                0,
                documentMetricsService.countHeadings(null)
        );
    }

    @Test
    void countHeadingsShouldReturnZeroWhenExtractionHasNoPages() {

        assertEquals(
                0,
                documentMetricsService.countHeadings(
                        new PdfExtractionResult()
                )
        );
    }

    @Test
    void shouldBucketHeadingsIntoThreeLevelsByFontSize() {

        PdfExtractionResult result =
                extractionResultWithBlocks(
                        headingBlock("Chapter 1", 24f),
                        headingBlock("Section A", 18f),
                        headingBlock("Section B", 18f),
                        headingBlock("Sub-point", 14f),
                        headingBlock("Another sub-point", 13f)
                );

        HeadingLevelCountResult levels =
                documentMetricsService.countHeadingLevels(result);

        assertEquals(1, levels.levelOne());
        assertEquals(2, levels.levelTwo());
        assertEquals(2, levels.levelThree());
    }

    @Test
    void countHeadingLevelsShouldReturnZeroesForNullExtractionResult() {

        HeadingLevelCountResult levels =
                documentMetricsService.countHeadingLevels(null);

        assertEquals(0, levels.levelOne());
        assertEquals(0, levels.levelTwo());
        assertEquals(0, levels.levelThree());
    }

    @Test
    void countHeadingLevelsShouldReturnZeroesWhenNoHeadingsPresent() {

        PdfExtractionResult result =
                extractionResultWithBlocks(
                        new StructuredBlock(
                                0,
                                BlockType.PARAGRAPH,
                                null,
                                "Body text",
                                0,
                                0,
                                100,
                                20,
                                List.of()
                        )
                );

        HeadingLevelCountResult levels =
                documentMetricsService.countHeadingLevels(result);

        assertEquals(0, levels.levelOne());
        assertEquals(0, levels.levelTwo());
        assertEquals(0, levels.levelThree());
    }

    @Test
    void shouldSumDetectedTablesAcrossPages() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction firstPage = new PageExtraction(0);
        firstPage.setTableCount(2);

        PageExtraction secondPage = new PageExtraction(1);
        secondPage.setTableCount(1);

        result.getPages().add(firstPage);
        result.getPages().add(secondPage);

        assertEquals(
                3,
                documentMetricsService.countTables(result)
        );
    }

    @Test
    void countTablesShouldReturnZeroForNullExtractionResult() {

        assertEquals(
                0,
                documentMetricsService.countTables(null)
        );
    }

    @Test
    void countTablesShouldReturnZeroWhenExtractionHasNoPages() {

        assertEquals(
                0,
                documentMetricsService.countTables(
                        new PdfExtractionResult()
                )
        );
    }

    @Test
    void shouldSumDetectedImagesAcrossPages() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction firstPage = new PageExtraction(0);

        firstPage.getImages().add(
                new ExtractedImage(
                        0,
                        "image1.png",
                        0,
                        0,
                        10,
                        10,
                        100,
                        100,
                        "image/png",
                        0f,
                        false,
                        new byte[0]
                )
        );

        PageExtraction secondPage = new PageExtraction(1);

        secondPage.getImages().add(
                new ExtractedImage(
                        1,
                        "image2.png",
                        0,
                        0,
                        10,
                        10,
                        100,
                        100,
                        "image/png",
                        0f,
                        false,
                        new byte[0]
                )
        );

        secondPage.getImages().add(
                new ExtractedImage(
                        1,
                        "image3.png",
                        0,
                        0,
                        10,
                        10,
                        100,
                        100,
                        "image/png",
                        0f,
                        false,
                        new byte[0]
                )
        );

        result.getPages().add(firstPage);
        result.getPages().add(secondPage);

        assertEquals(
                3,
                documentMetricsService.countImages(result)
        );
    }

    @Test
    void countImagesShouldReturnZeroForNullExtractionResult() {

        assertEquals(
                0,
                documentMetricsService.countImages(null)
        );
    }

    @Test
    void countImagesShouldReturnZeroWhenExtractionHasNoPages() {

        assertEquals(
                0,
                documentMetricsService.countImages(
                        new PdfExtractionResult()
                )
        );
    }

    @Test
    void shouldCountOnlyMultiColumnPages() {

        PdfExtractionResult result =
                new PdfExtractionResult();

        PageExtraction singleColumnPage = new PageExtraction(0);
        singleColumnPage.setMultiColumn(false);

        PageExtraction multiColumnPage = new PageExtraction(1);
        multiColumnPage.setMultiColumn(true);

        result.getPages().add(singleColumnPage);
        result.getPages().add(multiColumnPage);

        assertEquals(
                1,
                documentMetricsService.countMultiColumnPages(result)
        );
    }

    @Test
    void countMultiColumnPagesShouldReturnZeroForNullExtractionResult() {

        assertEquals(
                0,
                documentMetricsService.countMultiColumnPages(null)
        );
    }

    @Test
    void countMultiColumnPagesShouldReturnZeroWhenExtractionHasNoPages() {

        assertEquals(
                0,
                documentMetricsService.countMultiColumnPages(
                        new PdfExtractionResult()
                )
        );
    }

}