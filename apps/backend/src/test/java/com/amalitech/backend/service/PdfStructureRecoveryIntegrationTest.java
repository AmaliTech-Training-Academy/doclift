package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.PdfExtractionServiceImpl;
import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfStructureRecoveryIntegrationTest {

    private PdfExtractionService pdfExtractionService;

    @BeforeEach
    void setUp() {
        pdfExtractionService = new PdfExtractionServiceImpl(
                new StructureRecoveryServiceImpl()
        );

    }

    // =========================================================
    // MULTI-COLUMN STRUCTURE RECOVERY
    // =========================================================

    @Test
    void shouldRecoverReadingOrderForMultiColumnPdf() throws Exception {

        try (InputStream inputStream = getClass().getResourceAsStream(
                "/sample-files-main/026-latex-multicolumn/multicolumn.pdf"
        )) {

            assertThat(inputStream)
                    .as("Multi-column sample PDF should exist")
                    .isNotNull();

            PdfExtractionResult result =
                    pdfExtractionService.extract(inputStream);

            assertThat(result.getPages()).isNotEmpty();

            PageExtraction page = result.getPages().getFirst();

            List<StructuredBlock> blocks =
                    page.getStructuredBlocks();

            assertThat(blocks).isNotEmpty();

            // ---------------------------------------------------------
            // HEADING CHECK
            // ---------------------------------------------------------

            assertThat(blocks.getFirst().getType())
                    .isEqualTo(BlockType.HEADING);

            assertThat(blocks.getFirst().getText())
                    .isEqualTo(
                            "Two-Column Document with Lorem Ipsum"
                    );

            // ---------------------------------------------------------
            // MULTI-COLUMN READING ORDER CHECK
            // ---------------------------------------------------------

            int leftColumnIndex = -1;
            int rightColumnIndex = -1;

            for (int i = 0; i < blocks.size(); i++) {
                String text = blocks.get(i).getText();

                if (leftColumnIndex == -1
                        && text.contains(
                        "This is a sample document"
                )) {
                    leftColumnIndex = i;
                }

                if (rightColumnIndex == -1
                        && text.contains(
                        "molestie vitae"
                )) {
                    rightColumnIndex = i;
                }
            }

            assertThat(leftColumnIndex)
                    .as("Left-column text should be present")
                    .isGreaterThanOrEqualTo(0);

            assertThat(rightColumnIndex)
                    .as("Right-column text should be present")
                    .isGreaterThanOrEqualTo(0);

            assertThat(leftColumnIndex)
                    .as(
                            "Left column should be ordered before right column"
                    )
                    .isLessThan(rightColumnIndex);

            // ---------------------------------------------------------
            // FOOTER / PAGE NUMBER SHOULD REMAIN SEPARATE
            // ---------------------------------------------------------

            assertThat(blocks)
                    .extracting(StructuredBlock::getText)
                    .contains("1");
        }
    }

    @Test
    void shouldRecoverHeadingAndParagraphsFromOutlinePdf() throws Exception {

        try (InputStream inputStream = getClass().getResourceAsStream(
                "/sample-files-main/006-pdflatex-outline/pdflatex-outline.pdf"
        )) {

            assertThat(inputStream)
                    .as("Outline sample PDF should exist")
                    .isNotNull();

            PdfExtractionResult result =
                    pdfExtractionService.extract(inputStream);

            assertThat(result.getPages()).isNotEmpty();

            PageExtraction page = result.getPages().getFirst();

            List<StructuredBlock> blocks =
                    page.getStructuredBlocks();

            assertThat(blocks).isNotEmpty();

            // ---------------------------------------------------------
            // HEADING CLASSIFICATION
            // ---------------------------------------------------------

            assertThat(blocks.getFirst().getType())
                    .isEqualTo(BlockType.HEADING);

            assertThat(blocks.getFirst().getText())
                    .isEqualTo("Contents");

            // ---------------------------------------------------------
            // PARAGRAPH CLASSIFICATION
            // ---------------------------------------------------------

            assertThat(blocks)
                    .anySatisfy(block -> {
                        assertThat(block.getType())
                                .isEqualTo(BlockType.PARAGRAPH);

                        assertThat(block.getText())
                                .isEqualTo("1 Foo");
                    });

            assertThat(blocks)
                    .anySatisfy(block -> {
                        assertThat(block.getType())
                                .isEqualTo(BlockType.PARAGRAPH);

                        assertThat(block.getText())
                                .isEqualTo("2 Bar");
                    });

            // ---------------------------------------------------------
            // HEADING SHOULD APPEAR BEFORE BODY CONTENT
            // ---------------------------------------------------------

            int headingIndex = -1;
            int firstParagraphIndex = -1;

            for (int i = 0; i < blocks.size(); i++) {

                StructuredBlock block = blocks.get(i);

                if (headingIndex == -1
                        && block.getType() == BlockType.HEADING) {
                    headingIndex = i;
                }

                if (firstParagraphIndex == -1
                        && block.getType() == BlockType.PARAGRAPH
                        && block.getText().equals("1 Foo")) {
                    firstParagraphIndex = i;
                }
            }

            assertThat(headingIndex)
                    .as("Contents heading should be present")
                    .isGreaterThanOrEqualTo(0);

            assertThat(firstParagraphIndex)
                    .as("First contents entry should be present")
                    .isGreaterThanOrEqualTo(0);

            assertThat(headingIndex)
                    .as("Heading should appear before paragraph content")
                    .isLessThan(firstParagraphIndex);
        }
    }
}