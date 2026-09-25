package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.BlockType;
import com.amalitech.backend.service.PageExtraction;
import com.amalitech.backend.service.StructureRecoveryService;
import com.amalitech.backend.service.StructuredBlock;
import com.amalitech.backend.service.TableRegion;
import com.amalitech.backend.service.TextSpan;
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
    // Conventional first-line indents go up to ~0.5in (36pt); allow some slack.
    private static final float MAX_FIRST_LINE_INDENT = 48f;
    private static final float HEADING_FONT_RATIO = 1.25f;
    private static final int HEADING_MAX_LENGTH = 120;
    // A single capital letter followed by a period ("A. Smith") is far more
    // often an initial than a list marker, so uppercase letters need ")"
    // and uppercase roman numerals need at least two characters.
    private static final Pattern LIST_PATTERN = Pattern.compile(
            "^\\s*(?:[•◦▪‣⁃∙*-]|(?:\\d+|[a-z]|[ivx]+|[IVX]{2,})[.)]|[A-Z]\\))\\s+.+"
    );
    private static final float MIN_SEGMENT_GAP = 8f;
    private static final float SEGMENT_GAP_FONT_FACTOR = 0.9f;
    private static final int MIN_MULTI_COLUMN_ROWS = 3;
    // Share of rows with a large horizontal gap that must agree on the split.
    private static final float MIN_COLUMN_AGREEMENT_RATIO = 0.5f;
    // A column boundary must lie in the interior of the text area,
    // expressed as a fraction of the content width.
    private static final float MIN_COLUMN_SPLIT_POSITION = 0.3f;
    private static final float MAX_COLUMN_SPLIT_POSITION = 0.7f;
    // Column gutters are narrow; wide gaps come from tab stops, leaders,
    // label/value pairs or right-aligned numbers.
    private static final float TABLE_REGION_MARGIN = 1f;


    @Override
    public void recoverStructure(PageExtraction pageExtraction) {
        if (pageExtraction == null) {
            throw new IllegalArgumentException("Page extraction cannot be null.");
        }

        pageExtraction.getStructuredBlocks().clear();

        if (pageExtraction.getTextSpans().isEmpty()) {
            return;
        }

        List<LogicalLine> lines = buildReadingOrder(
                pageExtraction.getTextSpans(),
                pageExtraction.getCandidateTableRegions()
        );

        float bodyFontSize = determineBodyFontSize(
                pageExtraction.getTextSpans()
        );

        List<StructuredBlock> blocks = buildStructuredBlocks(
                pageExtraction.getPageIndex(),
                lines,
                bodyFontSize
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
            spans.addAll(line.getSpans());

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
                text.toString(),
                minX,
                minY,
                maxX - minX,
                maxY - minY,
                spans
        );
    }

    private List<StructuredBlock> buildStructuredBlocks(
            int pageIndex,
            List<LogicalLine> lines,
            float bodyFontSize
    ) {
        List<StructuredBlock> blocks = new ArrayList<>();
        List<LogicalLine> paragraphLines = new ArrayList<>();

        for (LogicalLine line : lines) {

            if (line.isTableRow()) {
                flushParagraph(
                        blocks,
                        paragraphLines,
                        pageIndex
                );

                blocks.add(
                        toBlock(
                                pageIndex,
                                line,
                                BlockType.PARAGRAPH
                        )
                );

                continue;
            }

            if (isHeading(line, bodyFontSize)) {
                flushParagraph(
                        blocks,
                        paragraphLines,
                        pageIndex
                );

                blocks.add(
                        toBlock(
                                pageIndex,
                                line,
                                BlockType.HEADING
                        )
                );

                continue;
            }

            if (isListItem(line)) {
                flushParagraph(
                        blocks,
                        paragraphLines,
                        pageIndex
                );

                blocks.add(
                        toBlock(
                                pageIndex,
                                line,
                                BlockType.LIST_ITEM
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
                    paragraphLines.size() == 1
            )) {
                paragraphLines.add(line);
            } else {
                flushParagraph(
                        blocks,
                        paragraphLines,
                        pageIndex
                );

                paragraphLines.add(line);
            }
        }

        flushParagraph(
                blocks,
                paragraphLines,
                pageIndex
        );

        return blocks;
    }
    private void flushParagraph(
            List<StructuredBlock> blocks,
            List<LogicalLine> paragraphLines,
            int pageIndex
    ) {
        if (paragraphLines.isEmpty()) {
            return;
        }

        blocks.add(
                toParagraphBlock(
                        pageIndex,
                        new ArrayList<>(paragraphLines)
                )
        );

        paragraphLines.clear();
    }
    private StructuredBlock toBlock(
            int pageIndex,
            LogicalLine line,
            BlockType type
    ) {
        return new StructuredBlock(
                pageIndex,
                type,
                line.getText(),
                line.getX(),
                line.getY(),
                line.getWidth(),
                line.getHeight(),
                new ArrayList<>(line.getSpans())
        );
    }

    private boolean belongsToSameParagraph(
            LogicalLine previous,
            LogicalLine current,
            boolean previousIsFirstLine
    ) {
        // A jump upward means reading order has moved into another
        // column or region, so these lines cannot share a paragraph.
        if (current.getY() < previous.getY()) {
            return false;
        }

        // Table text never merges with flowing text around it.
        if (current.isTableRow() != previous.isTableRow()) {
            return false;
        }

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

        boolean closeVertically =
                verticalGap <= allowedGap;

        float indentDelta = current.getX() - previous.getX();

        // The first line of a paragraph may be indented further than the
        // lines that follow it.
        boolean firstLineIndent = previousIsFirstLine
                && indentDelta < 0f
                && -indentDelta <= MAX_FIRST_LINE_INDENT;

        boolean similarlyIndented =
                Math.abs(indentDelta) <= INDENT_TOLERANCE
                        || firstLineIndent;

        return closeVertically && similarlyIndented;
    }

    private List<LogicalLine> buildReadingOrder(
            List<TextSpan> textSpans,
            List<TableRegion> tableRegions
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

        // Column detection only looks at flowing text; table cells would
        // otherwise look like repeated column boundaries.
        List<LogicalLine> physicalRows =
                groupSpansIntoLines(flowSpans);

        Float splitX =
                detectRepeatedColumnSplit(physicalRows);

        List<LogicalLine> tableRows =
                groupSpansIntoLines(tableSpans);

        for (LogicalLine tableRow : tableRows) {
            tableRow.markAsTableRow();
        }

        physicalRows.addAll(tableRows);
        physicalRows.sort(
                Comparator
                        .comparing(LogicalLine::getY)
                        .thenComparing(LogicalLine::getX)
        );

        // No repeated column boundary found.
        if (splitX == null) {
            return physicalRows;
        }

        List<LogicalLine> ordered = new ArrayList<>();
        List<LogicalLine> leftColumn = new ArrayList<>();
        List<LogicalLine> rightColumn = new ArrayList<>();

        int firstColumnRow = -1;

        // Find where the repeated two-column region begins.
        for (int i = 0; i < physicalRows.size(); i++) {
            LogicalLine row = physicalRows.get(i);

            if (!row.isTableRow() && hasGutterAt(row, splitX)) {
                firstColumnRow = i;
                break;
            }
        }

        if (firstColumnRow == -1) {
            return physicalRows;
        }

        // Keep title/author/date/etc. above the columns.
        for (int i = 0; i < firstColumnRow; i++) {
            ordered.add(physicalRows.get(i));
        }

        // Split each remaining row using the detected
        // document-level column boundary, keeping spanning rows intact.
        for (int i = firstColumnRow;
             i < physicalRows.size();
             i++) {

            LogicalLine row = physicalRows.get(i);

            if (isSpanningRow(row, splitX)) {
                flushColumnSection(ordered, leftColumn, rightColumn);
                ordered.add(row);
                continue;
            }

            if (!hasNearbyColumnSupport(
                    physicalRows,
                    i,
                    splitX
            )) {
                flushColumnSection(
                        ordered,
                        leftColumn,
                        rightColumn
                );

                for (int j = i; j < physicalRows.size(); j++) {
                    ordered.add(physicalRows.get(j));
                }

                return ordered;
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

        flushColumnSection(ordered, leftColumn, rightColumn);

        return ordered;
    }


    private boolean hasNearbyColumnSupport(
            List<LogicalLine> rows,
            int currentIndex,
            float splitX
    ) {
        LogicalLine current = rows.get(currentIndex);

        // If the current row itself clearly has the detected gutter,
        // it is part of the column section.
        if (hasGutterAt(current, splitX)) {
            return true;
        }

        // A one-sided row can still belong to a column section if
        // another nearby row continues the same column pattern.
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
        // Table rows act as full-width barriers so their cells are never
        // redistributed into left/right columns.
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

        // Text on both sides of the split without a real gutter between
        // them belongs to one line running across the boundary.
        return hasLeft && hasRight && !hasGutterAt(row, splitX);
    }

    /**
     * Returns true when the row has text on both sides of {@code splitX}
     * separated by a gap wide enough to be a column gutter.
     */
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
            List<LogicalLine> rightColumn
    ) {
        if (!leftColumn.isEmpty()) {
            leftColumn.sort(
                    Comparator
                            .comparing(LogicalLine::getY)
                            .thenComparing(LogicalLine::getX)
            );
            ordered.addAll(leftColumn);
            leftColumn.clear();
        }

        if (!rightColumn.isEmpty()) {
            rightColumn.sort(
                    Comparator
                            .comparing(LogicalLine::getY)
                            .thenComparing(LogicalLine::getX)
            );
            ordered.addAll(rightColumn);
            rightColumn.clear();
        }
    }

    private List<LogicalLine> groupSpansIntoLines(List<TextSpan> textSpans) {
        List<TextSpan> sortedSpans = new ArrayList<>(textSpans);

        sortedSpans.sort(
                Comparator
                        .comparing(TextSpan::getY)
                        .thenComparing(TextSpan::getX)
        );

        List<LogicalLine> lines = new ArrayList<>();

        for (TextSpan span : sortedSpans) {
            LogicalLine matchingLine = findMatchingLine(lines, span);

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

    /**
     * Looks for a column boundary shared by many rows. A split is only
     * accepted when enough rows agree on it, when it lies in the interior
     * of the text area and when the agreeing gaps are narrow gutters rather
     * than tab stops, leaders or right-aligned values.
     */
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

            // Prefer the best-supported split, then the most central one.
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

        if (bestSplit != null
                && looksLikeLabelValueLayout(rows, bestSplit)) {
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
            TextSpan span
    ) {
        LogicalLine closest = null;
        float closestDistance = Float.MAX_VALUE;

        // Pick the nearest line within tolerance rather than the first one,
        // so a span is not attached to a neighbouring line that merely
        // happens to be close enough.
        for (LogicalLine line : lines) {
            float tolerance = Math.max(
                    MIN_LINE_TOLERANCE,
                    Math.max(line.getAverageHeight(), span.getHeight())
                            * LINE_TOLERANCE_FACTOR
            );

            float distance = Math.abs(line.getY() - span.getY());

            if (distance <= tolerance && distance < closestDistance) {
                closest = line;
                closestDistance = distance;
            }
        }

        return closest;
    }

    private boolean isListItem(LogicalLine line) {
        String text = line.getText();

        if (text == null || text.isBlank()) {
            return false;
        }

        return LIST_PATTERN.matcher(text).matches();
    }

    private boolean isHeading(
            LogicalLine line,
            float bodyFontSize
    ) {
        String text = line.getText();

        if (text == null || text.isBlank()) {
            return false;
        }

        if (text.length() > HEADING_MAX_LENGTH) {
            return false;
        }

        if (bodyFontSize <= 0f) {
            return false;
        }

        // Every span must be large: a single oversized word (drop cap,
        // inline emphasis) must not turn a paragraph line into a heading
        // and split the paragraph mid-flow.
        return line.getMinFontSize()
                >= bodyFontSize * HEADING_FONT_RATIO;
    }

    private record HorizontalGap(float left, float right) {

        float width() {
            return right - left;
        }

        float center() {
            return left + (width() / 2f);
        }
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
            StringBuilder builder = new StringBuilder();

            for (TextSpan span : spans) {
                String text = span.getText();

                if (text == null || text.isBlank()) {
                    continue;
                }

                if (!builder.isEmpty()) {
                    builder.append(' ');
                }

                builder.append(text.trim());
            }

            return builder.toString();
        }
    }
}