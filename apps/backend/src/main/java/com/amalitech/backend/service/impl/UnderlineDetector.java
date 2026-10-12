package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.TableRegion;
import com.amalitech.backend.service.TextSpan;

import java.util.List;

/**
 * Marks text spans as underlined when a drawn horizontal stroke sits under
 * the span. PDFBox does not expose underline as a font flag, so the stroke
 * is the source of truth.
 */
final class UnderlineDetector {

    private static final float MIN_OVERLAP_RATIO = 0.60f;
    private static final float MAX_LINE_LENGTH_RATIO = 1.5f;
    private static final float MAX_LINE_LENGTH_SLACK = 4f;
    private static final float ABOVE_BASELINE_TOLERANCE = 1f;
    private static final float MIN_BELOW_SPAN = 2.5f;
    private static final float BELOW_SPAN_HEIGHT_RATIO = 0.4f;

    private UnderlineDetector() {
    }

    /**
     * Sets {@code underline} on spans that have a matching horizontal stroke.
     * Spans that already sit inside a candidate table region are left alone,
     * because those strokes are table rules.
     *
     * @param spans visible text spans on one page
     * @param lines horizontal strokes in the same top-down coordinate space as the spans
     * @param tableRegions candidate table bounds on that page
     */
    static void mark(List<TextSpan> spans, List<HorizontalLine> lines, List<TableRegion> tableRegions) {
        if (spans == null || spans.isEmpty() || lines == null || lines.isEmpty()) {
            return;
        }

        for (TextSpan span : spans) {
            if (span == null || span.isUnderline() || isBlank(span) || insideTable(span, tableRegions)) {
                continue;
            }
            if (hasUnderlineStroke(span, lines)) {
                span.setUnderline(true);
            }
        }
    }

    private static boolean isBlank(TextSpan span) {
        return span.getText() == null || span.getText().isBlank() || span.getWidth() <= 0f;
    }

    private static boolean insideTable(TextSpan span, List<TableRegion> tableRegions) {
        if (tableRegions == null || tableRegions.isEmpty()) {
            return false;
        }
        float centerX = span.getX() + (span.getWidth() / 2f);
        float centerY = span.getY() + (span.getHeight() / 2f);
        for (TableRegion region : tableRegions) {
            if (region == null || region.getPageIndex() != span.getPageIndex()) {
                continue;
            }
            boolean withinX = centerX >= region.getX() && centerX <= region.getX() + region.getWidth();
            boolean withinY = centerY >= region.getY() && centerY <= region.getY() + region.getHeight();
            if (withinX && withinY) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnderlineStroke(TextSpan span, List<HorizontalLine> lines) {
        float spanLeft = span.getX();
        float spanRight = span.getX() + span.getWidth();
        float spanBottom = span.getY() + span.getHeight();
        float maxBelow = Math.max(MIN_BELOW_SPAN, span.getHeight() * BELOW_SPAN_HEIGHT_RATIO);
        float maxLineLength = (span.getWidth() * MAX_LINE_LENGTH_RATIO) + MAX_LINE_LENGTH_SLACK;

        for (HorizontalLine line : lines) {
            if (line == null) {
                continue;
            }
            float lineLength = line.xEnd() - line.xStart();
            if (lineLength <= 0f || lineLength > maxLineLength) {
                continue;
            }
            float below = line.y() - spanBottom;
            if (below < -ABOVE_BASELINE_TOLERANCE || below > maxBelow) {
                continue;
            }
            float overlap = Math.min(spanRight, line.xEnd()) - Math.max(spanLeft, line.xStart());
            if (overlap < span.getWidth() * MIN_OVERLAP_RATIO) {
                continue;
            }
            // A stroke under a whole line is longer than one word. Accept it
            // when it covers this word. A long stroke that only clips the
            // edge is a table rule or a neighbour, so leave the word alone.
            if (lineLength > maxLineLength) {
                boolean coversSpan = line.xStart() <= spanLeft + 1f && line.xEnd() >= spanRight - 1f;
                if (!coversSpan) {
                    continue;
                }
            }
            return true;
        }
        return false;
    }
}
