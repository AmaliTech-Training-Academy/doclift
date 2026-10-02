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

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentMetricsServiceTest {

    private DocumentMetricsService documentMetricsService;
    private WordWriterService wordWriterService;

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
}