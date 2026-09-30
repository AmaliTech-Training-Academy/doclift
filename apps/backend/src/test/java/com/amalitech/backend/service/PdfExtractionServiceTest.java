package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.PdfExtractionServiceImpl;
import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExtractionServiceTest {

    private final PdfExtractionService pdfExtractionService =
            new PdfExtractionServiceImpl(
                    new StructureRecoveryServiceImpl()
            );

    private void writeText(
            PDPageContentStream content,
            PDType1Font font,
            float fontSize,
            float x,
            float y,
            String text
    ) throws Exception {

        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private void writeThreeColumnDateRow(
            PDPageContentStream content,
            PDType1Font normal,
            PDType1Font bold,
            float y,
            String firstColumn,
            String dateFirstPart,
            String dateSecondPart,
            String thirdColumn
    ) throws Exception {

        writeText(
                content,
                normal,
                12,
                80,
                y,
                firstColumn
        );

        content.beginText();
        content.newLineAtOffset(260, y);

        content.setFont(normal, 12);
        content.showText(dateFirstPart);

        content.setFont(bold, 12);
        content.showText(dateSecondPart);

        content.endText();

        writeText(
                content,
                normal,
                12,
                430,
                y,
                thirdColumn
        );
    }

    @Test
    void shouldDetectTwoColumnTableWhenCellContainsMixedFormatting()
            throws Exception {

        byte[] pdfBytes;

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            PDType1Font normal =
                    new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA
                    );

            PDType1Font bold =
                    new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA_BOLD
                    );

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                // Row 1
                writeText(
                        content,
                        normal,
                        12,
                        80,
                        700,
                        "Item"
                );

                writeText(
                        content,
                        normal,
                        12,
                        300,
                        700,
                        "Price"
                );

                // Row 2:
                // "Laptop" is one logical cell, but contains
                // a formatting boundary.
                content.beginText();
                content.newLineAtOffset(80, 670);

                content.setFont(normal, 12);
                content.showText("Lap");

                content.setFont(bold, 12);
                content.showText("top");

                content.endText();

                writeText(
                        content,
                        normal,
                        12,
                        300,
                        670,
                        "1200"
                );

                // Row 3
                writeText(
                        content,
                        normal,
                        12,
                        80,
                        640,
                        "Keyboard"
                );

                writeText(
                        content,
                        normal,
                        12,
                        300,
                        640,
                        "100"
                );

                // Row 4
                writeText(
                        content,
                        normal,
                        12,
                        80,
                        610,
                        "Mouse"
                );

                writeText(
                        content,
                        normal,
                        12,
                        300,
                        610,
                        "50"
                );
            }

            document.save(output);
            pdfBytes = output.toByteArray();
        }

        PdfExtractionResult result =
                pdfExtractionService.extract(pdfBytes);

        PageExtraction page =
                result.getPages().getFirst();

        assertThat(page.getCandidateTableRegions())
                .as(
                        "A formatting boundary inside one cell "
                                + "must not stop a real two-column table "
                                + "from being detected"
                )
                .anySatisfy(region -> {
                    assertThat(region.getColumnCount())
                            .isEqualTo(2);

                    assertThat(region.getRowCount())
                            .isEqualTo(4);
                });
    }

    @Test
    void shouldDetectMultiColumnTableWhenTabularValueIsSplitByFormatting()
            throws Exception {

        byte[] pdfBytes;

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            PDType1Font normal =
                    new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA
                    );

            PDType1Font bold =
                    new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA_BOLD
                    );

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                writeThreeColumnDateRow(
                        content,
                        normal,
                        bold,
                        700,
                        "Conference",
                        "12/",
                        "09/2026",
                        "Accra"
                );

                writeThreeColumnDateRow(
                        content,
                        normal,
                        bold,
                        670,
                        "Workshop",
                        "15/",
                        "10/2026",
                        "Kumasi"
                );

                writeThreeColumnDateRow(
                        content,
                        normal,
                        bold,
                        640,
                        "Seminar",
                        "21/",
                        "11/2026",
                        "Takoradi"
                );
            }

            document.save(output);
            pdfBytes = output.toByteArray();
        }

        PdfExtractionResult result =
                pdfExtractionService.extract(pdfBytes);

        PageExtraction page =
                result.getPages().getFirst();

        assertThat(page.getCandidateTableRegions())
                .as(
                        "Formatting splits inside a tabular value "
                                + "must not prevent a three-column table "
                                + "from being detected"
                )
                .anySatisfy(region -> {
                    assertThat(region.getColumnCount())
                            .isEqualTo(3);

                    assertThat(region.getRowCount())
                            .isEqualTo(3);
                });
    }

    @Test
    void shouldPreserveMixedBoldFormattingAsSeparateTextSpans()
            throws Exception {

        byte[] pdfBytes;

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            PDType1Font normalFont =
                    new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA
                    );

            PDType1Font boldFont =
                    new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA_BOLD
                    );

            try (PDPageContentStream content =
                         new PDPageContentStream(
                                 document,
                                 page
                         )) {

                content.beginText();
                content.newLineAtOffset(80, 700);

                content.setFont(normalFont, 12);
                content.showText("This is ");

                content.setFont(boldFont, 12);
                content.showText("important");

                content.setFont(normalFont, 12);
                content.showText(" text");

                content.endText();
            }

            document.save(output);
            pdfBytes = output.toByteArray();
        }

        PdfExtractionResult result =
                pdfExtractionService.extract(pdfBytes);

        PageExtraction page =
                result.getPages().getFirst();

        assertThat(page.getTextSpans())
                .hasSize(3);

        assertThat(page.getTextSpans().get(0).getText())
                .isEqualTo("This is ");

        assertThat(page.getTextSpans().get(0).isBold())
                .isFalse();

        assertThat(page.getTextSpans().get(1).getText())
                .isEqualTo("important");

        assertThat(page.getTextSpans().get(1).isBold())
                .isTrue();

        assertThat(page.getTextSpans().get(2).getText())
                .isEqualTo(" text");

        assertThat(page.getTextSpans().get(2).isBold())
                .isFalse();
    }

    @Test
    void shouldNotInsertWordSeparatorAcrossMidWordFormattingBoundary()
            throws Exception {

        try (PDDocument document = new PDDocument()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                content.beginText();
                content.newLineAtOffset(50, 700);

                content.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA
                        ),
                        12
                );
                content.showText("im");

                content.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA_BOLD
                        ),
                        12
                );
                content.showText("port");

                content.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA
                        ),
                        12
                );
                content.showText("ant");

                content.endText();
            }

            PdfExtractionResult result =
                    pdfExtractionService.extract(document);

            List<TextSpan> spans =
                    result.getPages()
                            .getFirst()
                            .getTextSpans();

            assertThat(spans)
                    .hasSize(3);

            assertThat(spans.get(0).getText())
                    .isEqualTo("im");

            assertThat(spans.get(1).getText())
                    .isEqualTo("port");

            assertThat(spans.get(2).getText())
                    .isEqualTo("ant");

            assertThat(spans.get(0).isWordSeparatorBefore())
                    .isFalse();

            assertThat(spans.get(1).isWordSeparatorBefore())
                    .isFalse();

            assertThat(spans.get(2).isWordSeparatorBefore())
                    .isFalse();

            assertThat(
                    result.getPages()
                            .getFirst()
                            .getStructuredBlocks()
                            .getFirst()
                            .getText()
            ).isEqualTo("important");
        }
    }

    @Test
    void shouldPreserveWordSeparatorAcrossFormattingBoundary()
            throws Exception {

        try (PDDocument document = new PDDocument()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                content.beginText();

                content.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA
                        ),
                        12
                );

                content.newLineAtOffset(50, 700);
                content.showText("Hello");


                content.setTextMatrix(
                        Matrix.getTranslateInstance(
                                110,
                                700
                        )
                );

                content.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA_BOLD
                        ),
                        12
                );

                content.showText("world");

                content.endText();
            }

            PdfExtractionResult result =
                    pdfExtractionService.extract(document);

            List<TextSpan> spans =
                    result.getPages()
                            .getFirst()
                            .getTextSpans();

            assertThat(spans)
                    .hasSize(2);

            assertThat(spans.get(0).getText())
                    .isEqualTo("Hello");

            assertThat(spans.get(1).getText())
                    .isEqualTo("world");

            assertThat(spans.get(1).isWordSeparatorBefore())
                    .isTrue();

            assertThat(
                    result.getPages()
                            .getFirst()
                            .getStructuredBlocks()
                            .getFirst()
                            .getText()
            ).isEqualTo("Hello world");
        }
    }
}