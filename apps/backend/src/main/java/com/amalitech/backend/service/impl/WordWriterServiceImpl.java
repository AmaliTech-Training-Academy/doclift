package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

import java.math.BigInteger;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.List;

@Service
public class WordWriterServiceImpl implements WordWriterService {

    private static final Pattern LIST_MARKER_PATTERN =
            Pattern.compile(
                    "^\\s*(?:"
                            + "[•◦▪‣⁃∙*\\-]"
                            + "|"
                            + "(?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)[.)]"
                            + "|"
                            + "\\((?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)\\)"
                            + ")\\s*"
            );

    private void applyFormatting(
            XWPFRun run,
            TextSpan span
    ) {

        run.setBold(span.isBold());
        run.setItalic(span.isItalic());

        run.setUnderline(
                span.isUnderline()
                        ? UnderlinePatterns.SINGLE
                        : UnderlinePatterns.NONE
        );

        if (span.getFontSize() > 0) {
            run.setFontSize(
                    Math.round(span.getFontSize())
            );
        }
    }



    private boolean hasLaterNonBlankListContent(
            List<TextSpan> spans,
            int startIndex
    ) {

        for (int i = startIndex; i < spans.size(); i++) {

            String text =
                    spans.get(i).getText();

            if (text == null) {
                continue;
            }

            String cleaned =
                    LIST_MARKER_PATTERN
                            .matcher(text)
                            .replaceFirst("");

            if (!cleaned.isBlank()) {
                return true;
            }
        }

        return false;
    }

    private BigInteger createNumbering(
            XWPFDocument document,
            BigInteger abstractNumId,
            STNumberFormat.Enum format,
            String levelText,
            BigInteger start
    ) {
        XWPFNumbering numbering =
                document.getNumbering();

        if (numbering == null) {
            numbering =
                    document.createNumbering();
        }

        CTAbstractNum abstractNum =
                CTAbstractNum.Factory.newInstance();

        abstractNum.setAbstractNumId(abstractNumId);

        CTLvl level =
                abstractNum.addNewLvl();

        level.setIlvl(BigInteger.ZERO);

        if (start != null) {
            level.addNewStart()
                    .setVal(start);
        }

        level.addNewNumFmt()
                .setVal(format);

        level.addNewLvlText()
                .setVal(levelText);

        level.addNewLvlJc()
                .setVal(STJc.LEFT);

        var pPr = level.addNewPPr();

        var tabs = pPr.addNewTabs();
        var tab = tabs.addNewTab();
        tab.setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.NUM
        );
        tab.setPos(BigInteger.valueOf(720));

        var ind = pPr.addNewInd();
        ind.setLeft(BigInteger.valueOf(720));
        ind.setHanging(BigInteger.valueOf(360));

        numbering.addAbstractNum(
                new XWPFAbstractNum(abstractNum)
        );

