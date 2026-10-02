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

    // =========================================================
    // TWO INDEPENDENT BORDERED TABLES ON THE SAME PAGE MUST NOT
    // BLEED COLUMN/ROW BOUNDARIES INTO EACH OTHER'S GRID
    // =========================================================

    @Test
    void shouldDetectTwoSeparateTablesOnSamePageWithoutCrossContamination() {

        // Mirrors the geometry of a real two-table PDF page: table A's
        // column boundaries (108.5, 163.5, 283.5, 433.5, 503.5) and table
        // B's (96, 226, 356, 436, 516) are numerically interleaved, and
        // 433.5 sits within LINE_POSITION_TOLERANCE of table B's 436 - close
        // enough to fuse if positions were clustered globally across the
        // whole page instead of per physical table.
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

    // =========================================================
    // A SINGLE RULED BOUNDARY DRAWN AS A SHORT CHAIN OF NEARLY-
    // TOUCHING LINES (e.g. a cell's border box layered under an
    // inset background-shading box - see TableLineStreamEngine)
    // MUST NOT BE READ AS TWO SEPARATE GRID LINES
    // =========================================================

    @Test
    void shouldMergeAChainOfNearDuplicateRowBoundariesIntoOneRow() {

        // The three lines at 50.0/51.75/53.5 all represent the SAME
        // visual divider between row 1 and row 2 - each adjacent pair is
        // only 1.75pt apart, but the chain spans 3.5pt end to end, more
        // than LINE_POSITION_TOLERANCE. Comparing every candidate
        // against a fixed cluster anchor (instead of its neighbor) used
        // to let that drift split one boundary into two, producing a
        // phantom extra row.
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

    // =========================================================
    // NON-RECTANGULAR (L-SHAPED) MERGE GROUPS MUST NOT CLOBBER
    // UNRELATED CELLS THAT MERELY FALL INSIDE THEIR BOUNDING BOX
    // =========================================================

    @Test
    void shouldNotOverwriteUnrelatedCellsCoveredByAnLShapedMergeGroupsBoundingBox() {

        // 2 rows x 3 cols. (0,2)/(1,0)/(1,1)/(1,2) form one L-shaped merge
        // group (missing borders chain them together), while (0,0) and
        // (0,1) are separate, fully-bordered cells. The L-group's bounding
        // box is the entire grid, but its real membership is not a
        // rectangle - (0,0) and (0,1) must keep their own text.
        List<HorizontalLine> horizontalLines = List.of(
                new HorizontalLine(0f, 0f, 300f),
                new HorizontalLine(30f, 0f, 100f),
                new HorizontalLine(30f, 100f, 200f),
                // no segment at (30, 200-300): merges (0,2) with (1,2)
                new HorizontalLine(60f, 0f, 300f)
        );

        List<VerticalLine> verticalLines = List.of(
                new VerticalLine(0f, 0f, 60f),
                new VerticalLine(100f, 0f, 30f),
                // no segment at (100, 30-60): merges (1,0) with (1,1)
                new VerticalLine(200f, 0f, 30f),
                // no segment at (200, 30-60): merges (1,1) with (1,2)
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
