package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.WordWriterServiceImpl;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

class WordWriterServiceTest {

    private final WordWriterService wordWriterService =
            new WordWriterServiceImpl();

    private record NumberingInfo(
            String paragraphText,
            String numFmt,
            String levelText
    ) {
    }

    private record BulletInfo(
            String paragraphText,
            String levelText,
            BigInteger numId
    ) {
    }

    private NumberingInfo writeAndReadOrderedListItem(
            String sourceText
    ) throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                new StructuredBlock(
                        0,
                        BlockType.LIST_ITEM,
                        ListType.ORDERED,
                        sourceText,
                        50,
                        100,
                        150,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        sourceText,
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
                                )
                        )
                )
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
            XWPFParagraph paragraph =
                    document.getParagraphs().getFirst();

            String numFmt =
                    paragraph.getNumFmt();

            BigInteger numId =
                    paragraph.getNumID();

            BigInteger abstractNumId =
                    document.getNumbering()
                            .getAbstractNumID(numId);

            XWPFAbstractNum abstractNum =
                    document.getNumbering()
                            .getAbstractNum(
                                    abstractNumId
                            );

            String levelText =
                    abstractNum
                            .getCTAbstractNum()
                            .getLvlArray(0)
                            .getLvlText()
                            .getVal();

            return new NumberingInfo(
                    paragraph.getText(),
                    numFmt,
                    levelText
            );
        }
    }

    private BulletInfo writeAndReadUnorderedListItem(
            String sourceText
    ) throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                new StructuredBlock(
                        0,
                        BlockType.LIST_ITEM,
                        ListType.UNORDERED,
                        sourceText,
                        50,
                        100,
                        150,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        sourceText,
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
                                )
                        )
                )
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
            XWPFParagraph paragraph =
                    document.getParagraphs().getFirst();

            BigInteger numId =
                    paragraph.getNumID();

            BigInteger abstractNumId =
                    document.getNumbering()
                            .getAbstractNumID(numId);

            XWPFAbstractNum abstractNum =
                    document.getNumbering()
                            .getAbstractNum(abstractNumId);

            String levelText =
                    abstractNum
                            .getCTAbstractNum()
                            .getLvlArray(0)
                            .getLvlText()
                            .getVal();

            return new BulletInfo(
                    paragraph.getText(),
                    levelText,
                    numId
            );
        }
    }

    private XWPFParagraph writeSingleOrderedListItem(
            String sourceText
    ) throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                new StructuredBlock(
                        0,
                        BlockType.LIST_ITEM,
                        ListType.ORDERED,
                        sourceText,
                        50,
                        100,
                        150,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        sourceText,
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
                                )
                        )
                )
        );

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        XWPFDocument document =
                new XWPFDocument(
                        new ByteArrayInputStream(docx)
                );

        return document.getParagraphs().getFirst();
    }

    private StructuredBlock unorderedListItem(
            String text
    ) {
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
                ListType.UNORDERED,
                text,
                50,
                100,
                150,
                12,
                List.of(span)
        );
    }

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
                                        18,
                                        true,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        StructuredBlock bodyParagraph =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "This is normal body text.",
                        50,
                        100,
                        250,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        "This is normal body text.",
                                        50,
                                        100,
                                        250,
                                        12,
                                        "Helvetica",
                                        10,
                                        false,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        page.getStructuredBlocks().add(heading);
        page.getStructuredBlocks().add(bodyParagraph);

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {

            XWPFParagraph headingParagraph =
                    document.getParagraphs().getFirst();

            assertThat(headingParagraph.getText())
                    .isEqualTo("Quarterly Results");

            assertThat(headingParagraph.getStyle())
                    .isEqualTo("Heading1");

            XWPFParagraph body =
                    document.getParagraphs().get(1);

            assertThat(body.getStyle())
                    .isEqualTo("Normal");
        }
    }

    @Test
    void shouldApplyHeading2StyleForMediumHeading() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock heading =
                new StructuredBlock(
                        0,
                        BlockType.HEADING,
                        null,
                        "Section Heading",
                        50,
                        50,
                        200,
                        20,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Section Heading",
                                        50,
                                        50,
                                        200,
                                        20,
                                        "Helvetica-Bold",
                                        14,
                                        true,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        StructuredBlock bodyParagraph =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Normal body text.",
                        50,
                        100,
                        250,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Normal body text.",
                                        50,
                                        100,
                                        250,
                                        12,
                                        "Helvetica",
                                        10,
                                        false,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        page.getStructuredBlocks().add(heading);
        page.getStructuredBlocks().add(bodyParagraph);

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {
            XWPFParagraph headingParagraph =
                    document.getParagraphs().getFirst();

            assertThat(headingParagraph.getStyle())
                    .isEqualTo("Heading2");
        }
    }

    @Test
    void shouldApplyHeading3StyleForSmallHeading() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        StructuredBlock heading =
                new StructuredBlock(
                        0,
                        BlockType.HEADING,
                        null,
                        "Subsection Heading",
                        50,
                        50,
                        200,
                        20,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Subsection Heading",
                                        50,
                                        50,
                                        200,
                                        20,
                                        "Helvetica-Bold",
                                        12,
                                        true,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        StructuredBlock bodyParagraph =
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Normal body text.",
                        50,
                        100,
                        250,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Normal body text.",
                                        50,
                                        100,
                                        250,
                                        12,
                                        "Helvetica",
                                        10,
                                        false,
                                        false,
                                        false,
                                        false
                                )
                        )
                );

        page.getStructuredBlocks().add(heading);
        page.getStructuredBlocks().add(bodyParagraph);

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        new XWPFDocument(
                                new ByteArrayInputStream(docx)
                        )
        ) {
            XWPFParagraph headingParagraph =
                    document.getParagraphs().getFirst();

            assertThat(headingParagraph.getStyle())
                    .isEqualTo("Heading3");
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
                            .getFirst()
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
                            .getFirst()
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
    void shouldNotAddPdfGapBetweenConsecutiveListItems() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction page = new PageExtraction(0);

        StructuredBlock first = new StructuredBlock(
                0,
                BlockType.LIST_ITEM,
                ListType.UNORDERED,
                "- First item",
                40,
                100,
                100,
                12,
                List.of()
        );
        StructuredBlock second = new StructuredBlock(
                0,
                BlockType.LIST_ITEM,
                ListType.UNORDERED,
                "- Second item",
                40,
                150,
                110,
                12,
                List.of()
        );

        page.getStructuredBlocks().add(first);
        page.getStructuredBlocks().add(second);
        extractionResult.getPages().add(page);

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            assertThat(document.getParagraphs()).hasSize(2);
            assertThat(document.getParagraphs().get(1).getSpacingBefore())
                    .isZero();
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
                        "ABCDEF+Helvetica",
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

            XWPFParagraph paragraph = document.getParagraphs().getFirst();

            assertThat(paragraph.getText()).isEqualTo("Hello Word");
            assertThat(paragraph.getCTP().xmlText()).doesNotContain("framePr");
            assertThat(paragraph.getRuns().getFirst().getFontFamily())
                    .isEqualTo("Arial");
        }
    }

    @Test
    void shouldPreserveFractionalFontSizeAndExplicitParagraphSpacing() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction page = new PageExtraction(0);
        TextSpan span = new TextSpan(
                0,
                "Fractional text",
                20,
                20,
                100,
                12,
                "Helvetica",
                12.5f,
                false,
                false,
                false,
                false
        );
        StructuredBlock block = new StructuredBlock(
                0,
                BlockType.PARAGRAPH,
                null,
                "Fractional text",
                20,
                20,
                100,
                12,
                List.of(span)
        );
        page.getStructuredBlocks().add(block);
        extractionResult.getPages().add(page);

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            XWPFParagraph paragraph = document.getParagraphs().getFirst();

            assertThat(paragraph.getRuns().getFirst().getFontSizeAsDouble())
                    .isEqualTo(12.5);
            assertThat(paragraph.getSpacingAfter()).isZero();
            assertThat(paragraph.getSpacingBetween()).isEqualTo(1.0);
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
    void shouldScaleWideTableColumnsProportionallyToPageContentWidth() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction page = new PageExtraction(0);
        page.setCropWidth(612);
        page.setCropHeight(792);

        StructuredBlock tableBlock = new StructuredBlock(
                0,
                BlockType.TABLE,
                "wide table",
                50,
                100,
                600,
                40,
                List.of(),
                List.of(List.of(
                        tableCell(0, 0, "Left"),
                        tableCell(0, 1, "Right")
                ))
        );
        tableBlock.setColumnWidths(List.of(200f, 400f));

        page.getStructuredBlocks().add(tableBlock);
        extractionResult.getPages().add(page);

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var gridColumns = document.getTables().getFirst().getCTTbl()
                    .getTblGrid().getGridColList();
            int firstWidth = Integer.parseInt(gridColumns.get(0).getW().toString());
            int secondWidth = Integer.parseInt(gridColumns.get(1).getW().toString());

            assertThat(firstWidth).isLessThan(4000);
            assertThat(secondWidth).isGreaterThan(firstWidth);
            assertThat(firstWidth + secondWidth).isLessThanOrEqualTo(10520);
            assertThat(document.getTables().getFirst().getCTTbl().getTblPr()
                    .getTblLayout().getType().toString())
                    .isEqualTo("fixed");
            assertThat(document.getTables().getFirst().getCTTbl().getTblPr()
                    .getTblW().getW().toString())
                    .isEqualTo(String.valueOf(firstWidth + secondWidth));
        }
    }

    @Test
    void shouldKeepRecoveredTablesInDocumentFlow() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction page = new PageExtraction(0);
        StructuredBlock tableBlock = new StructuredBlock(
                0,
                BlockType.TABLE,
                "table",
                80,
                240,
                200,
                30,
                List.of(),
                List.of(List.of(tableCell(0, 0, "Flow table")))
        );

        page.getStructuredBlocks().add(tableBlock);
        extractionResult.getPages().add(page);

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            assertThat(document.getTables()).hasSize(1);
            assertThat(document.getTables().getFirst().getCTTbl()
                    .getTblPr().xmlText())
                    .doesNotContain("tblpPr");
            assertThat(document.getTables().getFirst().getCTTbl().xmlText())
                    .doesNotContain("tblGrid");
        }
    }

    @Test
    void shouldDeriveStableSectionMarginsFromPageContentBounds() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction page = new PageExtraction(0);
        page.setCropWidth(612);
        page.setCropHeight(792);
        page.getStructuredBlocks().add(new StructuredBlock(
                0,
                BlockType.PARAGRAPH,
                "Content",
                50,
                60,
                400,
                20,
                List.of()
        ));
        extractionResult.getPages().add(page);

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var margins = document.getDocument().getBody().getSectPr().getPgMar();

            assertThat(margins.getLeft().toString()).isEqualTo("1000");
            assertThat(margins.getTop().toString()).isEqualTo("360");
            assertThat(margins.getRight().toString()).isEqualTo("2880");
            assertThat(margins.getBottom().toString()).isEqualTo("360");
        }
    }

    @Test
    void shouldUsePageBreaksForSourcePagesWithStableGeometry() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();

        for (int pageIndex = 0; pageIndex < 2; pageIndex++) {
            PageExtraction page = new PageExtraction(pageIndex);
            page.setCropWidth(612);
            page.setCropHeight(792);
            page.getStructuredBlocks().add(new StructuredBlock(
                    pageIndex,
                    BlockType.PARAGRAPH,
                    "Body content " + pageIndex,
                    50,
                    200,
                    200,
                    14,
                    List.of()
            ));
            extractionResult.getPages().add(page);
        }

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            String xml = document.getDocument().xmlText();
            assertThat(xml).contains("w:type=\"page\"");
            assertThat(document.getParagraphs())
                    .filteredOn(p -> p.getCTP().isSetPPr()
                            && p.getCTP().getPPr().isSetSectPr())
                    .isEmpty();
        }
    }

    @Test
    void shouldPreserveEmptySourcePageBoundary() throws Exception {
        PdfExtractionResult extractionResult = new PdfExtractionResult();
        extractionResult.getPages().add(new PageExtraction(0));
        extractionResult.getPages().add(new PageExtraction(1));
        extractionResult.getPages().get(1).getStructuredBlocks().add(
                paragraphBlock(1, "After empty page", 20, 20)
        );

        try (XWPFDocument document = readDocument(wordWriterService.write(extractionResult))) {
            assertThat(pageBreakCount(document)).isEqualTo(1);
        }
    }

    @Test
    void shouldPreserveFooterOnlySourcePageBoundary() throws Exception {
        PdfExtractionResult extractionResult = new PdfExtractionResult();
        for (int pageIndex = 0; pageIndex < 3; pageIndex++) {
            PageExtraction page = new PageExtraction(pageIndex);
            page.getStructuredBlocks().add(new StructuredBlock(
                    pageIndex,
                    BlockType.PARAGRAPH,
                    "Source footer",
                    20,
                    760,
                    100,
                    12,
                    List.of()
            ));
            extractionResult.getPages().add(page);
        }

        try (XWPFDocument document = readDocument(wordWriterService.write(extractionResult))) {
            assertThat(pageBreakCount(document)).isEqualTo(2);
            assertThat(document.getFooterList()).isNotEmpty();
            assertThat(document.getFooterList().getFirst().getText())
                    .contains("Source footer");
        }
    }

    @Test
    void shouldPreserveImageOnlySourcePageBoundary() throws Exception {
        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction imagePage = new PageExtraction(0);
        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        ImageIO.write(
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB),
                "png",
                imageBytes
        );
        imagePage.getImages().add(new ExtractedImage(
                0,
                "image-only.png",
                20,
                20,
                100,
                100,
                10,
                10,
                "image/png",
                0,
                false,
                imageBytes.toByteArray()
        ));
        extractionResult.getPages().add(imagePage);
        extractionResult.getPages().add(new PageExtraction(1));

        try (XWPFDocument document = readDocument(wordWriterService.write(extractionResult))) {
            assertThat(pageBreakCount(document)).isEqualTo(1);
            assertThat(document.getDocument().xmlText()).contains("image-only.png");
        }
    }

    @Test
    void shouldPreserveThreeSourcePagesAsThreeWordPages() throws Exception {
        PdfExtractionResult extractionResult = new PdfExtractionResult();
        for (int pageIndex = 0; pageIndex < 3; pageIndex++) {
            extractionResult.getPages().add(new PageExtraction(pageIndex));
        }

        try (XWPFDocument document = readDocument(wordWriterService.write(extractionResult))) {
            assertThat(pageBreakCount(document)).isEqualTo(2);
        }
    }

    @Test
    void shouldWriteCompactIndexEntriesWithDottedRightTabLeaders() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "Table of Contents..............................7",
                        20,
                        40
                )
        );

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "1.1 Nested heading............................12",
                        40,
                        58
                )
        );

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "A deliberately long wrapped list entry that keeps its page number aligned"
                                + " ..............................................................24",
                        40,
                        76
                )
        );

        extractionResult.getPages().add(page);

        try (
                XWPFDocument document =
                        readDocument(
                                wordWriterService.write(
                                        extractionResult
                                )
                        )
        ) {

            assertThat(document.getParagraphs())
                    .hasSize(3);

            String xml =
                    document.getDocument()
                            .xmlText();

            assertThat(xml)
                    .contains(
                            "<w:t>Table of Contents</w:t>"
                    )
                    .contains(
                            "<w:t>7</w:t>"
                    )
                    .contains(
                            "<w:t>1.1 Nested heading</w:t>"
                    )
                    .contains(
                            "<w:t>12</w:t>"
                    )
                    .contains(
                            "<w:t>A deliberately long wrapped list entry that keeps its page number aligned</w:t>"
                    )
                    .contains(
                            "<w:t>24</w:t>"
                    )
                    .contains(
                            "<w:tab/>"
                    )
                    .contains(
                            "w:val=\"right\""
                    )
                    .contains(
                            "w:leader=\"dot\""
                    );

            assertThat(xml)
                    .doesNotContain(
                            "Table of Contents\t7"
                    );

            assertThat(
                    document.getParagraphs()
            ).allSatisfy(
                    paragraph -> {
                        assertThat(
                                paragraph.getSpacingBefore()
                        ).isZero();

                        assertThat(
                                paragraph.getSpacingAfter()
                        ).isZero();
                    }
            );
        }
    }

    @Test
    void shouldMergeSplitIndexLevelMarkerIntoOneEntryParagraph() throws Exception {
        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction page = new PageExtraction(0);
        page.getStructuredBlocks().add(paragraphBlock(0, "1.4.1", 20, 40));
        page.getStructuredBlocks().add(paragraphBlock(
                0,
                "IEEE Standard P241, Gray Book........................10",
                44,
                45
        ));
        extractionResult.getPages().add(page);

        try (XWPFDocument document = readDocument(wordWriterService.write(extractionResult))) {
            assertThat(document.getParagraphs()).hasSize(1);
            assertThat(document.getParagraphs().getFirst().getText())
                    .isEqualTo("1.4.1 IEEE Standard P241, Gray Book\t10");
        }
    }

    @Test
    void shouldNotAddExtraFlowHeightForPageBreakCarrier() throws Exception {
        PdfExtractionResult extractionResult = new PdfExtractionResult();
        extractionResult.getPages().add(new PageExtraction(0));
        extractionResult.getPages().add(new PageExtraction(1));

        try (XWPFDocument document = readDocument(wordWriterService.write(extractionResult))) {
            XWPFParagraph carrier = document.getParagraphs().getFirst();
            assertThat(carrier.getCTP().xmlText()).doesNotContain("w:spacing");
            assertThat(carrier.getCTP().xmlText()).contains("w:val=\"nextPage\"");
        }
    }

    private StructuredBlock paragraphBlock(
            int pageIndex,
            String text,
            float x,
            float y
    ) {
        return new StructuredBlock(
                pageIndex,
                BlockType.PARAGRAPH,
                text,
                x,
                y,
                200,
                14,
                List.of()
        );
    }

    private XWPFDocument readDocument(byte[] bytes) throws Exception {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    private long pageBreakCount(XWPFDocument document) {
        return document.getParagraphs().stream()
                .filter(paragraph -> paragraph.getCTP().xmlText().contains("w:type=\"page\"")
                        || paragraph.getCTP().xmlText().contains("w:val=\"nextPage\""))
                .count();
    }

    @Test
    void shouldPreserveFormattingForTableCellSpans() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction page = new PageExtraction(0);

        TextSpan span = new TextSpan(
                0,
                "Styled cell",
                10,
                10,
                60,
                12,
                "Helvetica",
                14,
                true,
                false,
                false,
                false,
                "336699"
        );

        TableCell cell = new TableCell(
                0,
                0,
                1,
                1,
                "Styled cell",
                List.of(span),
                null
        );

        StructuredBlock tableBlock = new StructuredBlock(
                0,
                BlockType.TABLE,
                "table",
                50,
                50,
                300,
                30,
                List.of(),
                List.of(List.of(cell))
        );

        page.getStructuredBlocks().add(tableBlock);
        extractionResult.getPages().add(page);

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            XWPFRun run = document.getTables()
                    .getFirst()
                    .getRow(0)
                    .getCell(0)
                    .getParagraphs()
                    .getFirst()
                    .getRuns()
                    .getFirst();

            assertThat(run.getFontFamily()).isEqualTo("Arial");
            assertThat(run.getFontSize()).isEqualTo(14);
            assertThat(run.isBold()).isTrue();
            assertThat(run.getColor()).isEqualTo("336699");
        }
    }

    @Test
    void shouldPlaceContentCoveredBackgroundImageBehindText() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();
        PageExtraction page = new PageExtraction(0);
        page.setCropWidth(600);
        page.setCropHeight(800);
        page.getStructuredBlocks().add(new StructuredBlock(
                0,
                BlockType.PARAGRAPH,
                "Overlay text",
                120,
                220,
                300,
                30,
                List.of()
        ));

        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        ImageIO.write(
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB),
                "png",
                imageBytes
        );
        page.getImages().add(new ExtractedImage(
                0,
                "background.png",
                40,
                40,
                520,
                720,
                10,
                10,
                "image/png",
                0,
                false,
                imageBytes.toByteArray()
        ));
        extractionResult.getPages().add(page);

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            assertThat(document.getDocument().xmlText())
                    .contains("behindDoc=\"1\"");
        }
    }

    @Test
    void shouldPromoteRepeatedPageEdgesToHeadersAndFooters() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();

        for (int pageIndex = 0; pageIndex < 2; pageIndex++) {
            PageExtraction page = new PageExtraction(pageIndex);
            page.setCropWidth(600);
            page.setCropHeight(800);
            page.getStructuredBlocks().add(new StructuredBlock(
                    pageIndex,
                    BlockType.PARAGRAPH,
                    "Repeated report header",
                    40,
                    30,
                    220,
                    14,
                    List.of()
            ));
            page.getStructuredBlocks().add(new StructuredBlock(
                    pageIndex,
                    BlockType.PARAGRAPH,
                    "Body page " + pageIndex,
                    40,
                    200,
                    180,
                    14,
                    List.of()
            ));
            page.getStructuredBlocks().add(new StructuredBlock(
                    pageIndex,
                    BlockType.PARAGRAPH,
                    "Repeated report footer",
                    40,
                    740,
                    220,
                    14,
                    List.of()
            ));
            extractionResult.getPages().add(page);
        }

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            assertThat(document.getHeaderList()).isNotEmpty();
            assertThat(document.getFooterList()).isNotEmpty();
            assertThat(document.getHeaderList().getFirst().getText())
                    .contains("Repeated report header");
            assertThat(document.getFooterList().getFirst().getText())
                    .contains("Repeated report footer");
            assertThat(document.getParagraphs())
                    .extracting(XWPFParagraph::getText)
                    .doesNotContain("Repeated report header", "Repeated report footer");
            assertThat(document.getParagraphs())
                    .extracting(XWPFParagraph::getText)
                    .contains("Body page 0", "Body page 1");
        }
    }

    @Test
    void shouldWriteRepeatedNumericFooterAsDynamicPageField() throws Exception {

        PdfExtractionResult extractionResult = new PdfExtractionResult();

        for (int pageIndex = 0; pageIndex < 2; pageIndex++) {
            PageExtraction page = new PageExtraction(pageIndex);
            page.setCropWidth(600);
            page.setCropHeight(800);
            page.getStructuredBlocks().add(new StructuredBlock(
                    pageIndex,
                    BlockType.PARAGRAPH,
                    "Body",
                    40,
                    200,
                    100,
                    14,
                    List.of()
            ));
            page.getStructuredBlocks().add(new StructuredBlock(
                    pageIndex,
                    BlockType.PARAGRAPH,
                    Integer.toString(pageIndex + 1),
                    290,
                    740,
                    20,
                    14,
                    List.of()
            ));
            extractionResult.getPages().add(page);
        }

        byte[] docx = wordWriterService.write(extractionResult);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx))) {
            assertThat(document.getFooterList()).isNotEmpty();
            assertThat(document.getFooterList().getFirst().getParagraphs()
                    .getFirst().getCTP().xmlText())
                    .contains("PAGE", "fldCharType=\"begin\"");
            assertThat(document.getParagraphs())
                    .extracting(XWPFParagraph::getText)
                    .doesNotContain("1", "2");
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

    @Test
    void shouldUseDifferentNumberingForSeparateUnorderedLists()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                unorderedListItem("• Java")
        );

        page.getStructuredBlocks().add(
                unorderedListItem("• Spring")
        );

        page.getStructuredBlocks().add(
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Between lists",
                        50,
                        140,
                        200,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Between lists",
                                        50,
                                        140,
                                        200,
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

        page.getStructuredBlocks().add(
                unorderedListItem("• PostgreSQL")
        );

        page.getStructuredBlocks().add(
                unorderedListItem("• Docker")
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
            List<XWPFParagraph> paragraphs =
                    document.getParagraphs();

            BigInteger firstGroupNumId =
                    paragraphs.get(0).getNumID();

            BigInteger secondGroupNumId =
                    paragraphs.get(3).getNumID();

            assertThat(firstGroupNumId)
                    .isNotNull();

            assertThat(secondGroupNumId)
                    .isNotNull();

            assertThat(secondGroupNumId)
                    .isNotEqualTo(
                            firstGroupNumId
                    );
        }
    }

    @Test
    void shouldRestartNumberingForSeparateOrderedLists() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                listItem("1. First item")
        );

        page.getStructuredBlocks().add(
                listItem("2. Second item")
        );

        page.getStructuredBlocks().add(
                new StructuredBlock(
                        0,
                        BlockType.PARAGRAPH,
                        null,
                        "Between lists",
                        50,
                        140,
                        200,
                        12,
                        List.of(
                                new TextSpan(
                                        0,
                                        "Between lists",
                                        50,
                                        140,
                                        200,
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

        page.getStructuredBlocks().add(
                listItem("1. Another first item")
        );

        page.getStructuredBlocks().add(
                listItem("2. Another second item")
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
            List<XWPFParagraph> paragraphs =
                    document.getParagraphs();

            XWPFParagraph firstListFirst =
                    paragraphs.get(0);

            XWPFParagraph firstListSecond =
                    paragraphs.get(1);

            XWPFParagraph secondListFirst =
                    paragraphs.get(3);

            XWPFParagraph secondListSecond =
                    paragraphs.get(4);

            assertThat(
                    firstListFirst.getNumID()
            ).isNotNull();

            assertThat(
                    firstListSecond.getNumID()
            ).isEqualTo(
                    firstListFirst.getNumID()
            );

            assertThat(
                    secondListFirst.getNumID()
            ).isNotNull();

            assertThat(
                    secondListSecond.getNumID()
            ).isEqualTo(
                    secondListFirst.getNumID()
            );

            assertThat(
                    secondListFirst.getNumID()
            ).isNotEqualTo(
                    firstListFirst.getNumID()
            );
        }
    }

    @Test
    void shouldPreserveDecimalNumberingStyle()
            throws Exception {

        NumberingInfo info =
                writeAndReadOrderedListItem(
                        "1. First item"
                );

        assertThat(info.paragraphText())
                .isEqualTo("First item");

        assertThat(info.numFmt())
                .isEqualTo("decimal");

        assertThat(info.levelText())
                .isEqualTo("%1.");
    }

    @Test
    void shouldPreserveLowerRomanNumberingStyle()
            throws Exception {

        NumberingInfo info =
                writeAndReadOrderedListItem(
                        "i. Introduction"
                );

        assertThat(info.numFmt())
                .isEqualTo("lowerRoman");

        assertThat(info.levelText())
                .isEqualTo("%1.");
    }

    @Test
    void shouldPreserveUpperRomanNumberingStyle()
            throws Exception {

        NumberingInfo info =
                writeAndReadOrderedListItem(
                        "IV. Results"
                );

        assertThat(info.numFmt())
                .isEqualTo("upperRoman");

        assertThat(info.levelText())
                .isEqualTo("%1.");
    }

    @Test
    void shouldPreserveLowerLetterNumberingStyle()
            throws Exception {

        NumberingInfo info =
                writeAndReadOrderedListItem(
                        "a. First item"
                );

        assertThat(info.numFmt())
                .isEqualTo("lowerLetter");

        assertThat(info.levelText())
                .isEqualTo("%1.");
    }

    @Test
    void shouldPreserveUpperLetterNumberingStyle()
            throws Exception {

        NumberingInfo info =
                writeAndReadOrderedListItem(
                        "A. First item"
                );

        assertThat(info.numFmt())
                .isEqualTo("upperLetter");

        assertThat(info.levelText())
                .isEqualTo("%1.");
    }

    @Test
    void shouldPreserveClosingParenthesisNumbering()
            throws Exception {

        NumberingInfo info =
                writeAndReadOrderedListItem(
                        "1) First item"
                );

        assertThat(info.numFmt())
                .isEqualTo("decimal");

        assertThat(info.levelText())
                .isEqualTo("%1)");
    }

    @Test
    void shouldPreserveFullyParenthesizedNumbering()
            throws Exception {

        NumberingInfo info =
                writeAndReadOrderedListItem(
                        "(1) First item"
                );

        assertThat(info.numFmt())
                .isEqualTo("decimal");

        assertThat(info.levelText())
                .isEqualTo("(%1)");
    }

    @Test
    void shouldPreserveParenthesizedLowerRomanNumbering()
            throws Exception {

        NumberingInfo info =
                writeAndReadOrderedListItem(
                        "(i) First item"
                );

        assertThat(info.numFmt())
                .isEqualTo("lowerRoman");

        assertThat(info.levelText())
                .isEqualTo("(%1)");
    }

    @Test
    void shouldPreserveStandardBulletGlyph() throws Exception {

        BulletInfo info =
                writeAndReadUnorderedListItem(
                        "• Java"
                );

        assertThat(info.paragraphText())
                .isEqualTo("Java");

        assertThat(info.levelText())
                .isEqualTo("•");
    }

    @Test
    void shouldPreserveSquareBulletGlyph() throws Exception {

        BulletInfo info =
                writeAndReadUnorderedListItem(
                        "▪ Java"
                );

        assertThat(info.paragraphText())
                .isEqualTo("Java");

        assertThat(info.levelText())
                .isEqualTo("▪");
    }

    @Test
    void shouldPreserveCheckmarkBulletGlyph() throws Exception {

        BulletInfo info =
                writeAndReadUnorderedListItem(
                        "✓ Java"
                );

        assertThat(info.paragraphText())
                .isEqualTo("Java");

        assertThat(info.levelText())
                .isEqualTo("✓");
    }

    @Test
    void shouldPreserveMiddleDotBulletGlyph() throws Exception {

        BulletInfo info =
                writeAndReadUnorderedListItem(
                        "· Java"
                );

        assertThat(info.paragraphText())
                .isEqualTo("Java");

        assertThat(info.levelText())
                .isEqualTo("·");
    }

    @Test
    void shouldUseDifferentNumberingWhenBulletGlyphChanges()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.getStructuredBlocks().add(
                unorderedListItem("• Java")
        );

        page.getStructuredBlocks().add(
                unorderedListItem("• Spring")
        );

        page.getStructuredBlocks().add(
                unorderedListItem("▪ PostgreSQL")
        );

        page.getStructuredBlocks().add(
                unorderedListItem("▪ Docker")
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
            List<XWPFParagraph> paragraphs =
                    document.getParagraphs();

            BigInteger firstGlyphNumId =
                    paragraphs.get(0).getNumID();

            BigInteger secondGlyphNumId =
                    paragraphs.get(2).getNumID();

            assertThat(
                    paragraphs.get(1).getNumID()
            ).isEqualTo(firstGlyphNumId);

            assertThat(
                    paragraphs.get(3).getNumID()
            ).isEqualTo(secondGlyphNumId);

            assertThat(secondGlyphNumId)
                    .isNotEqualTo(firstGlyphNumId);
        }
    }

    @Test
    void shouldSuppressRepeatedFooterFromBodyFlow() throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        for (int pageIndex = 0; pageIndex < 2; pageIndex++) {

            PageExtraction page =
                    new PageExtraction(pageIndex);

            page.setCropWidth(612);
            page.setCropHeight(792);

            page.getStructuredBlocks().add(
                    paragraphBlock(
                            pageIndex,
                            "Body content " + pageIndex,
                            50,
                            200
                    )
            );

            page.getStructuredBlocks().add(
                    paragraphBlock(
                            pageIndex,
                            "MAZZETTI | Electric Circuit Data Collection",
                            50,
                            740
                    )
            );

            extractionResult.getPages().add(page);
        }

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        readDocument(docx)
        ) {

            assertThat(document.getFooterList())
                    .isNotEmpty();

            assertThat(
                    document.getFooterList()
                            .getFirst()
                            .getText()
            ).contains(
                    "MAZZETTI | Electric Circuit Data Collection"
            );

            assertThat(
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .toList()
            ).doesNotContain(
                    "MAZZETTI | Electric Circuit Data Collection"
            );
        }
    }

    @Test
    void shouldSuppressRepeatedFooterWithMergedPageNumber()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction firstPage =
                new PageExtraction(0);

        firstPage.setCropWidth(612);
        firstPage.setCropHeight(792);

        firstPage.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "Body content 0",
                        50,
                        200
                )
        );

        firstPage.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "MAZZETTI | Electric Circuit Data Collection",
                        50,
                        740
                )
        );

        PageExtraction secondPage =
                new PageExtraction(1);

        secondPage.setCropWidth(612);
        secondPage.setCropHeight(792);

        secondPage.getStructuredBlocks().add(
                paragraphBlock(
                        1,
                        "Body content 1",
                        50,
                        200
                )
        );

        secondPage.getStructuredBlocks().add(
                paragraphBlock(
                        1,
                        "MAZZETTI | Electric Circuit Data Collection 23",
                        50,
                        740
                )
        );

        extractionResult.getPages().add(firstPage);
        extractionResult.getPages().add(secondPage);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        readDocument(docx)
        ) {

            assertThat(document.getFooterList())
                    .isNotEmpty();

            assertThat(
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .toList()
            )
                    .doesNotContain(
                            "MAZZETTI | Electric Circuit Data Collection"
                    )
                    .doesNotContain(
                            "MAZZETTI | Electric Circuit Data Collection 23"
                    );
        }
    }

    @Test
    void shouldNormalizeRepeatedFooterWhitespace()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction firstPage =
                new PageExtraction(0);

        firstPage.setCropWidth(612);
        firstPage.setCropHeight(792);

        firstPage.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "Body content 0",
                        50,
                        200
                )
        );

        firstPage.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "MAZZETTI | Electric Circuit Data Collection",
                        50,
                        740
                )
        );

        PageExtraction secondPage =
                new PageExtraction(1);

        secondPage.setCropWidth(612);
        secondPage.setCropHeight(792);

        secondPage.getStructuredBlocks().add(
                paragraphBlock(
                        1,
                        "Body content 1",
                        50,
                        200
                )
        );

        secondPage.getStructuredBlocks().add(
                paragraphBlock(
                        1,
                        "MAZZETTI    |    Electric Circuit Data Collection",
                        50,
                        740
                )
        );

        extractionResult.getPages().add(firstPage);
        extractionResult.getPages().add(secondPage);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        readDocument(docx)
        ) {

            assertThat(document.getFooterList())
                    .isNotEmpty();

            assertThat(
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .toList()
            )
                    .doesNotContain(
                            "MAZZETTI | Electric Circuit Data Collection"
                    )
                    .doesNotContain(
                            "MAZZETTI    |    Electric Circuit Data Collection"
                    );
        }
    }

    @Test
    void shouldNotSuppressSimilarTextInPageBody()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        for (int pageIndex = 0; pageIndex < 2; pageIndex++) {

            PageExtraction page =
                    new PageExtraction(pageIndex);

            page.setCropWidth(612);
            page.setCropHeight(792);

            page.getStructuredBlocks().add(
                    paragraphBlock(
                            pageIndex,
                            "MAZZETTI | Electric Circuit Data Collection",
                            50,
                            300
                    )
            );

            page.getStructuredBlocks().add(
                    paragraphBlock(
                            pageIndex,
                            "Repeated footer",
                            50,
                            740
                    )
            );

            extractionResult.getPages().add(page);
        }

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        readDocument(docx)
        ) {

            assertThat(
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .toList()
            ).contains(
                    "MAZZETTI | Electric Circuit Data Collection"
            );
        }
    }

    @Test
    void shouldNotMergeStandalonePageNumberIntoRepeatedFooter()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        for (int pageIndex = 0; pageIndex < 2; pageIndex++) {

            PageExtraction page =
                    new PageExtraction(pageIndex);

            page.setCropWidth(612);
            page.setCropHeight(792);

            page.getStructuredBlocks().add(
                    paragraphBlock(
                            pageIndex,
                            "Body content " + pageIndex,
                            50,
                            200
                    )
            );

            page.getStructuredBlocks().add(
                    paragraphBlock(
                            pageIndex,
                            String.valueOf(pageIndex + 1),
                            500,
                            756
                    )
            );

            page.getStructuredBlocks().add(
                    paragraphBlock(
                            pageIndex,
                            "MAZZETTI | Electric Circuit Data Collection",
                            50,
                            768
                    )
            );

            extractionResult.getPages().add(page);
        }

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        readDocument(docx)
        ) {

            List<String> bodyParagraphs =
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .toList();

            assertThat(bodyParagraphs)
                    .doesNotContain(
                            "MAZZETTI | Electric Circuit Data Collection"
                    );

            assertThat(
                    bodyParagraphs.stream()
                            .noneMatch(text ->
                                    text.contains(
                                            "MAZZETTI | Electric Circuit Data Collection"
                                    )
                            )
            ).isTrue();

            assertThat(document.getFooterList())
                    .isNotEmpty();

            assertThat(
                    document.getFooterList()
                            .stream()
                            .map(footer -> footer.getText())
                            .anyMatch(text ->
                                    text.contains(
                                            "MAZZETTI | Electric Circuit Data Collection"
                                    )
                            )
            ).isTrue();
        }
    }

    @Test
    void shouldWriteAdjacentTocEntriesAsSeparateParagraphs()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.setCropWidth(612);
        page.setCropHeight(792);

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "1",
                        72,
                        216
                )
        );

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "Introduction ......................................................................... 7",
                        96,
                        216
                )
        );

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "1.1",
                        83,
                        236
                )
        );

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "Types of health care plug loads ………………………………………… 7",
                        116,
                        236
                )
        );

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        readDocument(docx)
        ) {
            List<String> paragraphs =
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .filter(text -> !text.isBlank())
                            .toList();

            assertThat(paragraphs)
                    .containsExactly(
                            "1 Introduction\t7",
                            "1.1 Types of health care plug loads\t7"
                    );
        }
    }

    @Test
    void shouldMergeWrappedTocEntryIntoSingleParagraph()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        PageExtraction page =
                new PageExtraction(0);

        page.setCropWidth(612);
        page.setCropHeight(792);

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "1.4.3",
                        96,
                        351
                )
        );

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "LBNL, “Evaluation of Miscellaneous and Electronic Device Energy Use in Hospitals,”",
                        138,
                        351
                )
        );

        page.getStructuredBlocks().add(
                paragraphBlock(
                        0,
                        "(2012)…….. .......................................................................... 11",
                        96,
                        364
                )
        );

        extractionResult.getPages().add(page);

        byte[] docx =
                wordWriterService.write(extractionResult);

        try (
                XWPFDocument document =
                        readDocument(docx)
        ) {

            List<String> paragraphs =
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .filter(text -> !text.isBlank())
                            .toList();

            assertThat(paragraphs)
                    .containsExactly(
                            "1.4.3 LBNL, “Evaluation of Miscellaneous and Electronic Device Energy Use in Hospitals,” (2012)\t11"
                    );
        }
    }

    @Test
    void shouldNotPromoteSequentialChapterHeadingsToRunningHeader()
            throws Exception {

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        for (int pageIndex = 0;
             pageIndex < 3;
             pageIndex++) {

            PageExtraction page =
                    new PageExtraction(pageIndex);

            page.setCropWidth(600);
            page.setCropHeight(800);

            page.getStructuredBlocks().add(
                    new StructuredBlock(
                            pageIndex,
                            BlockType.HEADING,
                            "Chapter " + (pageIndex + 1),
                            40,
                            40,
                            120,
                            18,
                            List.of()
                    )
            );

            page.getStructuredBlocks().add(
                    new StructuredBlock(
                            pageIndex,
                            BlockType.PARAGRAPH,
                            "Body page " + (pageIndex + 1),
                            40,
                            180,
                            300,
                            40,
                            List.of()
                    )
            );

            extractionResult.getPages().add(page);
        }

        byte[] docx =
                wordWriterService.write(
                        extractionResult
                );

        try (XWPFDocument document =
                     new XWPFDocument(
                             new ByteArrayInputStream(docx)
                     )) {

            List<String> bodyParagraphs =
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .toList();

            assertThat(bodyParagraphs)
                    .contains(
                            "Chapter 1",
                            "Chapter 2",
                            "Chapter 3"
                    );

            assertThat(document.getHeaderList())
                    .allSatisfy(header ->
                            assertThat(header.getText())
                                    .doesNotContain(
                                            "Chapter 1",
                                            "Chapter 2",
                                            "Chapter 3"
                                    )
                    );
        }
    }

    @Test
    void shouldNotPromoteLongRepeatedBodyParagraphToRunningHeader()
            throws Exception {

        String repeatedBody =
                "Agriculture remains a cornerstone of the Ghanaian economy, "
                        + "employing a large share of the workforce and supplying food "
                        + "to both rural and urban markets. Smallholder farmers often "
                        + "struggle to reach buyers quickly, which leads to spoilage "
                        + "and price swings. Digital marketplaces aim to shorten that "
                        + "distance by connecting growers directly with customers.";

        PdfExtractionResult extractionResult =
                new PdfExtractionResult();

        for (int pageIndex = 0;
             pageIndex < 3;
             pageIndex++) {

            PageExtraction page =
                    new PageExtraction(pageIndex);

            page.setCropWidth(600);
            page.setCropHeight(800);

            page.getStructuredBlocks().add(
                    new StructuredBlock(
                            pageIndex,
                            BlockType.HEADING,
                            "Chapter " + (pageIndex + 1),
                            40,
                            40,
                            120,
                            18,
                            List.of()
                    )
            );

            page.getStructuredBlocks().add(
                    new StructuredBlock(
                            pageIndex,
                            BlockType.PARAGRAPH,
                            repeatedBody,
                            40,
                            90,
                            500,
                            100,
                            List.of()
                    )
            );

            page.getStructuredBlocks().add(
                    new StructuredBlock(
                            pageIndex,
                            BlockType.PARAGRAPH,
                            "Unique body page "
                                    + (pageIndex + 1),
                            40,
                            240,
                            300,
                            40,
                            List.of()
                    )
            );

            extractionResult.getPages().add(page);
        }

        byte[] docx =
                wordWriterService.write(
                        extractionResult
                );

        try (XWPFDocument document =
                     new XWPFDocument(
                             new ByteArrayInputStream(docx)
                     )) {

            List<String> bodyParagraphs =
                    document.getParagraphs()
                            .stream()
                            .map(XWPFParagraph::getText)
                            .toList();

            assertThat(
                    bodyParagraphs.stream()
                            .filter(repeatedBody::equals)
                            .count()
            )
                    .isEqualTo(3);

            assertThat(document.getHeaderList())
                    .allSatisfy(header ->
                            assertThat(header.getText())
                                    .doesNotContain(
                                            repeatedBody
                                    )
                    );
        }
    }
}