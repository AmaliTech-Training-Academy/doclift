package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.TextSpan;
import com.amalitech.backend.service.TableCell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BorderedTableDetectorTest {

    private final BorderedTableDetector detector = new BorderedTableDetector();

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

    @Test
    void shouldMapHeaderCellSpanningMultipleColumnsAsOneAnchorWithColumnSpan() {

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

    @Test
    void shouldNotFuseUnrelatedStrayLineWithRealTableGrid() {

        List<HorizontalLine> horizontalLines = new java.util.ArrayList<>(List.of(
                new HorizontalLine(0f, 0f, 300f),
                new HorizontalLine(30f, 0f, 300f),
                new HorizontalLine(60f, 0f, 300f),
                new HorizontalLine(90f, 0f, 300f)
        ));

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
    void shouldDetectTwoSeparateTablesOnSamePageWithoutCrossContamination() {

        List<HorizontalLine> horizontalLines = List.of(
                new HorizontalLine(140f, 108.5f, 503.5f),
                new HorizontalLine(168f, 108.5f, 503.5f),
                new HorizontalLine(196f, 108.5f, 503.5f),
                new HorizontalLine(224f, 108.5f, 503.5f),
                new HorizontalLine(252f, 108.5f, 503.5f),
                new HorizontalLine(280f, 108.5f, 503.5f),

                new HorizontalLine(341f, 96f, 516f),
                new HorizontalLine(369f, 96f, 516f),
                new HorizontalLine(397f, 96f, 516f),
                new HorizontalLine(425f, 96f, 516f),
                new HorizontalLine(453f, 96f, 516f),
                new HorizontalLine(481f, 96f, 516f)
        );

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(108.5f, 140f, 280f),
                new VerticalLine(163.5f, 140f, 280f),
                new VerticalLine(283.5f, 140f, 280f),
                new VerticalLine(433.5f, 140f, 280f),
                new VerticalLine(503.5f, 140f, 280f),

                new VerticalLine(96f, 341f, 481f),
                new VerticalLine(226f, 341f, 481f),
                new VerticalLine(356f, 341f, 481f),
                new VerticalLine(436f, 341f, 481f),
                new VerticalLine(516f, 341f, 481f)
        );

        List<TextSpan> textSpans = List.of(
                span("ID", 110, 145),
                span("Name", 165, 145),
                span("Course", 285, 145),
                span("Score", 435, 145),

                span("Product", 98, 346),
                span("Category", 228, 346),
                span("Quantity", 358, 346),
                span("Price", 438, 346)
        );

        List<DetectedTable> tables =
                detector.detect(0, textSpans, horizontalLines, verticalLines);

        assertThat(tables).hasSize(2);

        DetectedTable tableA = tables.stream()
                .filter(t -> t.y() < 300f)
                .findFirst()
                .orElseThrow();

        DetectedTable tableB = tables.stream()
                .filter(t -> t.y() >= 300f)
                .findFirst()
                .orElseThrow();

        assertThat(tableA.columnCount()).isEqualTo(4);
        assertThat(tableB.columnCount()).isEqualTo(4);

        for (List<TableCell> row : tableA.cells()) {
            for (TableCell cell : row) {
                assertThat(cell.columnSpan()).isIn(0, 1);
            }
        }

        for (List<TableCell> row : tableB.cells()) {
            for (TableCell cell : row) {
                assertThat(cell.columnSpan()).isIn(0, 1);
            }
        }
    }

    @Test
    void shouldReturnNoTablesWhenNoLinesArePresent() {

        List<DetectedTable> tables =
                detector.detect(0, List.of(), List.of(), List.of());

        assertThat(tables).isEmpty();
    }

    @Test
    void shouldMergeAChainOfNearDuplicateRowBoundariesIntoOneRow() {

        List<HorizontalLine> horizontalLines = List.of(
                new HorizontalLine(0f, 0f, 200f),
                new HorizontalLine(50.0f, 0f, 200f),
                new HorizontalLine(51.75f, 0f, 200f),
                new HorizontalLine(53.5f, 0f, 200f),
                new HorizontalLine(100f, 0f, 200f)
        );

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(0f, 0f, 100f),
                new VerticalLine(100f, 0f, 100f),
                new VerticalLine(200f, 0f, 100f)
        );

        List<TextSpan> textSpans = List.of(
                span("R1C1", 10, 10),
                span("R1C2", 110, 10),
                span("R2C1", 10, 60),
                span("R2C2", 110, 60)
        );

        List<DetectedTable> tables =
                detector.detect(0, textSpans, horizontalLines, verticalLines);

        assertThat(tables).hasSize(1);

        DetectedTable table = tables.getFirst();

        assertThat(table.rowCount())
                .as("the near-duplicate chain should collapse to one row boundary, not two rows")
                .isEqualTo(2);

        assertThat(table.columnCount()).isEqualTo(2);
        assertThat(table.cells()).hasSize(2);

        assertThat(table.cells().get(0).get(0).text()).isEqualTo("R1C1");
        assertThat(table.cells().get(0).get(1).text()).isEqualTo("R1C2");
        assertThat(table.cells().get(1).get(0).text()).isEqualTo("R2C1");
        assertThat(table.cells().get(1).get(1).text()).isEqualTo("R2C2");
    }

    @Test
    void shouldLeaveNonRectangularMergeGroupsUnmergedInsteadOfProducingAnInvalidTable() {

        List<HorizontalLine> horizontalLines = List.of(
                new HorizontalLine(0f, 0f, 300f),
                new HorizontalLine(30f, 0f, 100f),
                new HorizontalLine(30f, 100f, 200f),
                new HorizontalLine(60f, 0f, 300f)
        );

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(0f, 0f, 60f),
                new VerticalLine(100f, 0f, 30f),
                new VerticalLine(200f, 0f, 30f),
                new VerticalLine(300f, 0f, 60f)
        );

        List<TextSpan> textSpans = List.of(
                span("KeepMe00", 10, 10),
                span("KeepMe01", 110, 10),
                span("Merged", 210, 40)
        );

        List<DetectedTable> tables =
                detector.detect(0, textSpans, horizontalLines, verticalLines);

        assertThat(tables).hasSize(1);

        DetectedTable table = tables.getFirst();

        assertThat(table.rowCount()).isEqualTo(2);
        assertThat(table.columnCount()).isEqualTo(3);

        assertThat(table.cells().get(0).get(0).text()).isEqualTo("KeepMe00");
        assertThat(table.cells().get(0).get(1).text()).isEqualTo("KeepMe01");

        List<TableCell> mergeGroupMembers = List.of(
                table.cells().get(0).get(2),
                table.cells().get(1).get(0),
                table.cells().get(1).get(1),
                table.cells().get(1).get(2)
        );

        assertThat(mergeGroupMembers)
                .as("a non-rectangular group must never merge - every member stays 1x1")
                .allSatisfy(cell -> {
                    assertThat(cell.rowSpan()).isEqualTo(1);
                    assertThat(cell.columnSpan()).isEqualTo(1);
                });

        assertThat(table.cells().get(0).get(2).text()).isEqualTo("");
        assertThat(table.cells().get(1).get(0).text()).isEqualTo("");
        assertThat(table.cells().get(1).get(1).text()).isEqualTo("");
        assertThat(table.cells().get(1).get(2).text()).isEqualTo("Merged");
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
