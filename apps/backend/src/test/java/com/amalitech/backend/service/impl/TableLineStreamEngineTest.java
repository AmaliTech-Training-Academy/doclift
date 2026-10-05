package com.amalitech.backend.service.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TableLineStreamEngineTest {

    @Test
    void shouldCollapseThinFilledHorizontalBarToOneCenteredLine() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(100f, 400f, 200f, 4f);
                content.fill();
            }

            TableLineStreamEngine engine = new TableLineStreamEngine(page);
            engine.processPage(page);

            assertThat(engine.getVerticalLines()).isEmpty();
            assertThat(engine.getHorizontalLines()).hasSize(1);

            HorizontalLine line = engine.getHorizontalLines().getFirst();

            assertThat(line.y()).isCloseTo(390f, within(0.5f));
            assertThat(line.xStart()).isCloseTo(100f, within(0.5f));
            assertThat(line.xEnd()).isCloseTo(300f, within(0.5f));
        }
    }

    @Test
    void shouldCollapseThinFilledVerticalBarToOneCenteredLine() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(150f, 300f, 4f, 250f);
                content.fill();
            }

            TableLineStreamEngine engine = new TableLineStreamEngine(page);
            engine.processPage(page);

            assertThat(engine.getHorizontalLines()).isEmpty();
            assertThat(engine.getVerticalLines()).hasSize(1);

            VerticalLine line = engine.getVerticalLines().getFirst();

            assertThat(line.x()).isCloseTo(152f, within(0.5f));
            assertThat(line.yEnd() - line.yStart()).isCloseTo(250f, within(0.5f));
        }
    }

    @Test
    void shouldIgnorePlainBackgroundFillWithNoStroke() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(100f, 400f, 200f, 30f);
                content.fill();
            }

            TableLineStreamEngine engine = new TableLineStreamEngine(page);
            engine.processPage(page);

            assertThat(engine.getHorizontalLines()).isEmpty();
            assertThat(engine.getVerticalLines()).isEmpty();
        }
    }

    @Test
    void shouldStillWalkEdgesOfAFilledAndStrokedBox() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(100f, 400f, 200f, 30f);
                content.fillAndStroke();
            }

            TableLineStreamEngine engine = new TableLineStreamEngine(page);
            engine.processPage(page);

            assertThat(engine.getHorizontalLines()).hasSize(2);
            assertThat(engine.getVerticalLines()).hasSize(2);
        }
    }

    @Test
    void shouldExtractStrokedRectangleEdgesAsFourLines() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(100f, 400f, 200f, 30f);
                content.stroke();
            }

            TableLineStreamEngine engine = new TableLineStreamEngine(page);
            engine.processPage(page);

            assertThat(engine.getHorizontalLines()).hasSize(2);
            assertThat(engine.getVerticalLines()).hasSize(2);
        }
    }
}
