package com.amalitech.backend.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Temporary diagnostic: dumps every horizontal/vertical line segment
 * TableLineStreamEngine extracts from a page, sorted by position, so the
 * raw geometry feeding table detection can be inspected directly.
 */
class LineGeometryDumpTest {

    @Test
    @EnabledIfSystemProperty(named = "pdf.path", matches = ".+")
    void dumpLineGeometry() throws IOException {

        Path inputPath = Path.of(System.getProperty("pdf.path"));
        byte[] pdfBytes = Files.readAllBytes(inputPath);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {

                PDPage page = document.getPage(pageIndex);

                TableLineStreamEngine engine = new TableLineStreamEngine(page);
                engine.processPage(page);

                System.out.println("=== page " + pageIndex + " ===");

                System.out.println("Horizontal lines (" + engine.getHorizontalLines().size() + "):");
                engine.getHorizontalLines().stream()
                        .sorted(Comparator.comparing(HorizontalLine::y))
                        .forEach(l -> System.out.printf(
                                "  y=%.2f  x=[%.2f, %.2f]  len=%.2f%n",
                                l.y(), l.xStart(), l.xEnd(), l.xEnd() - l.xStart()
                        ));

                System.out.println("Vertical lines (" + engine.getVerticalLines().size() + "):");
                engine.getVerticalLines().stream()
                        .sorted(Comparator.comparing(VerticalLine::x))
                        .forEach(l -> System.out.printf(
                                "  x=%.2f  y=[%.2f, %.2f]  len=%.2f%n",
                                l.x(), l.yStart(), l.yEnd(), l.yEnd() - l.yStart()
                        ));
            }
        }
    }
}
