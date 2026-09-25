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

@Service
public class StructureRecoveryServiceImpl implements StructureRecoveryService {

    private static final float MIN_LINE_TOLERANCE = 2.0f;
    private static final float LINE_TOLERANCE_FACTOR = 0.5f;
    private static final float COLUMN_GAP_THRESHOLD = 120f;
    private static final float SPANNING_WIDTH_RATIO = 0.7f;
    private static final float PARAGRAPH_GAP_FACTOR = 1.2f;
    private static final float INDENT_TOLERANCE = 20f;

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

        List<StructuredBlock> paragraphs = groupLinesIntoParagraphs(
                pageExtraction.getPageIndex(),
                lines
        );

        pageExtraction.getStructuredBlocks().addAll(paragraphs);
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

    private List<StructuredBlock> groupLinesIntoParagraphs(
            int pageIndex,
            List<LogicalLine> lines
    ) {
        List<StructuredBlock> blocks = new ArrayList<>();

        if (lines.isEmpty()) {
            return blocks;
        }

        List<LogicalLine> currentParagraph = new ArrayList<>();
        currentParagraph.add(lines.getFirst());

        for (int i = 1; i < lines.size(); i++) {
            LogicalLine previous = lines.get(i - 1);
            LogicalLine current = lines.get(i);

            if (belongsToSameParagraph(previous, current)) {
                currentParagraph.add(current);
            } else {
                blocks.add(
                        toParagraphBlock(pageIndex, currentParagraph)
                );

                currentParagraph = new ArrayList<>();
                currentParagraph.add(current);
            }
        }

        if (!currentParagraph.isEmpty()) {
            blocks.add(
                    toParagraphBlock(pageIndex, currentParagraph)
            );
        }

        return blocks;
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