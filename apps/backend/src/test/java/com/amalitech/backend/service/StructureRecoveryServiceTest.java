package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructureRecoveryServiceTest {

    private StructureRecoveryService structureRecoveryService;

    @BeforeEach
    void setUp() {
        structureRecoveryService = new StructureRecoveryServiceImpl();
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
                span("world", 110, 100),
                span("Hello", 50, 100)
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
                11
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
        return new TextSpan(
                0,
                text,
                x,
                y,
                width,
                16,
                fontName,
                fontSize
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
                        "Times-Bold"
                ),
                spanWithSize(
                        "Width",
                        165,
                        200,
                        40,
                        18,
                        "Times-Bold"
                ),
                spanWithSize(
                        "Heading",
                        210,
                        200,
                        70,
                        18,
                        "Times-Bold"
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
}