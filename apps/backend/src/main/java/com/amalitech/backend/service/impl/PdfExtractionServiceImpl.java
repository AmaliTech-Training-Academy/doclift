package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.util.Matrix;


import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class PdfExtractionServiceImpl implements PdfExtractionService {

    private static final int MIN_TABLE_ROWS = 3;
    private static final int MIN_TABLE_CHANNELS = 2;
    private static final float TABLE_MIN_CELL_GAP = 8f;
    private static final float TABLE_CELL_GAP_FONT_FACTOR = 0.9f;
    private static final float TABLE_MIN_CHANNEL_WIDTH = 2f;
    private static final float TABLE_MIN_ROW_TOLERANCE = 2f;
    private static final int MIN_TWO_COLUMN_TABLE_ROWS = 3;
    private static final float COLUMN_ALIGNMENT_TOLERANCE = 20f;
    private static final float TABLE_BLOCK_MERGE_MARGIN = 4f;
    private static final float MIN_COLUMN_SPLIT_GAP = 4f;
    private static final float COLUMN_SPLIT_SPACE_WIDTH_FACTOR = 2f;
    private static final int MIN_FOOTNOTE_ENTRIES = 2;
    private static final Pattern PURE_DIGITS = Pattern.compile("\\d+");
    private static final float FOOTNOTE_MARKER_MAX_SIZE_RATIO = 0.85f;

    private final StructureRecoveryService structureRecoveryService;
    private final BorderedTableDetector borderedTableDetector = new BorderedTableDetector();

    public PdfExtractionServiceImpl(
            StructureRecoveryService structureRecoveryService
    ) {
        this.structureRecoveryService = structureRecoveryService;
    }

    @Override
    public PdfExtractionResult extract(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return extract(document);
        }
    }

    public PdfExtractionResult extract(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("The input stream is null.");
        }
        return extract(inputStream.readAllBytes());
    }

    public PdfExtractionResult extract(PDDocument document) throws IOException {
        PdfExtractionResult result = new PdfExtractionResult();

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            PageExtraction pageExtraction = new PageExtraction(pageIndex);
            pageExtraction.setPageWidth(page.getCropBox().getWidth());
            pageExtraction.setPageHeight(page.getCropBox().getHeight());

            List<TextSpan> textSpans = extractTextSpans(pageIndex, page);
            pageExtraction.getTextSpans().addAll(textSpans);

            pageExtraction.getImages().addAll(extractImages(pageIndex, page));
            pageExtraction.getCandidateTableRegions().addAll(detectCandidateTableRegions(pageIndex, textSpans));

            List<DetectedTable> borderedTables =
                    detectBorderedTables(pageIndex, page, textSpans, pageExtraction);

            structureRecoveryService.recoverStructure(pageExtraction);

            mergeBorderedTables(pageExtraction, borderedTables);

            recoverFootnotes(pageIndex, pageExtraction, result);

            result.getPages().add(pageExtraction);
        }

        return result;
    }

    /**
     * Runs bordered-table detection and registers each detected table as a
     * {@link TableRegion} on the page before {@link StructureRecoveryService}
     * runs. Structure recovery consults those candidate regions to keep
     * table-area spans out of paragraph joining, so detection must happen
     * first - merging the detected tables into {@link StructuredBlock}s
     * happens separately, after structure recovery, via
     * {@link #mergeBorderedTables}.
     */
    private List<DetectedTable> detectBorderedTables(
            int pageIndex,
            PDPage page,
            List<TextSpan> textSpans,
            PageExtraction pageExtraction
    ) throws IOException {

        TableLineStreamEngine lineEngine = new TableLineStreamEngine(page);
        lineEngine.processPage(page);

        List<DetectedTable> tables = borderedTableDetector.detect(
                pageIndex,
                textSpans,
                lineEngine.getHorizontalLines(),
                lineEngine.getVerticalLines()
        );

        for (DetectedTable table : tables) {
            pageExtraction.getCandidateTableRegions().add(
                    new TableRegion(
                            table.pageIndex(),
                            table.x(),
                            table.y(),
                            table.width(),
                            table.height(),
                            table.rowCount(),
                            table.columnCount()
                    )
            );
        }

        return tables;
    }

    private void mergeBorderedTables(
            PageExtraction pageExtraction,
            List<DetectedTable> tables
    ) {
        for (DetectedTable table : tables) {
            mergeDetectedTable(pageExtraction, table);
        }
    }

    /**
     * Recognizes a trailing block made entirely of repeated
     * "marker + note text" entries - e.g. a Google Docs footnote area
     * sitting apart from the main flow near a page's bottom margin - and
     * converts it from a floating paragraph into real footnote content:
     * the block is removed from the page's flow, its entries become
     * {@link Footnote}s on the result, and every other block on the page
     * whose leading span is one of those markers has that marker
     * stripped and is flagged (via {@link StructuredBlock#setFootnoteKey}
     * or {@link TableCell#footnoteKey()}) so the Word writer can render a
     * proper footnote reference there instead of leaving the bare digit
     * sitting in the running text.
     *
     * <p>Deliberately conservative: the trailing block must consist of
     * nothing but marker/text pairs (see {@link #parseFootnoteEntries}),
     * which ordinary prose essentially never does, so this does not fire
     * on a page's last paragraph just because it happens to end in a
     * number.
     */
    private void recoverFootnotes(
            int pageIndex,
            PageExtraction pageExtraction,
            PdfExtractionResult result
    ) {
        List<StructuredBlock> blocks = pageExtraction.getStructuredBlocks();

        if (blocks.isEmpty()) {
            return;
        }

        StructuredBlock last = blocks.get(blocks.size() - 1);

        if (last.getType() != BlockType.PARAGRAPH) {
            return;
        }

        List<FootnoteEntry> entries = parseFootnoteEntries(last.getSpans());

        if (entries.size() < MIN_FOOTNOTE_ENTRIES) {
            return;
        }

        blocks.remove(blocks.size() - 1);

        Map<String, String> keysByMarker = new LinkedHashMap<>();

        for (FootnoteEntry entry : entries) {
            String key = pageIndex + ":" + entry.marker();
            keysByMarker.put(entry.marker(), key);
            result.getFootnotes().add(new Footnote(key, entry.text()));
        }

        float maxMarkerFontSize =
                determineBodyFontSize(pageExtraction.getTextSpans())
                        * FOOTNOTE_MARKER_MAX_SIZE_RATIO;

        for (StructuredBlock block : blocks) {
            applyFootnoteReference(block, keysByMarker, maxMarkerFontSize);
        }
    }

    /**
     * A genuine footnote reference marker is rendered in a visibly
     * smaller (often superscript) font than the body text it's attached
     * to - see {@link #parseFootnoteEntries}. Matching a candidate span
     * against {@code keysByMarker} by text alone would also catch any
     * ordinary full-size digit that happens to equal a marker, such as a
     * table's numeric ID/rank column or a numbered heading whose number
     * landed in its own span; requiring the candidate to actually be
     * smaller than the page's body font closes that off.
     */
    private float determineBodyFontSize(List<TextSpan> spans) {
        List<Float> sizes = spans.stream()
                .map(TextSpan::getFontSize)
                .filter(size -> size > 0f)
                .sorted()
                .toList();

        if (sizes.isEmpty()) {
            return 0f;
        }

        return sizes.get((sizes.size() - 1) / 2);
    }

    private record FootnoteEntry(String marker, String text) {
    }

    /**
     * A footnote area is a sequence of spans that alternates, with no
     * leftover content, between a standalone all-digit marker span and
     * the note text following it - the shape produced when a marker and
     * its text are rendered in different runs (typically a smaller,
     * raised marker font) and {@link StructureRecoveryServiceImpl} has
     * folded the resulting lines into one trailing paragraph. Returns an
     * empty list the moment the shape breaks (e.g. a block that is just
     * ordinary prose ending in a number), so callers can treat an empty
     * result as "not a footnote area" without a separate check.
     */
    private List<FootnoteEntry> parseFootnoteEntries(List<TextSpan> spans) {
        if (spans == null || spans.size() < MIN_FOOTNOTE_ENTRIES * 2) {
            return List.of();
        }

        List<FootnoteEntry> entries = new ArrayList<>();
        int i = 0;

        while (i < spans.size()) {

            String marker = strippedText(spans.get(i));

            if (!PURE_DIGITS.matcher(marker).matches()) {
                return List.of();
            }

            i++;

            StringBuilder body = new StringBuilder();

            while (i < spans.size()
                    && !PURE_DIGITS.matcher(strippedText(spans.get(i))).matches()) {

                if (!body.isEmpty()) {
                    body.append(' ');
                }

                body.append(strippedText(spans.get(i)));
                i++;
            }

            if (body.isEmpty()) {
                return List.of();
            }

            entries.add(new FootnoteEntry(marker, body.toString()));
        }

        return entries;
    }

    private String strippedText(TextSpan span) {
        return span.getText() == null ? "" : span.getText().strip();
    }

    private void applyFootnoteReference(
            StructuredBlock block,
            Map<String, String> keysByMarker,
            float maxMarkerFontSize
    ) {
        if (block.getType() == BlockType.TABLE) {
            applyFootnoteReferenceToTable(block, keysByMarker, maxMarkerFontSize);
            return;
        }

        List<TextSpan> spans = block.getSpans();

        if (spans == null || spans.size() < 2) {
            return;
        }

        TextSpan candidate = spans.get(0);

        if (!looksLikeFootnoteMarker(candidate, maxMarkerFontSize)) {
            return;
        }

        String key = keysByMarker.get(strippedText(candidate));

        if (key == null) {
            return;
        }

        block.setFootnoteKey(key);
        spans.remove(0);
    }

    private boolean looksLikeFootnoteMarker(
            TextSpan span,
            float maxMarkerFontSize
    ) {
        return span.getFontSize() > 0f
                && span.getFontSize() < maxMarkerFontSize;
    }

    private void applyFootnoteReferenceToTable(
            StructuredBlock block,
            Map<String, String> keysByMarker,
            float maxMarkerFontSize
    ) {
        List<List<TableCell>> rows = block.getTableRows();

        if (rows == null) {
            return;
        }

        for (List<TableCell> row : rows) {
            for (int column = 0; column < row.size(); column++) {

                TableCell cell = row.get(column);

                if (cell.rowSpan() < 1
                        || cell.spans() == null
                        || cell.spans().isEmpty()
                        || !looksLikeFootnoteMarker(cell.spans().getFirst(), maxMarkerFontSize)) {
                    continue;
                }

                String marker = strippedText(cell.spans().getFirst());
                String key = keysByMarker.get(marker);

                if (key == null) {
                    continue;
                }

                List<TextSpan> remainingSpans =
                        cell.spans().subList(1, cell.spans().size());

                String remainingText = cell.text().startsWith(marker)
                        ? cell.text().substring(marker.length()).stripLeading()
                        : cell.text();

                row.set(
                        column,
                        new TableCell(
                                cell.row(),
                                cell.column(),
                                cell.rowSpan(),
                                cell.columnSpan(),
                                remainingText,
                                remainingSpans,
                                key
                        )
                );
            }
        }
    }

    private void mergeDetectedTable(
            PageExtraction pageExtraction,
            DetectedTable table
    ) {
        List<StructuredBlock> blocks = pageExtraction.getStructuredBlocks();
        List<StructuredBlock> remaining = new ArrayList<>();

        int insertIndex = -1;

        for (StructuredBlock block : blocks) {

            if (blockOverlapsTable(block, table)) {
                if (insertIndex == -1) {
                    insertIndex = remaining.size();
                }
                continue;
            }

            remaining.add(block);
        }

        if (insertIndex == -1) {
            insertIndex = findInsertionIndexByPosition(remaining, table);
        }

        remaining.add(
                Math.min(insertIndex, remaining.size()),
                toTableBlock(table)
        );

        blocks.clear();
        blocks.addAll(remaining);
    }

    private int findInsertionIndexByPosition(
            List<StructuredBlock> remaining,
            DetectedTable table
    ) {
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).getY() > table.y()) {
                return i;
            }
        }

        return remaining.size();
    }

    private boolean blockOverlapsTable(
            StructuredBlock block,
            DetectedTable table
    ) {
        float centerX = block.getX() + (block.getWidth() / 2f);
        float centerY = block.getY() + (block.getHeight() / 2f);

        return centerX >= table.x() - TABLE_BLOCK_MERGE_MARGIN
                && centerX <= table.x() + table.width() + TABLE_BLOCK_MERGE_MARGIN
                && centerY >= table.y() - TABLE_BLOCK_MERGE_MARGIN
                && centerY <= table.y() + table.height() + TABLE_BLOCK_MERGE_MARGIN;
    }

    private StructuredBlock toTableBlock(DetectedTable table) {
        StringBuilder text = new StringBuilder();

        for (List<TableCell> row : table.cells()) {

            if (!text.isEmpty()) {
                text.append('\n');
            }

            for (int column = 0; column < row.size(); column++) {

                if (column > 0) {
                    text.append(" | ");
                }

                text.append(row.get(column).text());
            }
        }

        StructuredBlock block = new StructuredBlock(
                table.pageIndex(),
                BlockType.TABLE,
                text.toString(),
                table.x(),
                table.y(),
                table.width(),
                table.height(),
                List.of(),
                table.cells()
        );

        block.setColumnWidths(table.columnWidths());
        block.setRowHeights(table.rowHeights());

        return block;
    }

    private List<DetectionCell> reconstructDetectionCells(
            List<TextSpan> row
    ) {

        List<DetectionCell> cells =
                new ArrayList<>();

        if (row == null || row.isEmpty()) {
            return cells;
        }

        List<TextSpan> sorted =
                new ArrayList<>(row);

        sorted.sort(
                Comparator.comparing(TextSpan::getX)
        );

        StringBuilder currentText =
                new StringBuilder();

        float currentLeft = sorted.getFirst().getX();
        float currentRight =
                sorted.getFirst().getX()
                        + sorted.getFirst().getWidth();

        float previousFontSize =
                sorted.getFirst().getFontSize();

        currentText.append(
                sorted.getFirst().getText()
        );

        for (int i = 1; i < sorted.size(); i++) {

            TextSpan previous =
                    sorted.get(i - 1);

            TextSpan current =
                    sorted.get(i);

            float previousRight =
                    previous.getX()
                            + previous.getWidth();

            float gap =
                    current.getX()
                            - previousRight;

            float threshold =
                    Math.max(
                            TABLE_MIN_CELL_GAP,
                            Math.max(
                                    previousFontSize,
                                    current.getFontSize()
                            ) * TABLE_CELL_GAP_FONT_FACTOR
                    );

            if (gap > threshold) {

                cells.add(
                        new DetectionCell(
                                currentText.toString(),
                                currentLeft,
                                Math.max(
                                        0f,
                                        currentRight - currentLeft
                                )
                        )
                );

                currentText =
                        new StringBuilder();

                currentLeft =
                        current.getX();

                currentRight =
                        current.getX()
                                + current.getWidth();
            }

            currentText.append(
                    current.getText()
            );

            currentRight =
                    Math.max(
                            currentRight,
                            current.getX()
                                    + current.getWidth()
                    );

            previousFontSize =
                    current.getFontSize();
        }

        cells.add(
                new DetectionCell(
                        currentText.toString(),
                        currentLeft,
                        Math.max(
                                0f,
                                currentRight - currentLeft
                        )
                )
        );

        return cells;
    }

    private boolean isWhitespaceOnlyRun(
            List<TextPosition> positions
    ) {

        if (positions == null
                || positions.isEmpty()) {
            return false;
        }

        for (TextPosition position : positions) {

            String unicode =
                    position.getUnicode();

            if (unicode != null
                    && !unicode.isBlank()) {
                return false;
            }
        }

        return true;
    }

    private boolean isBoldFont(String fontName) {
        if (fontName == null) {
            return false;
        }

        String normalized = fontName.toLowerCase();

        return normalized.contains("bold")
                || normalized.contains("black")
                || normalized.contains("heavy");
    }

    private boolean isBold(TextPosition position) {

        if (position.getFont() != null
                && position.getFont().getFontDescriptor() != null
                && position.getFont()
                .getFontDescriptor()
                .isForceBold()) {

            return true;
        }

        return isBoldFont(
                getFontName(position)
        );
    }

    private boolean isItalic(TextPosition position) {

        if (position.getFont() != null
                && position.getFont().getFontDescriptor() != null
                && position.getFont()
                .getFontDescriptor()
                .isItalic()) {

            return true;
        }

        return isItalicFont(
                getFontName(position)
        );
    }

    private boolean isItalicFont(String fontName) {
        if (fontName == null) {
            return false;
        }

        String normalized = fontName.toLowerCase();

        return normalized.contains("italic")
                || normalized.contains("oblique");
    }


    private boolean hasSameFormatting(
            TextPosition first,
            TextPosition second
    ) {
        String firstFont = getFontName(first);
        String secondFont = getFontName(second);

        boolean sameFont =
                firstFont.equals(secondFont);

        boolean sameSize =
                Math.abs(
                        first.getFontSizeInPt()
                                - second.getFontSizeInPt()
                ) < 0.01f;

        return sameFont && sameSize;
    }

    private String getFontName(TextPosition position) {
        if (position.getFont() == null
                || position.getFont().getName() == null) {
            return "unknown";
        }

        return position.getFont().getName();
    }

    private boolean addTextSpanFromRun(
            List<TextSpan> spans,
            int pageIndex,
            List<TextPosition> positions,
            TextPosition previousPositionBeforeRun,
            boolean forceWordSeparatorBefore
    ) {

        if (positions == null || positions.isEmpty()) {
            return false;
        }

        boolean wordSeparatorBefore =
                forceWordSeparatorBefore
                        || (
                        previousPositionBeforeRun != null
                                && needsWordSeparator(
                                previousPositionBeforeRun,
                                positions.getFirst()
                        )
                );

        StringBuilder textBuilder =
                new StringBuilder();

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        TextPosition previousPosition = null;

        for (TextPosition position : positions) {

            String unicode =
                    position.getUnicode();

            if (unicode != null) {

                if (needsWordSeparator(
                        previousPosition,
                        position
                )) {
                    textBuilder.append(' ');
                }

                textBuilder.append(unicode);
            }

            minX = Math.min(
                    minX,
                    position.getXDirAdj()
            );

            maxX = Math.max(
                    maxX,
                    position.getXDirAdj()
                            + position.getWidthDirAdj()
            );

            minY = Math.min(
                    minY,
                    position.getYDirAdj()
            );

            maxY = Math.max(
                    maxY,
                    position.getYDirAdj()
                            + position.getHeightDir()
            );

            previousPosition = position;
        }

        String extractedText =
                textBuilder.toString();

        if (extractedText.isBlank()
                || minX == Float.MAX_VALUE) {
            return false;
        }

        TextPosition first =
                positions.getFirst();

        spans.add(
                new TextSpan(
                        pageIndex,
                        extractedText,
                        minX,
                        minY,
                        Math.max(
                                0f,
                                maxX - minX
                        ),
                        Math.max(
                                0f,
                                maxY - minY
                        ),
                        getFontName(first),
                        first.getFontSizeInPt(),
                        isBold(first),
                        isItalic(first),

                        // TODO: Implement underline detection and recovery
                        false,
                        wordSeparatorBefore
                )
        );

        return true;
    }

    private boolean looksLikeTwoColumnTable(
            List<List<TextSpan>> rows
    ) {

        if (rows.size() < MIN_TWO_COLUMN_TABLE_ROWS) {
            return false;
        }

        Float expectedLeftX = null;
        Float expectedRightX = null;

        int alignedRows = 0;
        int tabularRows = 0;

        for (List<TextSpan> row : rows) {

            List<DetectionCell> cells =
                    reconstructDetectionCells(row);

            if (cells.size() != 2) {
                continue;
            }

            DetectionCell left =
                    cells.get(0);

            DetectionCell right =
                    cells.get(1);

            if (expectedLeftX == null) {
                expectedLeftX = left.x();
                expectedRightX = right.x();
            }

            boolean leftAligned =
                    Math.abs(
                            left.x()
                                    - expectedLeftX
                    ) <= COLUMN_ALIGNMENT_TOLERANCE;

            boolean rightAligned =
                    Math.abs(
                            right.x()
                                    - expectedRightX
                    ) <= COLUMN_ALIGNMENT_TOLERANCE;

            if (!leftAligned || !rightAligned) {
                continue;
            }

            alignedRows++;

            if (looksLikeTabularValue(left.text())
                    || looksLikeTabularValue(right.text())) {
                tabularRows++;
            }
        }

        return alignedRows >= MIN_TWO_COLUMN_TABLE_ROWS
                && tabularRows
                >= MIN_TWO_COLUMN_TABLE_ROWS - 1;
    }

    private boolean looksLikeMultiColumnTable(
            List<List<TextSpan>> rows
    ) {

        if (rows.size() < MIN_TABLE_ROWS) {
            return false;
        }

        int tabularRows = 0;

        for (List<TextSpan> row : rows) {

            List<DetectionCell> cells =
                    reconstructDetectionCells(row);

            boolean hasTabularCell = false;

            for (DetectionCell cell : cells) {

                if (looksLikeTabularValue(
                        cell.text()
                )) {
                    hasTabularCell = true;
                    break;
                }
            }

            if (hasTabularCell) {
                tabularRows++;
            }
        }

        return tabularRows >= MIN_TABLE_ROWS - 1;
    }

    private boolean looksLikeTabularValue(String text) {
        if (text == null) {
            return false;
        }

        String value = text.trim();

        if (value.isEmpty()) {
            return false;
        }

        return value.matches(
                "^[\\p{Sc}]?\\d[\\d,]*(?:\\.\\d+)?%?$"
                        + "|^\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?$"
                        + "|^[A-Z0-9_-]{1,12}$"
        );
    }

    private List<TextSpan> extractTextSpans(
            int pageIndex,
            PDPage page
    ) throws IOException {

        final List<TextSpan> spans = new ArrayList<>();

        PDFTextStripper stripper = new PDFTextStripper() {

            private TextPosition lastTextPosition;
            private boolean pendingWhitespaceSeparator;

            {
                this.output = new StringWriter();
            }

            @Override
            protected void writeString(
                    String string,
                    List<TextPosition> textPositions
            ) {

                if (textPositions == null
                        || textPositions.isEmpty()) {
                    return;
                }

                List<TextPosition> currentRun =
                        new ArrayList<>();

                TextPosition previousPositionBeforeRun =
                        lastTextPosition;

                for (TextPosition position : textPositions) {

                    if (currentRun.isEmpty()) {
                        currentRun.add(position);
                        continue;
                    }

                    TextPosition previous =
                            currentRun.getLast();

                    if (hasSameFormatting(
                            previous,
                            position
                    ) && !isColumnSizedGap(
                            previous,
                            position
                    )) {

                        currentRun.add(position);

                    } else {

                        boolean whitespaceOnly =
                                isWhitespaceOnlyRun(
                                        currentRun
                                );

                        boolean added =
                                addTextSpanFromRun(
                                        spans,
                                        pageIndex,
                                        currentRun,
                                        previousPositionBeforeRun,
                                        pendingWhitespaceSeparator
                                );

                        if (whitespaceOnly) {
                            pendingWhitespaceSeparator = true;
                        } else if (added) {
                            pendingWhitespaceSeparator = false;
                        }

                        previousPositionBeforeRun =
                                currentRun.getLast();

                        currentRun =
                                new ArrayList<>();

                        currentRun.add(position);
                    }
                }

                if (!currentRun.isEmpty()) {

                    boolean whitespaceOnly =
                            isWhitespaceOnlyRun(
                                    currentRun
                            );

                    boolean added =
                            addTextSpanFromRun(
                                    spans,
                                    pageIndex,
                                    currentRun,
                                    previousPositionBeforeRun,
                                    pendingWhitespaceSeparator
                            );

                    if (whitespaceOnly) {
                        pendingWhitespaceSeparator = true;
                    } else if (added) {
                        pendingWhitespaceSeparator = false;
                    }
                }

                lastTextPosition =
                        textPositions.getLast();
            }
        @Override
        protected void writeLineSeparator()
        throws IOException {

            lastTextPosition = null;
            pendingWhitespaceSeparator = false;

            super.writeLineSeparator();
        }
        };

        stripper.setSortByPosition(true);
        stripper.processPage(page);

        return spans;
    }


    private List<ExtractedImage> extractImages(int pageIndex, PDPage page) throws IOException {
        List<ExtractedImage> images = new ArrayList<>();
        new ImageLocationStreamEngine(pageIndex, images).processPage(page);
        return images;
    }

    private static class ImageLocationStreamEngine extends PDFStreamEngine {

        private final int pageIndex;
        private final List<ExtractedImage> images;
        private final Deque<COSBase> formsInProgress = new ArrayDeque<>();

        ImageLocationStreamEngine(int pageIndex, List<ExtractedImage> images) {
            this.pageIndex = pageIndex;
            this.images = images;
            addOperator(new Concatenate(this));
            addOperator(new SetGraphicsStateParameters(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
            addOperator(new SetMatrix(this));
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (!"Do".equals(operator.getName()) || operands.isEmpty()
                    || !(operands.getFirst() instanceof COSName objectName)) {
                super.processOperator(operator, operands);
                return;
            }

            PDXObject xObject = getResources().getXObject(objectName);
            switch (xObject) {
                case null -> {

                }
                case PDImageXObject image -> recordImagePlacement(objectName.getName(), image);
                case PDFormXObject form -> {
                    COSBase formKey = form.getCOSObject();
                    formsInProgress.push(formKey);
                    try {
                        showForm(form);
                    } finally {
                        formsInProgress.pop();
                    }
                }
                default -> {
                }
            }

        }


        private void recordImagePlacement(String imageName, PDImageXObject image) {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();

            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;

            float[][] unitCorners = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            for (float[] corner : unitCorners) {
                Point2D.Float p = ctm.transformPoint(corner[0], corner[1]);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }

            images.add(new ExtractedImage(
                    pageIndex,
                    imageName,
                    minX,
                    minY,
                    maxX - minX,
                    maxY - minY,
                    image.getWidth(),
                    image.getHeight(),
                    image.getSuffix()
            ));
        }
    }

    private boolean isColumnSizedGap(
            TextPosition previous,
            TextPosition current
    ) {
        float gap =
                current.getXDirAdj()
                        - (previous.getXDirAdj()
                        + previous.getWidthDirAdj());

        float spaceWidth = Math.max(
                previous.getWidthOfSpace(),
                current.getWidthOfSpace()
        );

        float threshold = Math.max(
                MIN_COLUMN_SPLIT_GAP,
                spaceWidth * COLUMN_SPLIT_SPACE_WIDTH_FACTOR
        );

        return gap > threshold;
    }

    private boolean needsWordSeparator(
            TextPosition previous,
            TextPosition current
    ) {
        if (previous == null
                || current == null
                || previous.getUnicode() == null
                || previous.getUnicode().isEmpty()
                || current.getUnicode() == null
                || current.getUnicode().isBlank()) {
            return false;
        }

        String previousUnicode = previous.getUnicode();

        if (Character.isWhitespace(
                previousUnicode.charAt(previousUnicode.length() - 1)
        )) {
            return false;
        }

        float gap =
                current.getXDirAdj()
                        - (previous.getXDirAdj()
                        + previous.getWidthDirAdj());

        float spaceWidth =
                Math.max(
                        previous.getWidthOfSpace(),
                        current.getWidthOfSpace()
                );

        return gap > Math.max(
                1f,
                spaceWidth * 0.5f
        );
    }


    private List<TableRegion> detectCandidateTableRegions(
            int pageIndex,
            List<TextSpan> textSpans
    ) {
        List<TableRegion> regions = new ArrayList<>();

        if (textSpans.size() < 3) {
            return regions;
        }

        List<List<TextSpan>> rows = groupIntoRows(textSpans);

        int start = 0;

        while (start < rows.size()) {
            List<float[]> channels = findCellGaps(rows.get(start));
            int end = start + 1;

            while (end < rows.size()
                    && channels.size() >= MIN_TABLE_CHANNELS) {

                List<float[]> narrowed =
                        intersectGaps(
                                channels,
                                findCellGaps(rows.get(end))
                        );

                if (narrowed.size() < MIN_TABLE_CHANNELS) {
                    break;
                }

                channels = narrowed;
                end++;
            }

            int rowCount = end - start;

            if (rowCount >= MIN_TABLE_ROWS
                    && channels.size() >= MIN_TABLE_CHANNELS
                    && looksLikeMultiColumnTable(rows.subList(start, end))) {

                regions.add(
                        toTableRegion(
                                pageIndex,
                                rows.subList(start, end),
                                channels.size() + 1
                        )
                );

                start = end;
            } else {
                start++;
            }
        }


        for (int rowStart = 0; rowStart < rows.size(); rowStart++) {
            int rowEnd = rowStart;

            while (rowEnd < rows.size()
                    && reconstructDetectionCells(
                    rows.get(rowEnd)
            ).size() == 2) {

                rowEnd++;
            }

            if (rowEnd - rowStart >= MIN_TWO_COLUMN_TABLE_ROWS) {
                List<List<TextSpan>> candidateRows =
                        rows.subList(rowStart, rowEnd);

                if (looksLikeTwoColumnTable(candidateRows)) {
                    regions.add(
                            toTableRegion(
                                    pageIndex,
                                    candidateRows,
                                    2
                            )
                    );
                }
            }

            if (rowEnd > rowStart) {
                rowStart = rowEnd - 1;
            }
        }

        return regions;
    }

    private List<List<TextSpan>> groupIntoRows(List<TextSpan> textSpans) {
        List<TextSpan> sorted = new ArrayList<>(textSpans);
        sorted.sort(Comparator.comparing(TextSpan::getY).thenComparing(TextSpan::getX));

        List<List<TextSpan>> rows = new ArrayList<>();
        List<TextSpan> current = new ArrayList<>();
        float currentY = 0f;

        for (TextSpan span : sorted) {
            float tolerance = Math.max(TABLE_MIN_ROW_TOLERANCE, span.getHeight() * 0.5f);
            if (!current.isEmpty() && Math.abs(span.getY() - currentY) > tolerance) {
                rows.add(current);
                current = new ArrayList<>();
            }
            if (current.isEmpty()) {
                currentY = span.getY();
            }
            current.add(span);
        }

        if (!current.isEmpty()) {
            rows.add(current);
        }

        for (List<TextSpan> row : rows) {
            row.sort(Comparator.comparing(TextSpan::getX));
        }

        return rows;
    }

    private List<float[]> findCellGaps(List<TextSpan> row) {
        List<float[]> gaps = new ArrayList<>();
        float right = -Float.MAX_VALUE;
        float fontSize = 0f;

        for (TextSpan span : row) {
            if (right != -Float.MAX_VALUE) {
                float threshold = Math.max(
                        TABLE_MIN_CELL_GAP,
                        Math.max(fontSize, span.getFontSize()) * TABLE_CELL_GAP_FONT_FACTOR
                );
                if (span.getX() - right > threshold) {
                    gaps.add(new float[]{right, span.getX()});
                }
            }
            right = Math.max(right, span.getX() + span.getWidth());
            fontSize = span.getFontSize();
        }

        return gaps;
    }

    private List<float[]> intersectGaps(List<float[]> channels, List<float[]> gaps) {
        List<float[]> result = new ArrayList<>();
        for (float[] channel : channels) {
            for (float[] gap : gaps) {
                float left = Math.max(channel[0], gap[0]);
                float right = Math.min(channel[1], gap[1]);
                if (right - left >= TABLE_MIN_CHANNEL_WIDTH) {
                    result.add(new float[]{left, right});
                }
            }
        }
        return result;
    }

    private TableRegion toTableRegion(int pageIndex, List<List<TextSpan>> rows, int columnCount) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (List<TextSpan> row : rows) {
            for (TextSpan span : row) {
                minX = Math.min(minX, span.getX());
                maxX = Math.max(maxX, span.getX() + span.getWidth());
                minY = Math.min(minY, span.getY());
                maxY = Math.max(maxY, span.getY() + span.getHeight());
            }
        }

        return new TableRegion(
                pageIndex,
                minX,
                minY,
                Math.max(0f, maxX - minX),
                Math.max(0f, maxY - minY),
                rows.size(),
                columnCount
        );
    }
}
