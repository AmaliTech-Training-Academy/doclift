package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.BlockType;
import com.amalitech.backend.service.PageExtraction;
import com.amalitech.backend.service.PdfExtractionResult;
import com.amalitech.backend.service.StructuredBlock;
import com.amalitech.backend.service.TextSpan;
import com.amalitech.backend.service.WordWriterService;
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

@Service
public class WordWriterServiceImpl implements WordWriterService {


    private static final Pattern NUMBERED_PATTERN =
            Pattern.compile(
                    "^\\s*(?:\\d+|[a-z]|[ivx]+|[IVX]{2,}|[A-Z])[.)]\\s*"
            );

    private static final Pattern LIST_MARKER_PATTERN =
            Pattern.compile(
                    "^\\s*(?:[•◦▪‣⁃∙*-]|(?:\\d+|[a-z]|[ivx]+|[IVX]{2,}|[A-Z])[.)])\\s*"
            );

    private boolean isNumberedListItem(String text) {

        if (text == null || text.isBlank()) {
            return false;
        }

        return NUMBERED_PATTERN
                .matcher(text)
                .find();
    }

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

    private BigInteger createBulletNumbering(
            XWPFDocument document
    ) {

        XWPFNumbering numbering =
                document.getNumbering();

        if (numbering == null) {
            numbering =
                    document.createNumbering();
        }

        CTAbstractNum abstractNum =
                CTAbstractNum.Factory.newInstance();

        BigInteger abstractNumId =
                BigInteger.ZERO;

        abstractNum.setAbstractNumId(
                abstractNumId
        );

        CTLvl level =
                abstractNum.addNewLvl();

        level.setIlvl(
                BigInteger.ZERO
        );

        level.addNewNumFmt()
                .setVal(
                        STNumberFormat.BULLET
                );

        level.addNewLvlText()
                .setVal("•");

        level.addNewLvlJc()
                .setVal(STJc.LEFT);


        BigInteger createdAbstractId =
                numbering.addAbstractNum(
                        new XWPFAbstractNum(
                                abstractNum
                        )
                );

        return numbering.addNum(
                createdAbstractId
        );
    }

    private BigInteger createNumberedNumbering(
            XWPFDocument document
    ) {

        XWPFNumbering numbering =
                document.getNumbering();

        if (numbering == null) {
            numbering =
                    document.createNumbering();
        }

        CTAbstractNum abstractNum =
                CTAbstractNum.Factory.newInstance();

        BigInteger abstractNumId =
                BigInteger.ONE;

        abstractNum.setAbstractNumId(
                abstractNumId
        );

        CTLvl level =
                abstractNum.addNewLvl();

        level.setIlvl(
                BigInteger.ZERO
        );

        level.addNewStart()
                .setVal(BigInteger.ONE);

        level.addNewNumFmt()
                .setVal(
                        STNumberFormat.DECIMAL
                );

        level.addNewLvlText()
                .setVal("%1.");

        level.addNewLvlJc()
                .setVal(STJc.LEFT);


        BigInteger createdAbstractId =
                numbering.addAbstractNum(
                        new org.apache.poi.xwpf.usermodel.XWPFAbstractNum(
                                abstractNum
                        )
                );

        return numbering.addNum(
                createdAbstractId
        );
    }

    private boolean needsSpaceBetweenRuns(
            String previous,
            String current
    ) {

        if (previous == null
                || previous.isEmpty()
                || current == null
                || current.isEmpty()) {
            return false;
        }

        char previousLast =
                previous.charAt(previous.length() - 1);

        char currentFirst =
                current.charAt(0);

        return !Character.isWhitespace(previousLast)
                && !Character.isWhitespace(currentFirst);
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
            );

            return;
        }

        boolean markerRemoved = false;
        String previousText = null;

        for (TextSpan span : block.getSpans()) {

            String text = span.getText();

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

            if (text == null || text.isBlank()) {
                continue;
            }

            if (needsSpaceBetweenRuns(
                    previousText,
                    text
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

        try (
                XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            BigInteger bulletNumId =
                    createBulletNumbering(document);

            BigInteger numberedNumId =
                    createNumberedNumbering(document);

            for (PageExtraction page : extractionResult.getPages()) {

                for (StructuredBlock block :
                        page.getStructuredBlocks()) {

                    writeBlock(
                            document,
                            block,
                            bulletNumId,
                            numberedNumId
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
        BigInteger numberedNumId
) {

    XWPFParagraph paragraph =
            document.createParagraph();

    if (block.getType() == BlockType.HEADING) {
        paragraph.setStyle("Heading1");
    }

    if (block.getType() == BlockType.LIST_ITEM) {

        if (isNumberedListItem(block.getText())) {
            paragraph.setNumID(numberedNumId);
        } else {
            paragraph.setNumID(bulletNumId);
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

            run.setText(block.getText());

            return;
        }

        String previousText = null;

        for (TextSpan span : block.getSpans()) {

            String text = span.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            if (needsSpaceBetweenRuns(
                    previousText,
                    text
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
}