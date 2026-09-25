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
    private static final float COLUMN_GAP_THRESHOLD = 120f;
    private static final float SPANNING_WIDTH_RATIO = 0.7f;
    private static final float PARAGRAPH_GAP_FACTOR = 1.2f;
    private static final float INDENT_TOLERANCE = 20f;
    private static final float HEADING_FONT_RATIO = 1.25f;
    private static final int HEADING_MAX_LENGTH = 120;
    private static final Pattern LIST_PATTERN = Pattern.compile(
            "^\\s*(?:[•◦▪‣⁃∙*-]|(?:\\d+|[A-Za-z]|[ivxIVX]+)[.)])\\s+.+"
    );

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
        float previousBottom = previous.getY() + previous.getHeight();

        float verticalGap = current.getY() - previousBottom;

        float referenceHeight = Math.max(
                previous.getAverageHeight(),
                current.getAverageHeight()
        );

        float allowedGap = referenceHeight * PARAGRAPH_GAP_FACTOR;

        boolean closeVertically = verticalGap <= allowedGap;

        boolean similarlyIndented =
                Math.abs(current.getX() - previous.getX())
                        <= INDENT_TOLERANCE;

        return closeVertically && similarlyIndented;
    }

    private List<LogicalLine> buildReadingOrder(List<TextSpan> textSpans) {
        List<TextSpan> spanningSpans = findSpanningSpans(textSpans);

        List<TextSpan> remainingSpans = new ArrayList<>(textSpans);
        remainingSpans.removeAll(spanningSpans);

        List<LogicalLine> orderedLines = new ArrayList<>();

        if (!spanningSpans.isEmpty()) {
            orderedLines.addAll(groupSpansIntoLines(spanningSpans));
        }

        List<List<TextSpan>> columns = detectColumns(remainingSpans);

        for (List<TextSpan> column : columns) {
            orderedLines.addAll(groupSpansIntoLines(column));
        }

        return orderedLines;
    }

    private List<TextSpan> findSpanningSpans(List<TextSpan> textSpans) {
        if (textSpans.isEmpty()) {
            return List.of();
        }

        float minX = textSpans.stream()
                .map(TextSpan::getX)
                .min(Float::compare)
                .orElse(0f);

        float maxRight = textSpans.stream()
                .map(span -> span.getX() + span.getWidth())
                .max(Float::compare)
                .orElse(0f);

        float contentWidth = maxRight - minX;

        if (contentWidth <= 0f) {
            return List.of();
        }

        List<TextSpan> spanning = new ArrayList<>();

        for (TextSpan span : textSpans) {
            float spanRatio = span.getWidth() / contentWidth;

            if (spanRatio >= SPANNING_WIDTH_RATIO) {
                spanning.add(span);
            }
        }

        return spanning;
    }

    private List<List<TextSpan>> detectColumns(List<TextSpan> textSpans) {
        List<TextSpan> sortedByX = new ArrayList<>(textSpans);

        sortedByX.sort(Comparator.comparing(TextSpan::getX));

        List<List<TextSpan>> columns = new ArrayList<>();

        List<TextSpan> currentColumn = new ArrayList<>();

        Float previousX = null;

        for (TextSpan span : sortedByX) {
            if (previousX != null
                    && span.getX() - previousX > COLUMN_GAP_THRESHOLD
                    && !currentColumn.isEmpty()) {

                columns.add(currentColumn);
                currentColumn = new ArrayList<>();
            }

            currentColumn.add(span);
            previousX = span.getX();
        }

        if (!currentColumn.isEmpty()) {
            columns.add(currentColumn);
        }

        return columns;
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