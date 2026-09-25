package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.BlockType;
import com.amalitech.backend.service.PageExtraction;
import com.amalitech.backend.service.StructureRecoveryService;
import com.amalitech.backend.service.StructuredBlock;
import com.amalitech.backend.service.TextSpan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class StructureRecoveryServiceImpl implements StructureRecoveryService {

    private static final float MIN_LINE_TOLERANCE = 2.0f;
    private static final float LINE_TOLERANCE_FACTOR = 0.5f;
    private static final float PARAGRAPH_GAP_FACTOR = 1.2f;
    private static final float INDENT_TOLERANCE = 20f;
    private static final float HEADING_FONT_RATIO = 1.25f;
    private static final int HEADING_MAX_LENGTH = 120;
    private static final Pattern LIST_PATTERN = Pattern.compile(
            "^\\s*(?:[•◦▪‣⁃∙*-]|(?:\\d+|[A-Za-z]|[ivxIVX]+)[.)])\\s+.+"
    );
    private static final float MIN_SEGMENT_GAP = 8f;
    private static final float SEGMENT_GAP_FONT_FACTOR = 0.9f;
    private static final int MIN_MULTI_COLUMN_ROWS = 2;


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
                pageExtraction.getTextSpans()
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

            if (belongsToSameParagraph(previous, line)) {
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
            LogicalLine current
    ) {
        // A jump upward means reading order has moved into another
        // column or region, so these lines cannot share a paragraph.
        if (current.getY() < previous.getY()) {
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

        boolean similarlyIndented =
                Math.abs(current.getX() - previous.getX())
                        <= INDENT_TOLERANCE;

        return closeVertically && similarlyIndented;
    }

    private List<LogicalLine> buildReadingOrder(
            List<TextSpan> textSpans
    ) {
        List<LogicalLine> physicalRows =
                groupSpansIntoLines(textSpans);

        Float splitX =
                detectRepeatedColumnSplit(physicalRows);

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
            Float rowSplit =
                    findLargestHorizontalGapPosition(
                            physicalRows.get(i)
                    );

            if (rowSplit != null
                    && Math.abs(rowSplit - splitX) <= 20f) {
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
        // document-level column boundary.
        for (int i = firstColumnRow;
             i < physicalRows.size();
             i++) {

            LogicalLine row = physicalRows.get(i);

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

        leftColumn.sort(
                Comparator
                        .comparing(LogicalLine::getY)
                        .thenComparing(LogicalLine::getX)
        );

        rightColumn.sort(
                Comparator
                        .comparing(LogicalLine::getY)
                        .thenComparing(LogicalLine::getX)
        );

        ordered.addAll(leftColumn);
        ordered.addAll(rightColumn);

        return ordered;
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
    private Float findLargestHorizontalGapPosition(LogicalLine line) {
        List<TextSpan> spans = new ArrayList<>(line.getSpans());

        spans.sort(Comparator.comparing(TextSpan::getX));

        if (spans.size() < 2) {
            return null;
        }

        float largestGap = 0f;
        Float splitPosition = null;

        for (int i = 1; i < spans.size(); i++) {
            TextSpan previous = spans.get(i - 1);
            TextSpan current = spans.get(i);

            float previousRight =
                    previous.getX() + previous.getWidth();

            float gap =
                    current.getX() - previousRight;

            float referenceFontSize = Math.max(
                    previous.getFontSize(),
                    current.getFontSize()
            );

            float threshold = Math.max(
                    MIN_SEGMENT_GAP,
                    referenceFontSize * SEGMENT_GAP_FONT_FACTOR
            );

            if (gap > threshold && gap > largestGap) {
                largestGap = gap;

                splitPosition =
                        previousRight + (gap / 2f);
            }
        }

        return splitPosition;
    }
    private Float detectRepeatedColumnSplit(
            List<LogicalLine> rows
    ) {
        List<Float> candidates = new ArrayList<>();

        for (LogicalLine row : rows) {
            Float candidate =
                    findLargestHorizontalGapPosition(row);

            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        if (candidates.size() < MIN_MULTI_COLUMN_ROWS) {
            return null;
        }

        final float splitTolerance = 20f;

        for (Float candidate : candidates) {
            int matches = 0;

            for (Float other : candidates) {
                if (Math.abs(candidate - other)
                        <= splitTolerance) {
                    matches++;
                }
            }

            if (matches >= MIN_MULTI_COLUMN_ROWS) {
                return candidate;
            }
        }

        return null;
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
    private LogicalLine findMatchingLine(
            List<LogicalLine> lines,
            TextSpan span
    ) {
        for (LogicalLine line : lines) {
            float tolerance = Math.max(
                    MIN_LINE_TOLERANCE,
                    Math.max(line.getAverageHeight(), span.getHeight())
                            * LINE_TOLERANCE_FACTOR
            );


            if (Math.abs(line.getY() - span.getY()) <= tolerance) {
                return line;
            }
        }

        return null;
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

        return line.getAverageFontSize()
                >= bodyFontSize * HEADING_FONT_RATIO;
    }

    private static class LogicalLine {

        private final List<TextSpan> spans = new ArrayList<>();

        void add(TextSpan span) {
            spans.add(span);
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

        float getAverageFontSize() {
            if (spans.isEmpty()) {
                return 0f;
            }

            float total = 0f;
            int count = 0;

            for (TextSpan span : spans) {
                if (span.getFontSize() > 0f) {
                    total += span.getFontSize();
                    count++;
                }
            }

            return count == 0 ? 0f : total / count;
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