        return numbering.addNum(
                abstractNumId
        );
    }

    private BigInteger createBulletNumbering(
            XWPFDocument document,
            BigInteger abstractNumId
    ) {
        return createNumbering(
                document,
                abstractNumId,
                STNumberFormat.BULLET,
                "•",
                null
        );
    }

    private float getBlockMaxFontSize(
            StructuredBlock block
    ) {
        if (block.getSpans() == null
                || block.getSpans().isEmpty()) {
            return 0f;
        }

        return block.getSpans().stream()
                .map(TextSpan::getFontSize)
                .filter(size -> size > 0f)
                .max(Float::compare)
                .orElse(0f);
    }

    private float determineBodyFontSize(
            PdfExtractionResult extractionResult
    ) {
        List<Float> sizes =
                extractionResult.getPages().stream()
                        .flatMap(page ->
                                page.getStructuredBlocks().stream()
                        )
                        .filter(block ->
                                block.getType() == BlockType.PARAGRAPH
                                        || block.getType()
                                        == BlockType.LIST_ITEM
                        )
                        .flatMap(block ->
                                block.getSpans().stream()
                        )
                        .map(TextSpan::getFontSize)
                        .filter(size -> size > 0f)
                        .sorted()
                        .toList();

        if (sizes.isEmpty()) {
            return 0f;
        }

        return sizes.get(
                (sizes.size() - 1) / 2
        );
    }

    private String resolveHeadingStyle(
            StructuredBlock block,
            float bodyFontSize
    ) {
        float headingSize =
                getBlockMaxFontSize(block);

        if (bodyFontSize <= 0f
                || headingSize <= 0f) {
            return "Heading2";
        }

        float ratio =
                headingSize / bodyFontSize;

        if (ratio >= 1.60f) {
            return "Heading1";
        }

        if (ratio >= 1.30f) {
            return "Heading2";
        }

        return "Heading3";
    }

    private BigInteger createNumberedNumbering(
            XWPFDocument document,
            BigInteger abstractNumId,
            String firstItemText
    ) {
        return createNumbering(
                document,
                abstractNumId,
                resolveOrderedNumberFormat(
                        firstItemText
                ),
                resolveOrderedLevelText(
                        firstItemText
                ),
                BigInteger.ONE
        );
    }


    private void writeListRuns(
            XWPFParagraph paragraph,
            StructuredBlock block
    ) {

        if (block.getSpans() == null
                || block.getSpans().isEmpty()) {

            XWPFRun run =
                    paragraph.createRun();

            run.setText(
                    removeListMarker(block.getText())
                            .strip()
            );

            return;
        }

        boolean markerRemoved = false;
        boolean firstContentWritten = false;

        String previousText = null;

        List<TextSpan> spans =
                block.getSpans();

        for (int i = 0; i < spans.size(); i++) {

            TextSpan span =
                    spans.get(i);

            String text =
                    span.getText();

            if (text == null) {
                continue;
            }

            if (!markerRemoved) {

                String cleaned =
                        removeListMarker(text);

                if (!cleaned.equals(text)
                        || LIST_MARKER_PATTERN
                        .matcher(text)
                        .find()) {

                    markerRemoved = true;
                    text = cleaned;
                }
            }

            if (text.isBlank()) {
                continue;
            }

            if (!firstContentWritten) {
                text = text.stripLeading();
            }
            if (!hasLaterNonBlankListContent(
                    spans,
                    i + 1
            )) {
                text = text.stripTrailing();
            }

            if (text.isEmpty()) {
                continue;
            }

            if (span.isWordSeparatorBefore()
                    && previousText != null
                    && !previousText.isEmpty()
                    && !Character.isWhitespace(
                    previousText.charAt(
                            previousText.length() - 1
                    )
            )
                    && !Character.isWhitespace(
                    text.charAt(0)
            )) {

                text = " " + text;
            }

            XWPFRun run =
                    paragraph.createRun();

            run.setText(text);

            applyFormatting(run, span);

            previousText = text;
            firstContentWritten = true;
        }
    }

    private void applyParagraphSpacing(
            XWPFParagraph paragraph,
            StructuredBlock block
    ) {
        if (block.getType() == BlockType.HEADING) {
            paragraph.setSpacingBefore(240); // 12 pt
            paragraph.setSpacingAfter(120);  // 6 pt
            return;
        }

        if (block.getType() == BlockType.LIST_ITEM) {
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(40);   // 2 pt
            return;
        }

        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(120);      // 6 pt
    }
    private String removeListMarker(String text) {

        if (text == null) {
            return "";
        }

        return LIST_MARKER_PATTERN
                .matcher(text)
                .replaceFirst("");
    }

    @Override
    public byte[] write(PdfExtractionResult extractionResult) {



        if (extractionResult == null) {
            throw new IllegalArgumentException(
                    "Extraction result cannot be null."
            );
        }

        float bodyFontSize =
                determineBodyFontSize(
                        extractionResult
                );
        int nextAbstractNumId = 0;

        try (
                XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            BigInteger activeBulletNumId = null;
            BigInteger activeNumberedNumId = null;
            ListType activeListType = null;

            for (PageExtraction page : extractionResult.getPages()) {

                for (StructuredBlock block :
                        page.getStructuredBlocks()) {

                    if (block.getType() != BlockType.LIST_ITEM) {
                        activeListType = null;
                        activeBulletNumId = null;
                        activeNumberedNumId = null;

                        writeBlock(
                                document,
                                block,
                                null,
                                null,
                                bodyFontSize
                        );

                        continue;
                    }

                    ListType currentType =
                            block.getListType();

                    if (currentType != activeListType) {

                        BigInteger abstractNumId =
                                BigInteger.valueOf(
                                        nextAbstractNumId++
                                );

                        if (currentType == ListType.ORDERED) {

                            activeNumberedNumId =
                                    createNumberedNumbering(
                                            document,
                                            abstractNumId,
                                            block.getText()
                                    );

                        } else if (currentType == ListType.UNORDERED) {

                            activeBulletNumId =
                                    createBulletNumbering(
                                            document,
                                            abstractNumId
                                    );
                        }

                        activeListType = currentType;
                    }

                    writeBlock(
                            document,
                            block,
                            activeBulletNumId,
                            activeNumberedNumId,
                            bodyFontSize
                    );
                }
            }

            document.write(output);

            return output.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to generate Word document.",
                    e
            );
        }
    }

    private void writeBlock(
            XWPFDocument document,
            StructuredBlock block,
            BigInteger bulletNumId,
            BigInteger numberedNumId,
            float bodyFontSize
    ) {

    XWPFParagraph paragraph =
            document.createParagraph();

    applyParagraphSpacing(
            paragraph,
            block
    );

        if (block.getType() == BlockType.HEADING) {

            paragraph.setStyle(
                    resolveHeadingStyle(
                            block,
                            bodyFontSize
                    )
            );

        } else {
            paragraph.setStyle("Normal");
        }

    if (block.getType() == BlockType.LIST_ITEM) {

        if (block.getListType() == ListType.ORDERED) {
            paragraph.setNumID(numberedNumId);
            paragraph.setNumILvl(BigInteger.ZERO);

        } else if (block.getListType() == ListType.UNORDERED) {
            paragraph.setNumID(bulletNumId);
            paragraph.setNumILvl(BigInteger.ZERO);
        }

        writeListRuns(paragraph, block);

        return;
    }

    writeRuns(paragraph, block);
}

    private void writeRuns(
            XWPFParagraph paragraph,
            StructuredBlock block
    ) {

        if (block.getSpans() == null
                || block.getSpans().isEmpty()) {

            XWPFRun run =
                    paragraph.createRun();

            run.setText(
                    block.getText() == null
                            ? ""
                            : block.getText().strip()
            );

            return;
        }

        List<TextSpan> spans =
                block.getSpans().stream()
                        .filter(span ->
                                span.getText() != null
                                        && !span.getText().isBlank()
                        )
                        .toList();

        String previousText = null;

        for (int i = 0; i < spans.size(); i++) {

            TextSpan span =
                    spans.get(i);

            String text =
                    span.getText();

            if (i == 0) {
                text = text.stripLeading();
            }

            if (i == spans.size() - 1) {
                text = text.stripTrailing();
            }

            if (text.isEmpty()) {
                continue;
            }

            if (span.isWordSeparatorBefore()
                    && previousText != null
                    && !previousText.isEmpty()
                    && !Character.isWhitespace(
                    previousText.charAt(
                            previousText.length() - 1
                    )
            )
                    && !Character.isWhitespace(
                    text.charAt(0)
            )) {

                text = " " + text;
            }

            XWPFRun run =
                    paragraph.createRun();

            run.setText(text);

            applyFormatting(run, span);

            previousText = text;
        }
    }

    private STNumberFormat.Enum resolveOrderedNumberFormat(
            String text
    ) {
        if (text == null) {
            return STNumberFormat.DECIMAL;
        }

        String trimmed =
                text.stripLeading();

        String marker =
                trimmed.split("\\s+", 2)[0];

        String normalized =
                marker
                        .replace("(", "")
                        .replace(")", "")
                        .replace(".", "");

        if (normalized.matches(
                "[ivxlcdm]+"
        )) {
            return STNumberFormat.LOWER_ROMAN;
        }

        if (normalized.matches(
                "[IVXLCDM]+"
        )) {
            return STNumberFormat.UPPER_ROMAN;
        }

        if (normalized.matches(
                "[a-z]"
        )) {
            return STNumberFormat.LOWER_LETTER;
        }

        if (normalized.matches(
                "[A-Z]"
        )) {
            return STNumberFormat.UPPER_LETTER;
        }

        return STNumberFormat.DECIMAL;
    }

    private String resolveOrderedLevelText(
            String text
    ) {
        if (text == null) {
            return "%1.";
        }

        String trimmed = text.stripLeading();

        if (trimmed.matches(
                "^\\((?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)\\)\\s+.*"
        )) {
            return "(%1)";
        }

        if (trimmed.matches(
                "^(?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)\\)\\s+.*"
        )) {
            return "%1)";
        }

        return "%1.";
    }
}