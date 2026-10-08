package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureRecoveryServiceTest {

    private StructureRecoveryService structureRecoveryService;

    @BeforeEach
    void setUp() {
        structureRecoveryService = new StructureRecoveryServiceImpl();
    }

    private TextSpan textSpan(
            int pageIndex,
            String text,
            float x,
            float y,
            float width,
            float height
    ) {
        return new TextSpan(
                pageIndex,
                text,
                x,
                y,
                width,
                height,
                "Helvetica",
                height,
                false,
                false,
                false,
                true
        );
    }

    // =========================================================
    // SINGLE-COLUMN READING ORDER
    // =========================================================

    @Test
    void shouldOrderSingleColumnTopToBottom() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("Third line", 50, 140),
                span("First line", 50, 100),
                span("Second line", 50, 120)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "First line Second line Third line"
                );
    }

    // =========================================================
    // SAME-LINE GROUPING
    // =========================================================

    @Test
    void shouldGroupSpansOnSameLineLeftToRight() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("world", 110, 100, true),
                span("Hello", 50, 100, false)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks()).hasSize(1);

        assertThat(page.getStructuredBlocks().getFirst().getText())
                .isEqualTo("Hello world");
    }

    // =========================================================
    // DIFFERENT LINES
    // =========================================================

    @Test
    void shouldKeepVerticallySeparatedSpansOnDifferentLines() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("First line", 50, 100),
                span("Second line", 50, 130)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "First line",
                        "Second line"
                );
    }

    // =========================================================
    // EMPTY PAGE
    // =========================================================

    @Test
    void shouldReturnNoBlocksForEmptyPage() {
        PageExtraction page = new PageExtraction(0);

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks()).isEmpty();
    }

    // =========================================================
    // TEST DATA
    // =========================================================

    private TextSpan span(
            String text,
            float x,
            float y
    ) {
        return new TextSpan(
                0,
                text,
                x,
                y,
                50,
                10,
                "Times-Roman",
                11,
                false,
                false,
                false,
                false
        );
    }
    private TextSpan span(
            String text,
            float x,
            float y,
            boolean wordSeparatorBefore
    ) {
        return new TextSpan(
                0,
                text,
                x,
                y,
                50,
                10,
                "Times-Roman",
                11,
                false,
                false,
                false,
                wordSeparatorBefore
        );
    }

    // =========================================================
// TWO-COLUMN READING ORDER
// =========================================================

    @Test
    void shouldOrderTwoColumnLayoutColumnByColumn() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("Right one", 320, 100),
                span("Left one", 50, 100),

                span("Right two", 320, 120),
                span("Left two", 50, 120),

                span("Right three", 320, 140),
                span("Left three", 50, 140)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Left one Left two Left three",
                        "Right one Right two Right three"
                );
    }

    // =========================================================
