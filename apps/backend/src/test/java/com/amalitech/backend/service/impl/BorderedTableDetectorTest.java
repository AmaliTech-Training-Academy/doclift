package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.TextSpan;
import com.amalitech.backend.service.TableCell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BorderedTableDetectorTest {

    private final BorderedTableDetector detector = new BorderedTableDetector();

    // =========================================================
    // 3x3 GRID DRAWN AS FULL-WIDTH/HEIGHT BORDER LINES
    // =========================================================

    @Test
    void shouldDetectThreeByThreeBorderedGridAndMapCellsCorrectly() {

        List<HorizontalLine> horizontalLines = List.of(
                new HorizontalLine(0f, 0f, 300f),
                new HorizontalLine(30f, 0f, 300f),
                new HorizontalLine(60f, 0f, 300f),
                new HorizontalLine(90f, 0f, 300f)
        );

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(0f, 0f, 90f),
                new VerticalLine(100f, 0f, 90f),
                new VerticalLine(200f, 0f, 90f),
                new VerticalLine(300f, 0f, 90f)
        );

        List<TextSpan> textSpans = List.of(
                span("R1C1", 10, 10),
                span("R1C2", 110, 10),
                span("R1C3", 210, 10),
                span("R2C1", 10, 40),
                span("R2C2", 110, 40),
                span("R2C3", 210, 40),
                span("R3C1", 10, 70),
                span("R3C2", 110, 70),
                span("R3C3", 210, 70)
        );

        List<DetectedTable> tables =
                detector.detect(0, textSpans, horizontalLines, verticalLines);

        assertThat(tables).hasSize(1);

        DetectedTable table = tables.getFirst();

        assertThat(table.rowCount()).isEqualTo(3);
        assertThat(table.columnCount()).isEqualTo(3);
        assertThat(table.cells()).hasSize(3);

        for (int row = 0; row < 3; row++) {
            assertThat(table.cells().get(row)).hasSize(3);

            for (int col = 0; col < 3; col++) {
                TableCell cell = table.cells().get(row).get(col);

                assertThat(cell.row()).isEqualTo(row);
                assertThat(cell.column()).isEqualTo(col);
                assertThat(cell.text())
                        .isEqualTo("R" + (row + 1) + "C" + (col + 1));
            }
        }
    }

    // =========================================================
    // 3x3 GRID ASSEMBLED FROM NINE INDIVIDUALLY BORDERED CELLS
    // =========================================================

    @Test
    void shouldDetectGridAssembledFromIndividualCellRectangles() {

        List<HorizontalLine> horizontalLines = List.of(
                new HorizontalLine(0f, 0f, 100f),
                new HorizontalLine(0f, 100f, 200f),
                new HorizontalLine(0f, 200f, 300f),

                new HorizontalLine(30f, 0f, 100f),
                new HorizontalLine(30f, 100f, 200f),
                new HorizontalLine(30f, 200f, 300f),

                new HorizontalLine(60f, 0f, 100f),
                new HorizontalLine(60f, 100f, 200f),
                new HorizontalLine(60f, 200f, 300f),

                new HorizontalLine(90f, 0f, 100f),
                new HorizontalLine(90f, 100f, 200f),
                new HorizontalLine(90f, 200f, 300f)
        );

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(0f, 0f, 30f),
                new VerticalLine(0f, 30f, 60f),
                new VerticalLine(0f, 60f, 90f),

                new VerticalLine(100f, 0f, 30f),
                new VerticalLine(100f, 30f, 60f),
                new VerticalLine(100f, 60f, 90f),

                new VerticalLine(200f, 0f, 30f),
                new VerticalLine(200f, 30f, 60f),
                new VerticalLine(200f, 60f, 90f),

                new VerticalLine(300f, 0f, 30f),
                new VerticalLine(300f, 30f, 60f),
                new VerticalLine(300f, 60f, 90f)
        );

        List<TextSpan> textSpans = List.of(
                span("A", 10, 10),
                span("B", 110, 10),
                span("C", 210, 10)
        );

        List<DetectedTable> tables =
                detector.detect(0, textSpans, horizontalLines, verticalLines);

        assertThat(tables).hasSize(1);

        DetectedTable table = tables.getFirst();

        assertThat(table.rowCount()).isEqualTo(3);
        assertThat(table.columnCount()).isEqualTo(3);

        assertThat(table.cells().get(0).get(0).text()).isEqualTo("A");
        assertThat(table.cells().get(0).get(1).text()).isEqualTo("B");
        assertThat(table.cells().get(0).get(2).text()).isEqualTo("C");
        assertThat(table.cells().get(1).get(0).text()).isEmpty();
    }

    // =========================================================
    // MERGED / SPANNING CELLS (e.g. a header spanning columns)
    // =========================================================

    @Test
    void shouldMapHeaderCellSpanningMultipleColumnsAsOneAnchorWithColumnSpan() {

        // 2 rows x 4 cols; row 1's middle 3 columns merge into one cell,
        // mirroring a real-world "label | spanning value" table.
        List<HorizontalLine> horizontalLines = List.of(
                new HorizontalLine(0f, 0f, 400f),
                new HorizontalLine(30f, 0f, 400f),
                new HorizontalLine(60f, 0f, 400f)
        );

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(0f, 0f, 60f),
                new VerticalLine(100f, 0f, 60f),
                new VerticalLine(200f, 0f, 30f),
                new VerticalLine(300f, 0f, 30f),
                new VerticalLine(400f, 0f, 60f)
        );

        List<TextSpan> textSpans = List.of(
                span("Label", 10, 10),
                span("A", 110, 10),
                span("B", 210, 10),
                span("C", 310, 10),
                span("Continent", 10, 40),
                span("Europe", 150, 40)
        );

        List<DetectedTable> tables =
                detector.detect(0, textSpans, horizontalLines, verticalLines);

        assertThat(tables).hasSize(1);

        DetectedTable table = tables.getFirst();

        assertThat(table.rowCount()).isEqualTo(2);
        assertThat(table.columnCount()).isEqualTo(4);

        TableCell anchor = table.cells().get(1).get(1);

        assertThat(anchor.rowSpan()).isEqualTo(1);
        assertThat(anchor.columnSpan()).isEqualTo(3);
        assertThat(anchor.text()).isEqualTo("Europe");

        for (int col = 2; col <= 3; col++) {
            TableCell covered = table.cells().get(1).get(col);

            assertThat(covered.rowSpan()).isZero();
            assertThat(covered.row()).isEqualTo(1);
            assertThat(covered.column()).isEqualTo(1);
        }

        assertThat(table.cells().get(1).get(0).text()).isEqualTo("Continent");
    }

    // =========================================================
    // UNRELATED LINES ELSEWHERE ON THE PAGE MUST NOT BE FUSED
    // INTO THE REAL TABLE'S GRID
    // =========================================================

    @Test
    void shouldNotFuseUnrelatedStrayLineWithRealTableGrid() {

        List<HorizontalLine> horizontalLines = new java.util.ArrayList<>(List.of(
                new HorizontalLine(0f, 0f, 300f),
                new HorizontalLine(30f, 0f, 300f),
                new HorizontalLine(60f, 0f, 300f),
                new HorizontalLine(90f, 0f, 300f)
        ));

        // An unrelated short rule elsewhere on the page (e.g. a footnote
        // separator) that does not span the table's width.
        horizontalLines.add(new HorizontalLine(400f, 0f, 80f));

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(0f, 0f, 90f),
                new VerticalLine(100f, 0f, 90f),
                new VerticalLine(200f, 0f, 90f),
                new VerticalLine(300f, 0f, 90f)
        );

        List<TextSpan> textSpans = List.of(
                span("R1C1", 10, 10),
                span("R1C2", 110, 10),
                span("R1C3", 210, 10)
        );

        List<DetectedTable> tables =
                detector.detect(0, textSpans, horizontalLines, verticalLines);

        assertThat(tables).hasSize(1);

        DetectedTable table = tables.getFirst();

        assertThat(table.rowCount()).isEqualTo(3);
        assertThat(table.columnCount()).isEqualTo(3);
        assertThat(table.height()).isEqualTo(90f);
    }

    // =========================================================
    // INCOMPLETE / NON-GRID GEOMETRY SHOULD NOT BE DETECTED
    // =========================================================

    @Test
    void shouldIgnoreIncompleteGridWithMissingBorders() {

        List<HorizontalLine> horizontalLines = List.of(
                new HorizontalLine(0f, 0f, 300f)
        );

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(0f, 0f, 90f)
        );

        List<TextSpan> textSpans = List.of(
                span("Loose text", 10, 10)
        );

        List<DetectedTable> tables =
                detector.detect(0, textSpans, horizontalLines, verticalLines);

        assertThat(tables).isEmpty();
    }

    @Test
    void shouldReturnNoTablesWhenNoLinesArePresent() {

        List<DetectedTable> tables =
                detector.detect(0, List.of(), List.of(), List.of());

        assertThat(tables).isEmpty();
    }

    private TextSpan span(String text, float x, float y) {
        return new TextSpan(
                0,
                text,
                x,
                y,
                40,
                12,
                "Helvetica",
                12,
                false,
                false,
                false,
                false
        );
    }
}
