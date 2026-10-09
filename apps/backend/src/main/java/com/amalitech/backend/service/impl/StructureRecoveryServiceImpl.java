package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class StructureRecoveryServiceImpl implements StructureRecoveryService {

    private static final float MIN_LINE_TOLERANCE = 2.0f;
    private static final float COLUMN_SPLIT_TOLERANCE = 20f;
    private static final float LINE_TOLERANCE_FACTOR = 0.5f;
    private static final float PARAGRAPH_GAP_FACTOR = 1.2f;
    private static final float INDENT_TOLERANCE = 20f;
    private static final float MAX_FIRST_LINE_INDENT = 48f;
    private static final float HEADING_FONT_RATIO = 1.25f;
    private static final int HEADING_MAX_LENGTH = 120;
    private static final float MIN_COLUMN_START_SEPARATION_RATIO =
            0.25f;

    private static final float COLUMN_START_CLUSTER_TOLERANCE =
            20f;
    private static final Pattern UNORDERED_LIST_PATTERN =
            Pattern.compile(
                    "^\\s*[•●◦▪‣⁃∙·✓*\\-]\\s+.+"
            );

    private static final Pattern ORDERED_LIST_PATTERN =
            Pattern.compile(
                    "^\\s*(?:"
                            + "(?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)[.)]"
                            + "|"
                            + "\\((?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)\\)"
                            + ")\\s+.+"
            );
    private static final Pattern FIGURE_INDEX_ENTRY_PATTERN =
            Pattern.compile(
                    "^\\s*Figure\\s+\\d+\\s*:\\s*.+",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern TABLE_INDEX_ENTRY_PATTERN =
            Pattern.compile(
                    "^\\s*Table\\s+\\d+\\s*:\\s*.+",
                    Pattern.CASE_INSENSITIVE
            );
    private static final float MIN_SEGMENT_GAP = 8f;
    private static final float SEGMENT_GAP_FONT_FACTOR = 0.9f;
    private static final int MIN_MULTI_COLUMN_ROWS = 3;
    private static final float MIN_COLUMN_AGREEMENT_RATIO = 0.5f;
    private static final float MIN_COLUMN_SPLIT_POSITION = 0.3f;
    private static final float MAX_COLUMN_SPLIT_POSITION = 0.7f;
    private static final float TABLE_REGION_MARGIN = 1f;
    private static final float RIGHT_MARGIN_CLUSTER_TOLERANCE = 10f;
    private static final int MIN_RIGHT_MARGIN_SUPPORT = 2;
    private static final float RIGHT_MARGIN_REACH_TOLERANCE = 30f;
    private static final String SENTENCE_TERMINATORS = ".!?";
    private static final String TRAILING_QUOTE_CHARACTERS = "\"')]";
    private static final float CENTER_ALIGNMENT_TOLERANCE = 6f;
    private static final float MAX_CENTERED_WIDTH_RATIO = 0.7f;
    private static final float JUSTIFY_LINE_TOLERANCE = 5f;
    private static final float LINE_SPACING_CLUSTER_TOLERANCE = 1.5f;
    private static final int MIN_LINE_SPACING_SUPPORT = 3;
    private static final float MAX_LINE_SPACING_TO_FONT_RATIO = 1.8f;
    private static final float PAGE_EDGE_START_RATIO = 0.94f;


    @Override
    public void recoverStructure(PageExtraction pageExtraction) {
        if (pageExtraction == null) {
            throw new IllegalArgumentException("Page extraction cannot be null.");
        }

        pageExtraction.getStructuredBlocks().clear();
        pageExtraction.getColumnRegions().clear();

        if (pageExtraction.getTextSpans().isEmpty()) {
            pageExtraction.setMultiColumn(false);
            return;
        }

        ReadingOrderResult readingOrderResult =
                buildReadingOrder(
                        pageExtraction.getTextSpans(),
                        pageExtraction.getCandidateTableRegions(),
                        pageExtraction.getPageHeight()
                );

        List<LogicalLine> lines =
                readingOrderResult.lines();

        pageExtraction.getColumnRegions().addAll(
                readingOrderResult.columnRegions()
        );

        pageExtraction.setMultiColumn(
                !readingOrderResult.columnRegions().isEmpty()
        );

        float bodyFontSize = determineBodyFontSize(
                pageExtraction.getTextSpans()
        );

        Float bodyRightMargin = determineBodyRightMargin(lines);
        Float dominantLineSpacing = determineDominantLineSpacing(lines, bodyFontSize);

        List<StructuredBlock> blocks = buildStructuredBlocks(
                pageExtraction.getPageIndex(),
                lines,
                bodyFontSize,
                bodyRightMargin,
                pageExtraction.getPageWidth(),
                pageExtraction.getPageHeight(),
                dominantLineSpacing
        );

        pageExtraction.getStructuredBlocks().addAll(blocks);
    }

    private StructuredBlock toParagraphBlock(
            int pageIndex,
            List<LogicalLine> lines
    ) {
        List<TextSpan> spans = new ArrayList<>();

        StringBuilder text = new StringBuilder();

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (LogicalLine line : lines) {
            appendLineSpans(spans, line);

            if (!text.isEmpty()) {
                text.append(' ');
            }

            text.append(line.getText());

            minX = Math.min(minX, line.getX());
            minY = Math.min(minY, line.getY());
            maxX = Math.max(maxX, line.getX() + line.getWidth());
            maxY = Math.max(maxY, line.getY() + line.getHeight());
        }

        return new StructuredBlock(
                pageIndex,
                BlockType.PARAGRAPH,
                null,
                text.toString(),
                minX,
                minY,
                maxX - minX,
                maxY - minY,
                spans
        );
    }

    private void appendLineSpans(
            List<TextSpan> spans,
            LogicalLine line
    ) {
        List<TextSpan> lineSpans = line.getSpans();

        if (spans.isEmpty() || lineSpans.isEmpty()) {
            spans.addAll(lineSpans);
            return;
        }

        TextSpan first = lineSpans.getFirst();

        if (first.isWordSeparatorBefore()) {
            spans.addAll(lineSpans);
            return;
        }

        spans.add(
                new TextSpan(
                        first.getPageIndex(),
                        first.getText(),
                        first.getX(),
                        first.getY(),
                        first.getWidth(),
                        first.getHeight(),
                        first.getFontName(),
                        first.getFontSize(),
                        first.isBold(),
                        first.isItalic(),
                        first.isUnderline(),
                        true,
                        first.getColorHex()
                )
        );

        spans.addAll(lineSpans.subList(1, lineSpans.size()));
    }

    private List<StructuredBlock> buildStructuredBlocks(
            int pageIndex,
            List<LogicalLine> lines,
            float bodyFontSize,
            Float bodyRightMargin,
            float pageWidth,
            float pageHeight,
            Float dominantLineSpacing
    ) {
        List<StructuredBlock> blocks = new ArrayList<>();
        List<LogicalLine> paragraphLines = new ArrayList<>();

        for (LogicalLine line : lines) {

            if (line.isTableRow()) {
                flushParagraph(
                        blocks,
                        paragraphLines,
                        pageIndex,
                        pageWidth,
                        bodyRightMargin
                );

                for (LogicalLine cell : splitRowIntoCells(line)) {
                    blocks.add(
                            toBlock(
                                    pageIndex,
                                    cell,
                                    BlockType.PARAGRAPH,
                                    null,
                                    BlockAlignment.LEFT
                            )
                    );
                }

                continue;
            }

            if (isHeading(line, bodyFontSize)) {
                flushParagraph(
                        blocks,
                        paragraphLines,
                        pageIndex,
                        pageWidth,
                        bodyRightMargin
                );

                blocks.add(
                        toBlock(
                                pageIndex,
                                line,
                                BlockType.HEADING,
                                null,
                                detectSingleLineAlignment(
                                        line,
                                        pageWidth
                                )
                        )
                );

                continue;
            }

            ListType listType = detectListType(line);

            if (listType != null) {
                flushParagraph(
                        blocks,
                        paragraphLines,
                        pageIndex,
                        pageWidth,
                        bodyRightMargin
                );

                blocks.add(
                        toBlock(
                                pageIndex,
                                line,
                                BlockType.LIST_ITEM,
                                listType,
                                BlockAlignment.LEFT
                        )
                );

                continue;
            }

            if (paragraphLines.isEmpty()) {
                paragraphLines.add(line);
                continue;
            }

            LogicalLine previous =
                    paragraphLines.getLast();

            if (belongsToSameParagraph(
                    previous,
                    line,
                    paragraphLines.size() == 1,
                    bodyRightMargin,
                    pageHeight,
                    dominantLineSpacing
            )) {
                paragraphLines.add(line);
            } else {
                flushParagraph(
                        blocks,
                        paragraphLines,
                        pageIndex,
                        pageWidth,
                        bodyRightMargin
                );

                paragraphLines.add(line);
            }
        }

        flushParagraph(
                blocks,
                paragraphLines,
                pageIndex,
                pageWidth,
                bodyRightMargin
        );

        return blocks;
    }
    private void flushParagraph(
            List<StructuredBlock> blocks,
            List<LogicalLine> paragraphLines,
            int pageIndex,
            float pageWidth,
            Float bodyRightMargin
    ) {
        if (paragraphLines.isEmpty()) {
            return;
        }

        StructuredBlock block = toParagraphBlock(
                pageIndex,
                new ArrayList<>(paragraphLines)
        );

        block.setAlignment(
                detectParagraphAlignment(
                        paragraphLines,
                        pageWidth,
                        bodyRightMargin
                )
        );

        blocks.add(block);

        paragraphLines.clear();
    }

    private StructuredBlock toBlock(
            int pageIndex,
            LogicalLine line,
            BlockType type,
            ListType listType,
            BlockAlignment alignment
    ) {
        StructuredBlock block = new StructuredBlock(
                pageIndex,
                type,
                listType,
                line.getText(),
                line.getX(),
                line.getY(),
                line.getWidth(),
                line.getHeight(),
                new ArrayList<>(line.getSpans())
        );

        block.setAlignment(alignment);

        return block;
    }

    private BlockAlignment detectSingleLineAlignment(
            LogicalLine line,
            float pageWidth
    ) {
        if (pageWidth <= 0f
                || line.getWidth() > pageWidth * MAX_CENTERED_WIDTH_RATIO) {
            return BlockAlignment.LEFT;
        }

        float leftGap = line.getX();
        float rightGap = pageWidth - (line.getX() + line.getWidth());

        boolean isCentered = leftGap > CENTER_ALIGNMENT_TOLERANCE
                && Math.abs(leftGap - rightGap) <= CENTER_ALIGNMENT_TOLERANCE;

        return isCentered ? BlockAlignment.CENTER : BlockAlignment.LEFT;
    }


    private BlockAlignment detectParagraphAlignment(
            List<LogicalLine> paragraphLines,
            float pageWidth,
            Float bodyRightMargin
    ) {
        if (paragraphLines.size() == 1) {
            return detectSingleLineAlignment(
                    paragraphLines.getFirst(),
                    pageWidth
            );
        }

        if (bodyRightMargin == null) {
            return BlockAlignment.LEFT;
        }

        for (int i = 0; i < paragraphLines.size() - 1; i++) {

            LogicalLine line = paragraphLines.get(i);
            float lineRight = line.getX() + line.getWidth();

            if ((bodyRightMargin - lineRight) > JUSTIFY_LINE_TOLERANCE) {
                return BlockAlignment.LEFT;
            }
        }

        return BlockAlignment.JUSTIFY;
    }

    private boolean belongsToSameParagraph(
            LogicalLine previous,
            LogicalLine current,
            boolean previousIsFirstLine,
            Float bodyRightMargin,
            float pageHeight,
            Float dominantLineSpacing
    ) {

        if (current.getY() < previous.getY()) {
            return false;
        }
        if (startsFigureOrTableIndexEntry(current)) {
            return false;
        }
        if (crossesBottomPageEdgeBoundary(
                previous,
                current,
                pageHeight
        )) {
            return false;
        }

        if (current.isTableRow() != previous.isTableRow()) {
            return false;
        }

        boolean closeVertically;

        if (dominantLineSpacing != null) {

            float baselineDelta = current.getY() - previous.getY();

            closeVertically =
                    Math.abs(baselineDelta - dominantLineSpacing)
                            <= LINE_SPACING_CLUSTER_TOLERANCE;
        } else {
            float previousBottom =
                    previous.getY() + previous.getHeight();

            float verticalGap =
                    current.getY() - previousBottom;

            float referenceHeight = Math.max(
                    previous.getAverageHeight(),
                    current.getAverageHeight()
            );

            float allowedGap =
                    referenceHeight * PARAGRAPH_GAP_FACTOR;

            closeVertically =
                    verticalGap <= allowedGap;
        }

        float indentDelta = current.getX() - previous.getX();


        boolean firstLineIndent = previousIsFirstLine
                && indentDelta < 0f
                && -indentDelta <= MAX_FIRST_LINE_INDENT;

        boolean similarlyIndented =
                Math.abs(indentDelta) <= INDENT_TOLERANCE
                        || firstLineIndent;

        if (!closeVertically || !similarlyIndented) {
            return false;
        }


        return !endsWithSentenceTerminator(previous.getText())
                || reachesRightMargin(previous, bodyRightMargin);
    }

    private boolean reachesRightMargin(
            LogicalLine line,
            Float bodyRightMargin
    ) {
        if (bodyRightMargin == null) {
            return false;
        }

        float lineRight = line.getX() + line.getWidth();

        return (bodyRightMargin - lineRight) <= RIGHT_MARGIN_REACH_TOLERANCE;
    }

    private boolean endsWithSentenceTerminator(String text) {
        if (text == null) {
            return false;
        }

        String trimmed = text.stripTrailing();

        int index = trimmed.length() - 1;

        while (index >= 0
                && TRAILING_QUOTE_CHARACTERS.indexOf(trimmed.charAt(index)) >= 0) {
            index--;
        }

        if (index < 0) {
            return false;
        }

        return SENTENCE_TERMINATORS.indexOf(trimmed.charAt(index)) >= 0;
    }


    private Float determineBodyRightMargin(List<LogicalLine> lines) {
        List<Float> rightEdges = new ArrayList<>();

        for (LogicalLine line : lines) {
            if (line.isTableRow()) {
                continue;
            }

            rightEdges.add(line.getX() + line.getWidth());
        }

        Float bestMargin = null;
        int bestSupport = 0;

        for (Float candidate : rightEdges) {
            int support = 0;
            float total = 0f;

            for (Float other : rightEdges) {
                if (Math.abs(candidate - other) <= RIGHT_MARGIN_CLUSTER_TOLERANCE) {
                    support++;
                    total += other;
                }
            }

            if (support > bestSupport) {
                bestSupport = support;
                bestMargin = total / support;
            }
        }

        if (bestSupport < MIN_RIGHT_MARGIN_SUPPORT) {
            return null;
        }

        return bestMargin;
    }

    private Float determineDominantLineSpacing(
            List<LogicalLine> lines,
            float bodyFontSize
    ) {
        List<Float> sortedY = new ArrayList<>();

        for (LogicalLine line : lines) {
            if (line.isTableRow()) {
                continue;
            }

            sortedY.add(line.getY());
        }

        sortedY.sort(Float::compare);

        List<Float> deltas = new ArrayList<>();

        for (int i = 1; i < sortedY.size(); i++) {
            float delta = sortedY.get(i) - sortedY.get(i - 1);

            if (delta > 0f) {
                deltas.add(delta);
            }
        }

        Float bestSpacing = null;
        int bestSupport = 0;

        for (Float candidate : deltas) {
            int support = 0;
            float total = 0f;

            for (Float other : deltas) {
                if (Math.abs(candidate - other) <= LINE_SPACING_CLUSTER_TOLERANCE) {
                    support++;
                    total += other;
                }
            }

            if (support > bestSupport) {
                bestSupport = support;
                bestSpacing = total / support;
            }
        }

        if (bestSupport < MIN_LINE_SPACING_SUPPORT) {
            return null;
        }

        if (bodyFontSize > 0f
                && bestSpacing > bodyFontSize * MAX_LINE_SPACING_TO_FONT_RATIO) {
            return null;
        }

        return bestSpacing;
    }

    private ReadingOrderResult buildReadingOrder(
            List<TextSpan> textSpans,
            List<TableRegion> tableRegions,
            float pageHeight
    ) {
        List<TextSpan> flowSpans = new ArrayList<>();
        List<TextSpan> tableSpans = new ArrayList<>();

        for (TextSpan span : textSpans) {
            if (isInsideTableRegion(span, tableRegions)) {
                tableSpans.add(span);
            } else {
                flowSpans.add(span);
            }
        }


        List<LogicalLine> physicalRows =
                groupSpansIntoLines(
                        flowSpans,
                        pageHeight
                );

        Float repeatedRowSplit =
                detectRepeatedColumnSplit(
                        physicalRows
                );

        Float verticalBandCandidate =
                detectColumnSplitFromVerticalBands(
                        physicalRows
                );

        boolean verticalBandSplit =
                verticalBandCandidate != null;

        Float splitX =
                verticalBandSplit
                        ? verticalBandCandidate
                        : repeatedRowSplit;

        List<LogicalLine> tableRows =
                groupSpansIntoLines(
                        tableSpans,
                        pageHeight
                );

        for (LogicalLine tableRow : tableRows) {
            tableRow.markAsTableRow();
        }

        physicalRows.addAll(tableRows);
        physicalRows.sort(
                Comparator
                        .comparing(LogicalLine::getY)
                        .thenComparing(LogicalLine::getX)
        );

        if (splitX == null) {
            return new ReadingOrderResult(
                    physicalRows,
                    List.of()
            );
        }

        List<LogicalLine> ordered = new ArrayList<>();
        List<LogicalLine> leftColumn = new ArrayList<>();
        List<LogicalLine> rightColumn = new ArrayList<>();
        List<ColumnRegion> columnRegions =
                new ArrayList<>();

        int firstColumnRow = -1;

        for (int i = 0;
             i < physicalRows.size();
             i++) {

            LogicalLine row =
                    physicalRows.get(i);

            if (row.isTableRow()) {
                continue;
            }

            if (verticalBandSplit) {

                boolean belongsToLeftBand =
                        row.getX()
                                < splitX
                                - COLUMN_START_CLUSTER_TOLERANCE;

                boolean belongsToRightBand =
                        row.getX()
                                > splitX
                                + COLUMN_START_CLUSTER_TOLERANCE;

                if (!isSpanningRow(
                        row,
                        splitX
                )
                        && (belongsToLeftBand
                        || belongsToRightBand)
                        && hasNearbyOppositeColumnSupport(
                        physicalRows,
                        i,
                        splitX
                )) {

                    firstColumnRow = i;
                    break;
                }
            } else if (hasGutterAt(
                    row,
                    splitX
            )) {
                firstColumnRow = i;
                break;
            }
        }

        if (firstColumnRow == -1) {
            return new ReadingOrderResult(
                    physicalRows,
                    List.of()
            );
        }

        for (int i = 0; i < firstColumnRow; i++) {
            ordered.add(physicalRows.get(i));
        }

        for (int i = firstColumnRow;
             i < physicalRows.size();
             i++) {

            LogicalLine row = physicalRows.get(i);

            if (isSpanningRow(row, splitX)) {
                flushColumnSection(
                        ordered,
                        leftColumn,
                        rightColumn,
                        columnRegions,
                        splitX
                );
                ordered.add(row);
                continue;
            }

            if (!verticalBandSplit
                    && !hasNearbyColumnSupport(
                    physicalRows,
                    i,
                    splitX
            )) {
                flushColumnSection(
                        ordered,
                        leftColumn,
                        rightColumn,
                        columnRegions,
                        splitX
                );

                for (int j = i;
                     j < physicalRows.size();
                     j++) {

                    ordered.add(
                            physicalRows.get(j)
                    );
                }

                return new ReadingOrderResult(
                        ordered,
                        columnRegions
                );
            }

            List<TextSpan> leftSpans =
                    new ArrayList<>();

            List<TextSpan> rightSpans =
                    new ArrayList<>();

            for (TextSpan span : row.getSpans()) {
                if (span.getX() < splitX) {
                    leftSpans.add(span);
                } else {
                    rightSpans.add(span);
                }
            }

            if (!leftSpans.isEmpty()) {
                LogicalLine left = new LogicalLine();

                for (TextSpan span : leftSpans) {
                    left.add(span);
                }

                left.sortLeftToRight();
                leftColumn.add(left);
            }

            if (!rightSpans.isEmpty()) {
                LogicalLine right = new LogicalLine();

                for (TextSpan span : rightSpans) {
                    right.add(span);
                }

                right.sortLeftToRight();
                rightColumn.add(right);
            }
        }

        flushColumnSection(
                ordered,
                leftColumn,
                rightColumn,
                columnRegions,
                splitX
        );

        return new ReadingOrderResult(
                ordered,
                columnRegions
        );
    }

    private boolean hasNearbyOppositeColumnSupport(
            List<LogicalLine> rows,
            int currentIndex,
            float splitX
    ) {
        LogicalLine current =
                rows.get(currentIndex);

        boolean currentIsLeft =
                current.getX() < splitX;

        float verticalTolerance =
                Math.max(
                        24f,
                        current.getAverageHeight() * 3f
                );

        for (int i = 0;
             i < rows.size();
             i++) {

            if (i == currentIndex) {
                continue;
            }

            LogicalLine other =
                    rows.get(i);

            if (other.isTableRow()
                    || isSpanningRow(
                    other,
                    splitX
            )) {
                continue;
            }

            boolean otherIsLeft =
                    other.getX() < splitX;

            if (currentIsLeft == otherIsLeft) {
                continue;
            }

            float verticalDistance =
                    Math.abs(
                            other.getY()
                                    - current.getY()
                    );

            if (verticalDistance
                    <= verticalTolerance) {
                return true;
            }
        }

        return false;
    }

    private boolean hasNearbyColumnSupport(
            List<LogicalLine> rows,
            int currentIndex,
            float splitX
    ) {
        LogicalLine current = rows.get(currentIndex);

        if (hasGutterAt(current, splitX)) {
            return true;
        }

        int lookAheadLimit = Math.min(
                rows.size(),
                currentIndex + MIN_MULTI_COLUMN_ROWS
        );

        for (int i = currentIndex + 1;
             i < lookAheadLimit;
             i++) {

            LogicalLine next = rows.get(i);

            if (next.isTableRow()
                    || isSpanningRow(next, splitX)) {
                break;
            }

            if (hasGutterAt(next, splitX)) {
                return true;
            }
        }

        return false;
    }
    private boolean isSpanningRow(LogicalLine row, float splitX) {
        if (row.isTableRow()) {
            return true;
        }

        boolean hasLeft = false;
        boolean hasRight = false;

        for (TextSpan span : row.getSpans()) {
            if (span.getX() < splitX
                    && (span.getX() + span.getWidth()) > splitX) {
                return true;
            }

            if (span.getX() < splitX) {
                hasLeft = true;
            } else {
                hasRight = true;
            }
        }


        return hasLeft && hasRight && !hasGutterAt(row, splitX);
    }

    private boolean hasGutterAt(LogicalLine row, float splitX) {
        TextSpan nearestLeft = null;
        TextSpan nearestRight = null;

        for (TextSpan span : row.getSpans()) {
            float right = span.getX() + span.getWidth();

            if (span.getX() < splitX && right > splitX) {
                return false;
            }

            if (right <= splitX) {
                if (nearestLeft == null
                        || right > nearestLeft.getX() + nearestLeft.getWidth()) {
                    nearestLeft = span;
                }
            } else if (nearestRight == null
                    || span.getX() < nearestRight.getX()) {
                nearestRight = span;
            }
        }

        if (nearestLeft == null || nearestRight == null) {
            return false;
        }

        float gap = nearestRight.getX()
                - (nearestLeft.getX() + nearestLeft.getWidth());

        return gap > segmentGapThreshold(nearestLeft, nearestRight);
    }

    private List<LogicalLine> splitRowIntoCells(LogicalLine line) {
        List<TextSpan> spans =
                new ArrayList<>(line.getSpans());

        spans.sort(
                Comparator.comparing(TextSpan::getX)
        );

        List<LogicalLine> cells =
                new ArrayList<>();

        LogicalLine current =
                new LogicalLine();

        current.markAsTableRow();

        TextSpan previous = null;

        for (TextSpan span : spans) {

            if (previous != null
                    && (span.getX()
                    - (previous.getX() + previous.getWidth()))
                    > segmentGapThreshold(previous, span)) {

                cells.add(current);

                current =
                        new LogicalLine();

                current.markAsTableRow();
            }

            current.add(span);
            previous = span;
        }

        if (!current.getSpans().isEmpty()) {
            cells.add(current);
        }

        return cells;
    }

    private boolean isInsideTableRegion(
            TextSpan span,
            List<TableRegion> tableRegions
    ) {
        float centerX = span.getX() + (span.getWidth() / 2f);
        float centerY = span.getY() + (span.getHeight() / 2f);

        for (TableRegion region : tableRegions) {
            if (centerX >= region.getX() - TABLE_REGION_MARGIN
                    && centerX <= region.getX() + region.getWidth() + TABLE_REGION_MARGIN
                    && centerY >= region.getY() - TABLE_REGION_MARGIN
                    && centerY <= region.getY() + region.getHeight() + TABLE_REGION_MARGIN) {
                return true;
            }
        }

        return false;
    }

    private void flushColumnSection(
            List<LogicalLine> ordered,
            List<LogicalLine> leftColumn,
            List<LogicalLine> rightColumn,
            List<ColumnRegion> columnRegions,
            float splitX
    ) {

        if (!leftColumn.isEmpty()
                && !rightColumn.isEmpty()) {

            float startY =
                    Float.MAX_VALUE;

            float endY =
                    -Float.MAX_VALUE;

            for (LogicalLine line :
                    leftColumn) {

                startY =
                        Math.min(
                                startY,
                                line.getY()
                        );

                endY =
                        Math.max(
                                endY,
                                line.getY()
                                        + line.getHeight()
                        );
            }

            for (LogicalLine line :
                    rightColumn) {

                startY =
                        Math.min(
                                startY,
                                line.getY()
                        );

                endY =
                        Math.max(
                                endY,
                                line.getY()
                                        + line.getHeight()
                        );
            }

            columnRegions.add(
                    new ColumnRegion(
                            startY,
                            endY,
                            2,
                            splitX
                    )
            );
        }

        if (!leftColumn.isEmpty()) {

            leftColumn.sort(
                    Comparator
                            .comparing(
                                    LogicalLine::getY
                            )
                            .thenComparing(
                                    LogicalLine::getX
                            )
            );

            ordered.addAll(
                    leftColumn
            );

            leftColumn.clear();
        }

        if (!rightColumn.isEmpty()) {

            rightColumn.sort(
                    Comparator
                            .comparing(
                                    LogicalLine::getY
                            )
                            .thenComparing(
                                    LogicalLine::getX
                            )
            );

            ordered.addAll(
                    rightColumn
            );

            rightColumn.clear();
        }

    }

    private List<LogicalLine> groupSpansIntoLines(
            List<TextSpan> textSpans,
            float pageHeight
    ) {
        List<TextSpan> sortedSpans = new ArrayList<>(textSpans);

        sortedSpans.sort(
                Comparator
                        .comparing(TextSpan::getY)
                        .thenComparing(TextSpan::getX)
        );

        List<LogicalLine> lines = new ArrayList<>();

        for (TextSpan span : sortedSpans) {
            LogicalLine matchingLine =
                    findMatchingLine(
                            lines,
                            span,
                            pageHeight
                    );

            if (matchingLine == null) {
                LogicalLine newLine = new LogicalLine();
                newLine.add(span);
                lines.add(newLine);
            } else {
                matchingLine.add(span);
            }
        }

        for (LogicalLine line : lines) {
            line.sortLeftToRight();
        }

        lines.sort(
                Comparator
                        .comparing(LogicalLine::getY)
                        .thenComparing(LogicalLine::getX)
        );

        return lines;
    }
    private float segmentGapThreshold(TextSpan previous, TextSpan current) {
        float referenceFontSize = Math.max(
                previous.getFontSize(),
                current.getFontSize()
        );

        return Math.max(
                MIN_SEGMENT_GAP,
                referenceFontSize * SEGMENT_GAP_FONT_FACTOR
        );
    }

    private boolean hasSubstantialContentOnBothSides(
            LogicalLine row,
            float splitX
    ) {
        float leftWidth = 0f;
        float rightWidth = 0f;

        for (TextSpan span : row.getSpans()) {
            float spanRight = span.getX() + span.getWidth();

            if (spanRight <= splitX) {
                leftWidth += span.getWidth();
            } else if (span.getX() >= splitX) {
                rightWidth += span.getWidth();
            }
        }

        if (leftWidth <= 0f || rightWidth <= 0f) {
            return false;
        }

        float smaller = Math.min(leftWidth, rightWidth);
        float larger = Math.max(leftWidth, rightWidth);

        return smaller / larger >= 0.35f;
    }

    private HorizontalGap findLargestHorizontalGap(LogicalLine line) {
        List<TextSpan> spans = new ArrayList<>(line.getSpans());

        spans.sort(Comparator.comparing(TextSpan::getX));

        if (spans.size() < 2) {
            return null;
        }

        HorizontalGap largest = null;

        for (int i = 1; i < spans.size(); i++) {
            TextSpan previous = spans.get(i - 1);
            TextSpan current = spans.get(i);

            float previousRight =
                    previous.getX() + previous.getWidth();

            float gap =
                    current.getX() - previousRight;

            if (gap > segmentGapThreshold(previous, current)
                    && (largest == null || gap > largest.width())) {
                largest = new HorizontalGap(previousRight, current.getX());
            }
        }

        return largest;
    }

    private Float detectColumnSplitFromVerticalBands(
            List<LogicalLine> rows
    ) {
        if (rows.size()
                < MIN_MULTI_COLUMN_ROWS * 2) {
            return null;
        }

        float contentLeft =
                rows.stream()
                        .map(LogicalLine::getX)
                        .min(Float::compare)
                        .orElse(0f);

        float contentRight =
                rows.stream()
                        .map(row ->
                                row.getX()
                                        + row.getWidth()
                        )
                        .max(Float::compare)
                        .orElse(contentLeft);

        float contentWidth =
                contentRight - contentLeft;

        if (contentWidth <= 0f) {
            return null;
        }

        List<Float> starts =
                rows.stream()
                        .map(LogicalLine::getX)
                        .sorted()
                        .toList();

        float bestGap =
                0f;

        Float leftAnchor =
                null;

        Float rightAnchor =
                null;

        for (int i = 1;
             i < starts.size();
             i++) {

            float previous =
                    starts.get(i - 1);

            float current =
                    starts.get(i);

            float gap =
                    current - previous;

            if (gap > bestGap) {
                bestGap = gap;
                leftAnchor = previous;
                rightAnchor = current;
            }
        }

        if (leftAnchor == null) {
            return null;
        }

        if (bestGap
                < contentWidth
                * MIN_COLUMN_START_SEPARATION_RATIO) {
            return null;
        }

        int leftSupport =
                0;

        int rightSupport =
                0;

        for (LogicalLine row : rows) {

            if (Math.abs(
                    row.getX()
                            - contentLeft
            ) <= COLUMN_START_CLUSTER_TOLERANCE) {

                leftSupport++;
            }

            if (Math.abs(
                    row.getX()
                            - rightAnchor
            ) <= COLUMN_START_CLUSTER_TOLERANCE) {

                rightSupport++;
            }
        }

        if (leftSupport < MIN_MULTI_COLUMN_ROWS
                || rightSupport
                < MIN_MULTI_COLUMN_ROWS) {
            return null;
        }

        float leftContentRight =
                -Float.MAX_VALUE;

        float rightContentLeft =
                Float.MAX_VALUE;

        for (LogicalLine row : rows) {

            float rowLeft =
                    row.getX();

            float rowRight =
                    row.getX()
                            + row.getWidth();

            /*
             * Only use rows that clearly belong to one side.
             * Full-width headings or spanning rows must not
             * destroy the detected gutter.
             */
            if (rowLeft
                    < rightAnchor
                    - COLUMN_START_CLUSTER_TOLERANCE
                    && rowRight
                    < rightAnchor) {

                leftContentRight =
                        Math.max(
                                leftContentRight,
                                rowRight
                        );
            }

            if (Math.abs(
                    rowLeft - rightAnchor
            ) <= COLUMN_START_CLUSTER_TOLERANCE) {

                rightContentLeft =
                        Math.min(
                                rightContentLeft,
                                rowLeft
                        );
            }
        }

        if (leftContentRight
                == -Float.MAX_VALUE
                || rightContentLeft
                == Float.MAX_VALUE) {

            return null;
        }

        if (rightContentLeft
                <= leftContentRight) {
            return null;
        }

        return (
                leftContentRight
                        + rightContentLeft
        ) / 2f;
    }

    private Float detectRepeatedColumnSplit(
            List<LogicalLine> rows
    ) {
        if (rows.isEmpty()) {
            return null;
        }

        float contentLeft = Float.MAX_VALUE;
        float contentRight = -Float.MAX_VALUE;

        for (LogicalLine row : rows) {
            contentLeft = Math.min(contentLeft, row.getX());
            contentRight = Math.max(contentRight, row.getX() + row.getWidth());
        }

        float contentWidth = contentRight - contentLeft;

        if (contentWidth <= 0f) {
            return null;
        }

        int candidateRows = 0;
        List<Float> gutterPositions = new ArrayList<>();

        for (LogicalLine row : rows) {
            HorizontalGap gap = findLargestHorizontalGap(row);

            if (gap == null) {
                continue;
            }

            candidateRows++;

            float relativePosition =
                    (gap.center() - contentLeft) / contentWidth;

            boolean interior =
                    relativePosition >= MIN_COLUMN_SPLIT_POSITION
                            && relativePosition <= MAX_COLUMN_SPLIT_POSITION;



            if (interior
                    && hasSubstantialContentOnBothSides(
                    row,
                    gap.center()
            )) {
                gutterPositions.add(gap.center());
            }
        }

        if (candidateRows < MIN_MULTI_COLUMN_ROWS) {
            return null;
        }

        float contentCenter = contentLeft + (contentWidth / 2f);

        Float bestSplit = null;
        int bestSupport = 0;

        for (Float candidate : gutterPositions) {
            int support = 0;
            float total = 0f;

            for (Float other : gutterPositions) {
                if (Math.abs(candidate - other) <= COLUMN_SPLIT_TOLERANCE) {
                    support++;
                    total += other;
                }
            }

            float split = total / support;


            if (support > bestSupport
                    || (support == bestSupport
                    && Math.abs(split - contentCenter)
                    < Math.abs(bestSplit - contentCenter))) {
                bestSupport = support;
                bestSplit = split;
            }
        }

        if (bestSupport < MIN_MULTI_COLUMN_ROWS
                || bestSupport < candidateRows * MIN_COLUMN_AGREEMENT_RATIO) {
            return null;
        }

        if (looksLikeLabelValueLayout(rows, bestSplit)) {
            return null;
        }

        return bestSplit;
    }
    private float determineBodyFontSize(List<TextSpan> spans) {
        if (spans.isEmpty()) {
            return 0f;
        }

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

    private boolean looksLikeLabelValueLayout(
            List<LogicalLine> rows,
            float splitX
    ) {
        int eligibleRows = 0;
        int labelValueRows = 0;

        for (LogicalLine row : rows) {
            StringBuilder leftText = new StringBuilder();
            boolean hasRight = false;

            for (TextSpan span : row.getSpans()) {
                float spanRight =
                        span.getX() + span.getWidth();

                if (spanRight <= splitX) {
                    if (!leftText.isEmpty()) {
                        leftText.append(' ');
                    }

                    leftText.append(span.getText().trim());
                } else if (span.getX() >= splitX) {
                    hasRight = true;
                }
            }

            if (leftText.isEmpty() || !hasRight) {
                continue;
            }

            eligibleRows++;

            if (leftText.toString().trim().endsWith(":")) {
                labelValueRows++;
            }
        }

        return eligibleRows >= MIN_MULTI_COLUMN_ROWS
                && labelValueRows >= Math.ceil(eligibleRows * 0.5);
    }

    private LogicalLine findMatchingLine(
            List<LogicalLine> lines,
            TextSpan span,
            float pageHeight
    ) {
        LogicalLine closest = null;
        float closestDistance = Float.MAX_VALUE;

        boolean spanNearBottom =
                pageHeight > 0f
                        && span.getY()
                        >= pageHeight * PAGE_EDGE_START_RATIO;

        for (LogicalLine line : lines) {

            float tolerance = Math.max(
                    MIN_LINE_TOLERANCE,
                    Math.max(
                            line.getAverageHeight(),
                            span.getHeight()
                    ) * LINE_TOLERANCE_FACTOR
            );

            float distance =
                    Math.abs(
                            line.getY()
                                    - span.getY()
                    );

            boolean lineNearBottom =
                    pageHeight > 0f
                            && line.getY()
                            >= pageHeight * PAGE_EDGE_START_RATIO;

            float effectiveTolerance =
                    (spanNearBottom || lineNearBottom)
                            ? MIN_LINE_TOLERANCE
                            : tolerance;

            if (distance <= effectiveTolerance
                    && distance < closestDistance) {

                closest = line;
                closestDistance = distance;
            }
        }

        return closest;
    }

    private ListType detectListType(LogicalLine line) {
        String text = line.getText();

        if (text.isBlank()) {
            return null;
        }

        if (UNORDERED_LIST_PATTERN.matcher(text).matches()) {
            return ListType.UNORDERED;
        }

        if (ORDERED_LIST_PATTERN.matcher(text).matches()) {
            return ListType.ORDERED;
        }

        return null;
    }

    private boolean isHeading(
            LogicalLine line,
            float bodyFontSize
    ) {
        String text = line.getText();

        if (text.isBlank()) {
            return false;
        }

        if (text.length() > HEADING_MAX_LENGTH) {
            return false;
        }

        if (bodyFontSize <= 0f) {
            return false;
        }

        float fontRatio =
                line.getMinFontSize()
                        / bodyFontSize;

        boolean clearlyLarger =
                fontRatio >= HEADING_FONT_RATIO;

        boolean slightlyLarger =
                fontRatio >= 1.10f;

        boolean mostlyBold =
                line.isMostlyBold();


        if (clearlyLarger) {
            return true;
        }

        return mostlyBold && slightlyLarger;
    }

    private record HorizontalGap(float left, float right) {

        float width() {
            return right - left;
        }

        float center() {
            return left + (width() / 2f);
        }
    }

    private record ReadingOrderResult(
            List<LogicalLine> lines,
            List<ColumnRegion> columnRegions
    ) {
    }

    private static class LogicalLine {

        private final List<TextSpan> spans = new ArrayList<>();
        private boolean tableRow;

        void add(TextSpan span) {
            spans.add(span);
        }

        void markAsTableRow() {
            tableRow = true;
        }

        boolean isTableRow() {
            return tableRow;
        }

        float getMinFontSize() {
            return spans.stream()
                    .map(TextSpan::getFontSize)
                    .filter(size -> size > 0f)
                    .min(Float::compare)
                    .orElse(0f);
        }

        boolean isMostlyBold() {
            if (spans.isEmpty()) {
                return false;
            }

            int meaningfulSpans = 0;
            int boldSpans = 0;

            for (TextSpan span : spans) {

                if (span.getText() == null
                        || span.getText().isBlank()) {
                    continue;
                }

                meaningfulSpans++;

                if (span.isBold()) {
                    boldSpans++;
                }
            }

            if (meaningfulSpans == 0) {
                return false;
            }

            return boldSpans >= Math.ceil(
                    meaningfulSpans * 0.6
            );
        }

        void sortLeftToRight() {
            spans.sort(Comparator.comparing(TextSpan::getX));
        }

        List<TextSpan> getSpans() {
            return spans;
        }

        float getX() {
            return spans.stream()
                    .map(TextSpan::getX)
                    .min(Float::compare)
                    .orElse(0f);
        }

        float getY() {
            return spans.stream()
                    .map(TextSpan::getY)
                    .min(Float::compare)
                    .orElse(0f);
        }

        float getWidth() {
            if (spans.isEmpty()) {
                return 0f;
            }

            float minX = spans.stream()
                    .map(TextSpan::getX)
                    .min(Float::compare)
                    .orElse(0f);

            float maxX = spans.stream()
                    .map(span -> span.getX() + span.getWidth())
                    .max(Float::compare)
                    .orElse(minX);

            return Math.max(0f, maxX - minX);
        }

        float getHeight() {
            if (spans.isEmpty()) {
                return 0f;
            }

            float minY = spans.stream()
                    .map(TextSpan::getY)
                    .min(Float::compare)
                    .orElse(0f);

            float maxY = spans.stream()
                    .map(span -> span.getY() + span.getHeight())
                    .max(Float::compare)
                    .orElse(minY);

            return Math.max(0f, maxY - minY);
        }

        float getAverageHeight() {
            if (spans.isEmpty()) {
                return 0f;
            }

            float totalHeight = 0f;

            for (TextSpan span : spans) {
                totalHeight += span.getHeight();
            }

            return totalHeight / spans.size();
        }

        String getText() {

            StringBuilder builder =
                    new StringBuilder();

            boolean previousEndedWithWhitespace =
                    false;

            for (TextSpan span : spans) {

                String text =
                        span.getText();

                if (text == null
                        || text.isBlank()) {
                    continue;
                }

                boolean startsWithWhitespace =
                        Character.isWhitespace(
                                text.charAt(0)
                        );

                boolean endsWithWhitespace =
                        Character.isWhitespace(
                                text.charAt(
                                        text.length() - 1
                                )
                        );

                String cleaned =
                        text.strip();

                if (!builder.isEmpty()
                        && (span.isWordSeparatorBefore()
                        || previousEndedWithWhitespace
                        || startsWithWhitespace)
                        && !Character.isWhitespace(
                        builder.charAt(
                                builder.length() - 1
                        )
                )) {

                    builder.append(' ');
                }

                builder.append(cleaned);

                previousEndedWithWhitespace =
                        endsWithWhitespace;
            }

            return builder.toString();
        }
    }
    private boolean crossesBottomPageEdgeBoundary(
            LogicalLine previous,
            LogicalLine current,
            float pageHeight
    ) {
        if (pageHeight <= 0f) {
            return false;
        }

        float bottomEdgeStart =
                pageHeight * 0.94f;

        boolean previousInBottomEdge =
                previous.getY() >= bottomEdgeStart;

        boolean currentInBottomEdge =
                current.getY() >= bottomEdgeStart;

        if (!previousInBottomEdge
                && currentInBottomEdge) {
            return true;
        }

        return previousInBottomEdge
                && currentInBottomEdge
                && Math.abs(
                current.getY()
                        - previous.getY()
        ) > MIN_LINE_TOLERANCE;
    }

    private boolean startsFigureOrTableIndexEntry(
            LogicalLine line
    ) {
        if (line == null) {
            return false;
        } else {
            line.getText();
        }

        String text =
                line.getText().strip();

        return FIGURE_INDEX_ENTRY_PATTERN
                .matcher(text)
                .matches()
                || TABLE_INDEX_ENTRY_PATTERN
                .matcher(text)
                .matches();
    }

}