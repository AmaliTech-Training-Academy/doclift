package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.PdfExtractionServiceImpl;
import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExtractionServiceTest {

    private final PdfExtractionService pdfExtractionService =
            new PdfExtractionServiceImpl(
                    new StructureRecoveryServiceImpl()
            );

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
}