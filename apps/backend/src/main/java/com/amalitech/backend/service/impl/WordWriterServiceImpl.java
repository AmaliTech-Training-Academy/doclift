package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.BlockType;
import com.amalitech.backend.service.PageExtraction;
import com.amalitech.backend.service.PdfExtractionResult;
import com.amalitech.backend.service.StructuredBlock;
import com.amalitech.backend.service.TableCell;
import com.amalitech.backend.service.TextSpan;
import com.amalitech.backend.service.WordWriterService;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

import java.math.BigInteger;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.List;

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

        CTLvl level =
                abstractNum.addNewLvl();

        level.setIlvl(
                BigInteger.ZERO
        );

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

        BigInteger abstractNumId =
                numbering.addAbstractNum(
                        new XWPFAbstractNum(
                                abstractNum
                        )
                );

        return numbering.addNum(
                abstractNumId
        );
    }

    private BigInteger createBulletNumbering(
            XWPFDocument document
    ) {
        return createNumbering(
                document,
                STNumberFormat.BULLET,
                "•",
                null
        );
    }

    private BigInteger createNumberedNumbering(
            XWPFDocument document
    ) {
        return createNumbering(
                document,
                STNumberFormat.DECIMAL,
                "%1.",
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

    if (block.getType() == BlockType.TABLE) {
        writeTable(document, block);
        return;
    }

    XWPFParagraph paragraph =
            document.createParagraph();

    if (block.getType() == BlockType.HEADING) {
        paragraph.setStyle("Heading1");
    } else {
        paragraph.setStyle("Normal");
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

    private void writeTable(
            XWPFDocument document,
            StructuredBlock block
    ) {

        List<List<TableCell>> rows =
                block.getTableRows();

        if (rows == null || rows.isEmpty()) {
            return;
        }

        int columnCount = rows.getFirst().size();

        if (columnCount == 0) {
            return;
        }

        XWPFTable table =
                document.createTable(rows.size(), columnCount);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {

            XWPFTableRow tableRow =
                    table.getRow(rowIndex);

            List<TableCell> row =
                    rows.get(rowIndex);

            // Right-to-left so removing a swallowed cell never shifts
            // the index of a cell still waiting to be processed.
            for (int columnIndex = row.size() - 1;
                 columnIndex >= 0;
                 columnIndex--) {

                writeTableCell(rows, tableRow, rowIndex, columnIndex);
            }
        }
    }

    private void writeTableCell(
            List<List<TableCell>> rows,
            XWPFTableRow tableRow,
            int rowIndex,
            int columnIndex
    ) {

        TableCell cell =
                rows.get(rowIndex).get(columnIndex);

        if (cell.rowSpan() >= 1) {

            XWPFTableCell tableCell =
                    tableRow.getCell(columnIndex);

            setCellText(tableCell, cell.text());

            if (cell.columnSpan() > 1) {
                setGridSpan(tableCell, cell.columnSpan());
            }

            if (cell.rowSpan() > 1) {
                setVerticalMerge(tableCell, STMerge.RESTART);
            }

            return;
        }

        // Covered by a merge: cell.row()/cell.column() point at the
        // anchor that owns this position.
        TableCell anchor =
                rows.get(cell.row()).get(cell.column());

        boolean sameRowAsAnchor =
                cell.row() == rowIndex;

        if (sameRowAsAnchor) {
            // Swallowed by a horizontal merge on the anchor's own row.
            tableRow.removeCell(columnIndex);
            return;
        }

        if (columnIndex != cell.column()) {
            // A later row within a vertical merge that also spans
            // columns: only the anchor's column keeps a physical cell.
            tableRow.removeCell(columnIndex);
            return;
        }

        XWPFTableCell tableCell =
                tableRow.getCell(columnIndex);

        setCellText(tableCell, "");
        setVerticalMerge(tableCell, STMerge.CONTINUE);

        if (anchor.columnSpan() > 1) {
            setGridSpan(tableCell, anchor.columnSpan());
        }
    }

    private void setCellText(
            XWPFTableCell tableCell,
            String text
    ) {

        XWPFParagraph cellParagraph =
                tableCell.getParagraphArray(0) != null
                        ? tableCell.getParagraphArray(0)
                        : tableCell.addParagraph();

        XWPFRun run =
                cellParagraph.createRun();

        run.setText(text);
    }

    private void setGridSpan(
            XWPFTableCell tableCell,
            int span
    ) {

        CTTcPr tcPr =
                tableCell.getCTTc().isSetTcPr()
                        ? tableCell.getCTTc().getTcPr()
                        : tableCell.getCTTc().addNewTcPr();

        tcPr.addNewGridSpan()
                .setVal(BigInteger.valueOf(span));
    }

    private void setVerticalMerge(
            XWPFTableCell tableCell,
            STMerge.Enum mergeType
    ) {

        CTTcPr tcPr =
                tableCell.getCTTc().isSetTcPr()
                        ? tableCell.getCTTc().getTcPr()
                        : tableCell.getCTTc().addNewTcPr();

        tcPr.addNewVMerge()
                .setVal(mergeType);
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
}