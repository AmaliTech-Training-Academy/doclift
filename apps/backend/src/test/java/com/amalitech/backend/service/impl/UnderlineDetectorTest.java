package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.TableRegion;
import com.amalitech.backend.service.TextSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnderlineDetectorTest {

    @Test
    void marksASpanWhenAStrokeSitsUnderIt() {
        TextSpan span = span("Signed", 40f, 100f, 60f, 12f);
        HorizontalLine stroke = new HorizontalLine(113f, 40f, 100f);

        UnderlineDetector.mark(List.of(span), List.of(stroke), List.of());

        assertTrue(span.isUnderline());
    }

    @Test
    void leavesASpanUnmarkedWhenTheStrokeIsATableRule() {
        TextSpan span = span("Amount", 40f, 100f, 60f, 12f);
        HorizontalLine tableRule = new HorizontalLine(113f, 10f, 400f);
        TableRegion table = new TableRegion(0, 10f, 80f, 400f, 80f, 2, 2);

        UnderlineDetector.mark(List.of(span), List.of(tableRule), List.of(table));

        assertFalse(span.isUnderline());
    }

    @Test
    void marksAWordCoveredByAStrokeThatContinuesPastIt() {
        TextSpan span = span("signed", 80f, 100f, 40f, 12f);
        HorizontalLine stroke = new HorizontalLine(113f, 40f, 220f);

        UnderlineDetector.mark(List.of(span), List.of(stroke), List.of());

        assertTrue(span.isUnderline());
    }

    @Test
    void leavesAWordUnmarkedWhenALongStrokeOnlyClipsIt() {
        TextSpan span = span("signed", 40f, 100f, 40f, 12f);
        HorizontalLine stroke = new HorizontalLine(113f, 50f, 300f);

        UnderlineDetector.mark(List.of(span), List.of(stroke), List.of());

        assertFalse(span.isUnderline());
    }

    @Test
    void leavesASpanUnmarkedWhenTheStrokeDoesNotOverlap() {
        TextSpan span = span("Title", 40f, 100f, 60f, 12f);
        HorizontalLine stroke = new HorizontalLine(113f, 200f, 260f);

        UnderlineDetector.mark(List.of(span), List.of(stroke), List.of());

        assertFalse(span.isUnderline());
    }

    private static TextSpan span(String text, float x, float y, float width, float height) {
        return new TextSpan(
                0,
                text,
                x,
                y,
                width,
                height,
                "Helvetica",
                12f,
                false,
                false,
                false,
                false
        );
    }
}