// SPANNING HEADING + TWO-COLUMN READING ORDER
// =========================================================

    @Test
    void shouldPlaceSpanningHeadingBeforeColumns() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                spanWithSize(
                        "Document Title",
                        50,
                        50,
                        500,
                        18,
                        "Times-Bold"
                ),

                span("Left one", 50, 100),
                span("Right one", 320, 100),

                span("Left two", 50, 120),
                span("Right two", 320, 120),

                span("Left three", 50, 140),
                span("Right three", 320, 140)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Document Title",
                        "Left one Left two Left three",
                        "Right one Right two Right three"
                );
    }

    @Test
    void shouldPreserveReadingOrderWithSpanningHeadingBetweenColumnSections() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("Left one", 50, 100),
                span("Right one", 320, 100),

                span("Left two", 50, 120),
                span("Right two", 320, 120),

                span("Left three", 50, 140),
                span("Right three", 320, 140),

                spanWithSize(
                        "Middle Section Heading",
                        50,
                        200,
                        500,
                        18,
                        "Times-Bold"
                ),

                span("Left four", 50, 300),
                span("Right four", 320, 300),

                span("Left five", 50, 320),
                span("Right five", 320, 320),

                span("Left six", 50, 340),
                span("Right six", 320, 340)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Left one Left two Left three",
                        "Right one Right two Right three",
                        "Middle Section Heading",
                        "Left four Left five Left six",
                        "Right four Right five Right six"
                );

        assertThat(page.getStructuredBlocks().get(2).getType())
                .isEqualTo(BlockType.HEADING);
    }

    @Test
    void shouldPreserveReadingOrderWithSpanningFooterAfterTwoColumns() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("Right one", 320, 100),
                span("Left one", 50, 100),

                span("Right two", 320, 120),
                span("Left two", 50, 120),

                span("Right three", 320, 140),
                span("Left three", 50, 140),

                spanWithSize(
                        "Page 1 of 1 - Confidential Document",
                        50,
                        500,
                        500,
                        11,
                        "Times-Roman"
                )
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Left one Left two Left three",
                        "Right one Right two Right three",
                        "Page 1 of 1 - Confidential Document"
                );
    }

    @Test
    void shouldPreserveFullDocumentLayoutWithSpanningHeaderMiddleHeadingAndFooter() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                spanWithSize("Main Title", 50, 30, 500, 20, "Times-Bold"),
                span("Left one", 50, 100),
                span("Right one", 320, 100),
                span("Left two", 50, 120),
                span("Right two", 320, 120),

                spanWithSize("Mid Document Subheading", 50, 200, 500, 16, "Times-Bold"),

                span("Left three", 50, 300),
                span("Right three", 320, 300),
                span("Left four", 50, 320),
                span("Right four", 320, 320),

                spanWithSize("Footer Copyright Notice", 50, 600, 500, 9, "Times-Roman")
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Main Title",
                        "Left one Left two",
                        "Right one Right two",
                        "Mid Document Subheading",
                        "Left three Left four",
                        "Right three Right four",
                        "Footer Copyright Notice"
                );
    }

    private TextSpan spanWithSize(
            String text,
            float x,
            float y,
            float width,
            float fontSize,
            String fontName
    ) {
        return spanWithSize(
                text,
                x,
                y,
                width,
                fontSize,
                fontName,
                false
        );
    }

    private TextSpan spanWithSize(
            String text,
            float x,
            float y,
            float width,
            float fontSize,
            String fontName,
            boolean wordSeparatorBefore
    ) {
        return new TextSpan(
                0,
                text,
                x,
                y,
                width,
                16,
                fontName,
                fontSize,
                false,
                false,
                false,
                wordSeparatorBefore
        );
    }

    @Test
    void shouldClassifyLargeTextAsHeading() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                spanWithSize(
                        "Introduction",
                        50,
                        50,
                        180,
                        18,
                        "Times-Bold"
                ),
                spanWithSize(
                        "This is normal body text.",
                        50,
                        100,
                        200,
                        11,
                        "Times-Roman"
                )
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks().getFirst().getType())
                .isEqualTo(BlockType.HEADING);

        assertThat(page.getStructuredBlocks().getFirst().getText())
                .isEqualTo("Introduction");
    }

    @Test
    void shouldClassifyBulletListItems() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("• First item", 50, 100),
                span("• Second item", 50, 120)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getType)
                .containsExactly(
                        BlockType.LIST_ITEM,
                        BlockType.LIST_ITEM
                );
    }

    @Test
    void shouldClassifyNumberedListItems() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("1. First item", 50, 100),
                span("2. Second item", 50, 120)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getType)
                .containsExactly(
                        BlockType.LIST_ITEM,
                        BlockType.LIST_ITEM
                );
    }

    @Test
    void shouldSeparateParagraphsOnLargeVerticalGap() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("First paragraph.", 50, 100),
                span("Second paragraph.", 50, 150)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "First paragraph.",
                        "Second paragraph."
                );
    }

    @Test
    void shouldDetectLargeFontHeading() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "Main Title",
                        50,
                        50,
                        200,
                        20,
                        "Helvetica-Bold",
                        18,
                        true,
                        false,
                        false,
                        false
                )
        );

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "Normal body text",
                        50,
                        100,
                        200,
                        12,
                        "Helvetica",
                        10,
                        false,
                        false,
                        false,
                        false
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertEquals(
                BlockType.HEADING,
                page.getStructuredBlocks()
                        .getFirst()
                        .getType()
        );
    }
    @Test
    void shouldPreserveMultiSpanRowCrossingColumnBoundary() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("Left one", 50, 100),
                span("Right one", 320, 100),
                span("Left two", 50, 120),
                span("Right two", 320, 120),

                spanWithSize(
                        "Full",
                        120,
                        200,
                        40,
                        18,
                        "Times-Bold",
                        false
                ),

                spanWithSize(
                        "Width",
                        165,
                        200,
                        40,
                        18,
                        "Times-Bold",
                        true
                ),

                spanWithSize(
                        "Heading",
                        210,
                        200,
                        70,
                        18,
                        "Times-Bold",
                        true
                ),

                span("Left three", 50, 300),
                span("Right three", 320, 300),
                span("Left four", 50, 320),
                span("Right four", 320, 320)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Left one Left two",
                        "Right one Right two",
                        "Full Width Heading",
                        "Left three Left four",
                        "Right three Right four"
                );

        assertThat(page.getStructuredBlocks().get(2).getType())
                .isEqualTo(BlockType.HEADING);
    }
    @Test
    void shouldNotTreatRepeatedLabelValueRowsAsColumns() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("Name:", 50, 100),
                span("John Doe", 250, 100, true),

                span("Email:", 50, 120),
                span("john@example.com", 250, 120, true),

                span("Phone:", 50, 140),
                span("0240000000", 250, 140, true)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Name: John Doe Email: john@example.com Phone: 0240000000"
                );
    }

    @Test
    void shouldPreserveTwoColumnTableRowsInRowOrder() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("Item", 50, 100),
                span("Price", 300, 100, true),

                span("Laptop", 50, 120),
                span("1200", 300, 120, true),

                span("Keyboard", 50, 140),
                span("100", 300, 140, true),

                span("Mouse", 50, 160),
                span("50", 300, 160, true)
        ));

        page.getCandidateTableRegions().add(
                new TableRegion(
                        0,
                        40,
                        90,
                        320,
                        90,
                        4,
                        2
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Item",
                        "Price",
                        "Laptop",
                        "1200",
                        "Keyboard",
                        "100",
                        "Mouse",
                        "50"
                );
    }
    @Test
    void shouldReturnToSingleColumnOrderAfterColumnSectionEnds() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("Left one", 50, 100),
                span("Right one", 320, 100),

                span("Left two", 50, 120),
                span("Right two", 320, 120),

                span("Left three", 50, 140),
                span("Right three", 320, 140),

                span("Single column paragraph", 50, 220)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .containsExactly(
                        "Left one Left two Left three",
                        "Right one Right two Right three",
                        "Single column paragraph"
                );
    }

    @Test
    void shouldJoinMidWordFormattingSpansWithoutSpaces() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().addAll(
                List.of(
                        new TextSpan(
                                0,
                                "im",
                                50f,
                                100f,
                                12f,
                                10f,
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
                                62f,
                                100f,
                                25f,
                                10f,
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
                                87f,
                                100f,
                                18f,
                                10f,
                                "Helvetica",
                                12f,
                                false,
                                false,
                                false,
                                false
                        )
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .hasSize(1);

        assertThat(
                page.getStructuredBlocks()
                        .getFirst()
                        .getText()
        ).isEqualTo("important");
    }

    @Test
    void shouldInsertSpaceWhenSpanHasWordSeparatorBefore() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().addAll(
                List.of(
                        new TextSpan(
                                0,
                                "Hello",
                                50f,
                                100f,
                                30f,
                                10f,
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
                                85f,
                                100f,
                                30f,
                                10f,
                                "Helvetica-Bold",
                                12f,
                                true,
                                false,
                                false,
                                true
                        )
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertThat(
                page.getStructuredBlocks()
                        .getFirst()
                        .getText()
        ).isEqualTo("Hello world");
    }

    @Test
    void shouldPreserveLiteralWhitespaceAcrossFormattingBoundary() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().addAll(
                List.of(
                        new TextSpan(
                                0,
                                "This is ",
                                50f,
                                100f,
                                40f,
                                10f,
                                "Helvetica",
                                12f,
                                false,
                                false,
                                false,
                                false
                        ),
                        new TextSpan(
                                0,
                                "important",
                                90f,
                                100f,
                                50f,
                                10f,
                                "Helvetica-Bold",
                                12f,
                                true,
                                false,
                                false,
                                false
                        )
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertThat(
                page.getStructuredBlocks()
                        .getFirst()
                        .getText()
        ).isEqualTo("This is important");
    }

    @Test
    void shouldNotDetectBodySizedBoldColonLabelAsHeading() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "Name:",
                        50,
                        50,
                        200,
                        12,
                        "Helvetica-Bold",
                        12,
                        true,
                        false,
                        false,
                        false
                )
        );

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "Normal body text",
                        50,
                        100,
                        200,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertEquals(
                BlockType.PARAGRAPH,
                page.getStructuredBlocks()
                        .getFirst()
                        .getType()
        );
    }

    @Test
    void shouldDetectLargerBoldColonLabelAsHeading() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "Important Information:",
                        50,
                        50,
                        220,
                        14,
                        "Helvetica-Bold",
                        14,
                        true,
                        false,
                        false,
                        false
                )
        );

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "Normal body text",
                        50,
                        100,
                        200,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertEquals(
                BlockType.HEADING,
                page.getStructuredBlocks()
                        .getFirst()
                        .getType()
        );
    }

    @Test
    void shouldNotDetectNormalBodyTextAsHeading() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "This is a normal sentence.",
                        50,
                        50,
                        250,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                )
        );

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "More normal body text.",
                        50,
                        80,
                        250,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertEquals(
                BlockType.PARAGRAPH,
                page.getStructuredBlocks()
                        .getFirst()
                        .getType()
        );
    }

    @Test
    void shouldNotMisclassifyNormalListItemAsHeading() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "1. First item",
                        50,
                        50,
                        150,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                )
        );

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "Normal body text",
                        50,
                        100,
                        200,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertEquals(
                BlockType.LIST_ITEM,
                page.getStructuredBlocks()
                        .getFirst()
                        .getType()
        );
    }

    @Test
    void shouldPreferBoldColonHeadingOverListPattern() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "vii. Uppercase Roman numerals:",
                        50,
                        50,
                        220,
                        14,
                        "Helvetica-Bold",
                        14,
                        true,
                        false,
                        false,
                        false
                )
        );

        page.getTextSpans().add(
                new TextSpan(
                        0,
                        "Normal body text",
                        50,
                        100,
                        200,
                        12,
                        "Helvetica",
                        12,
                        false,
                        false,
                        false,
                        false
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertEquals(
                BlockType.HEADING,
                page.getStructuredBlocks()
                        .getFirst()
                        .getType()
        );
    }

    @Test
    void shouldDetectCenteredSingleLineAlignment() {
        PageExtraction page = new PageExtraction(0);
        page.setPageWidth(612f);

        page.getTextSpans().add(
                spanWithSize("Centered Title", 186f, 50f, 240f, 18f, "Times-Bold")
        );

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks()).hasSize(1);

        assertThat(page.getStructuredBlocks().getFirst().getAlignment())
                .isEqualTo(BlockAlignment.CENTER);
    }

    @Test
    void shouldNotTreatFullWidthLeftAlignedLineAsCentered() {
        PageExtraction page = new PageExtraction(0);
        page.setPageWidth(612f);

        page.getTextSpans().add(
                spanWithSize("Left aligned heading", 72f, 50f, 468f, 18f, "Times-Bold")
        );

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks().getFirst().getAlignment())
                .isEqualTo(BlockAlignment.LEFT);
    }

    @Test
    void shouldDetectJustifiedParagraphWhenWrappedLinesReachTheSameRightMargin() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                spanWithSize("First line reaching the margin", 50f, 100f, 450f, 12f, "Times-Roman"),
                spanWithSize("Second line reaching the margin", 50f, 114f, 452f, 12f, "Times-Roman"),
                spanWithSize("Third line reaching the margin", 50f, 128f, 448f, 12f, "Times-Roman"),
                spanWithSize("Short final line", 50f, 142f, 150f, 12f, "Times-Roman")
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks()).hasSize(1);

        assertThat(page.getStructuredBlocks().getFirst().getAlignment())
                .isEqualTo(BlockAlignment.JUSTIFY);
    }

    @Test
    void shouldNotJustifyOrdinaryRaggedRightParagraph() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                spanWithSize("First line falls well short", 50f, 100f, 300f, 12f, "Times-Roman"),
                spanWithSize("Second line also falls short", 50f, 114f, 250f, 12f, "Times-Roman"),
                spanWithSize("Third line falls short too", 50f, 128f, 280f, 12f, "Times-Roman"),
                spanWithSize("Short final line", 50f, 142f, 150f, 12f, "Times-Roman")
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks()).hasSize(1);

        assertThat(page.getStructuredBlocks().getFirst().getAlignment())
                .isEqualTo(BlockAlignment.LEFT);
    }

    @Test
    void shouldMergeSingleSpacedLinesEvenWhenGlyphBoundingBoxIsUnreliablySmall() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                tinyHeightSpan("Line one continues", 50f, 100f, 12f),
                tinyHeightSpan("onto line two here", 50f, 114f, 12f),
                tinyHeightSpan("and line three too", 50f, 128f, 12f),
                tinyHeightSpan("finishing on four", 50f, 142f, 12f)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks()).hasSize(1);

        assertThat(page.getStructuredBlocks().getFirst().getType())
                .isEqualTo(BlockType.PARAGRAPH);
    }

    @Test
    void shouldNotTrustImplausiblyLargeDominantSpacingAsSingleLinePitch() {
        PageExtraction page = new PageExtraction(0);

        page.getTextSpans().addAll(List.of(
                span("First separate line", 50, 100),
                span("Second separate line", 50, 125),
                span("Third separate line", 50, 150),
                span("Fourth separate line", 50, 175)
        ));

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks()).hasSize(4);
    }

    private TextSpan tinyHeightSpan(
            String text,
            float x,
            float y,
            float fontSize
    ) {
        return new TextSpan(
                0,
                text,
                x,
                y,
                200f,
                4f,
                "Times-Roman",
                fontSize,
                false,
                false,
                false,
                false
        );
    }
    @Test
    void shouldClassifyParenthesizedDecimalAsOrderedList() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                span("(1) First item", 50, 100)
        );

        structureRecoveryService
                .recoverStructure(page);

        StructuredBlock block =
                page.getStructuredBlocks()
                        .getFirst();

        assertThat(block.getType())
                .isEqualTo(BlockType.LIST_ITEM);

        assertThat(block.getListType())
                .isEqualTo(ListType.ORDERED);
    }

    @Test
    void shouldClassifyParenthesizedRomanAsOrderedList() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                span("(i) First item", 50, 100)
        );

        structureRecoveryService
                .recoverStructure(page);

        StructuredBlock block =
                page.getStructuredBlocks()
                        .getFirst();

        assertThat(block.getType())
                .isEqualTo(BlockType.LIST_ITEM);

        assertThat(block.getListType())
                .isEqualTo(ListType.ORDERED);
    }

    @Test
    void shouldClassifyParenthesizedUppercaseLetterAsOrderedList() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                span("(A) First item", 50, 100)
        );

        structureRecoveryService
                .recoverStructure(page);

        StructuredBlock block =
                page.getStructuredBlocks()
                        .getFirst();

        assertThat(block.getType())
                .isEqualTo(BlockType.LIST_ITEM);

        assertThat(block.getListType())
                .isEqualTo(ListType.ORDERED);
    }

    @Test
    void shouldClassifyCheckmarkAsUnorderedList() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                span("✓ First item", 50, 100)
        );

        structureRecoveryService
                .recoverStructure(page);

        StructuredBlock block =
                page.getStructuredBlocks()
                        .getFirst();

        assertThat(block.getType())
                .isEqualTo(BlockType.LIST_ITEM);

        assertThat(block.getListType())
                .isEqualTo(ListType.UNORDERED);
    }

    @Test
    void shouldClassifyMiddleDotAsUnorderedList() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                span("· First item", 50, 100)
        );

        structureRecoveryService
                .recoverStructure(page);

        StructuredBlock block =
                page.getStructuredBlocks()
                        .getFirst();

        assertThat(block.getType())
                .isEqualTo(BlockType.LIST_ITEM);

        assertThat(block.getListType())
                .isEqualTo(ListType.UNORDERED);
    }

    @Test
    void shouldNotClassifyPlainOAsUnorderedList() {

        PageExtraction page =
                new PageExtraction(0);

        page.getTextSpans().add(
                span("o First item", 50, 100)
        );

        structureRecoveryService
                .recoverStructure(page);

        StructuredBlock block =
                page.getStructuredBlocks()
                        .getFirst();

        assertThat(block.getType())
                .isNotEqualTo(BlockType.LIST_ITEM);

        assertThat(block.getListType())
                .isNull();
    }

    @Test
    void shouldNotMergeBottomPageEdgeTextIntoBodyParagraph() {

        PageExtraction page =
                new PageExtraction(0);

        page.setPageWidth(612);
        page.setPageHeight(792);

        page.getTextSpans().add(
                textSpan(
                        0,
                        "This is body text.",
                        50,
                        700,
                        300,
                        12
                )
        );

        page.getTextSpans().add(
                textSpan(
                        0,
                        "More body text.",
                        50,
                        712,
                        300,
                        12
                )
        );

        page.getTextSpans().add(
                textSpan(
                        0,
                        "23",
                        50,
                        756,
                        20,
                        10
                )
        );

        page.getTextSpans().add(
                textSpan(
                        0,
                        "MAZZETTI | Electric Circuit Data Collection",
                        90,
                        768,
                        350,
                        8
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .extracting(StructuredBlock::getText)
                .contains(
                        "This is body text. More body text.",
                        "23",
                        "MAZZETTI | Electric Circuit Data Collection"
                );

        assertThat(page.getStructuredBlocks())
                .noneMatch(block ->
                        block.getText().contains("More body text.")
                                && block.getText().contains("MAZZETTI")
                );
    }

    @Test
    void shouldStillMergeNormalParagraphLinesAboveBottomEdge() {

        PageExtraction page =
                new PageExtraction(0);

        page.setPageWidth(612);
        page.setPageHeight(792);

        page.getTextSpans().add(
                textSpan(
                        0,
                        "First line of paragraph",
                        50,
                        700,
                        300,
                        12
                )
        );

        page.getTextSpans().add(
                textSpan(
                        0,
                        "second line of paragraph",
                        50,
                        712,
                        300,
                        12
                )
        );

        structureRecoveryService.recoverStructure(page);

        assertThat(page.getStructuredBlocks())
                .hasSize(1);

        assertThat(
                page.getStructuredBlocks()
                        .getFirst()
                        .getText()
        ).isEqualTo(
                "First line of paragraph second line of paragraph"
        );
    }
}