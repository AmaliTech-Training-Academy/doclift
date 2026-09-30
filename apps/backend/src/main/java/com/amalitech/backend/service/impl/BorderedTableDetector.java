package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.TextSpan;
import com.amalitech.backend.service.TableCell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


class BorderedTableDetector {

    private static final float LINE_POSITION_TOLERANCE = 3f;
    private static final float BOUNDARY_COVERAGE_TOLERANCE = 4f;
    private static final int MIN_ROWS = 2;
    private static final int MIN_COLUMNS = 2;
    private static final int MAX_BOUNDARY_POSITIONS = 60;

    List<DetectedTable> detect(
            int pageIndex,
            List<TextSpan> textSpans,
            List<HorizontalLine> horizontalLines,
            List<VerticalLine> verticalLines
    ) {
        List<DetectedTable> tables = new ArrayList<>();

        if (horizontalLines.isEmpty() || verticalLines.isEmpty()) {
            return tables;
        }

        List<Float> rowPositions = clusterPositions(
                horizontalLines.stream()
                        .map(HorizontalLine::y)
                        .toList()
        );

        List<Float> columnPositions = clusterPositions(
                verticalLines.stream()
                        .map(VerticalLine::x)
                        .toList()
        );

        if (rowPositions.size() - 1 < MIN_ROWS
                || columnPositions.size() - 1 < MIN_COLUMNS
                || rowPositions.size() > MAX_BOUNDARY_POSITIONS
                || columnPositions.size() > MAX_BOUNDARY_POSITIONS) {
            return tables;
        }

        List<int[]> rectangles = findClosedRectangles(
                rowPositions,
                columnPositions,
                horizontalLines,
                verticalLines
        );

        List<int[]> maximalRectangles = discardNestedRectangles(rectangles);

        for (int[] rectangle : maximalRectangles) {

            List<Float> rowBoundaries = rowPositions.subList(
                    rectangle[0], rectangle[1] + 1
            );

            List<Float> columnBoundaries = columnPositions.subList(
                    rectangle[2], rectangle[3] + 1
            );

            float left = columnBoundaries.getFirst();
            float right = columnBoundaries.getLast();
            float top = rowBoundaries.getFirst();
            float bottom = rowBoundaries.getLast();

            List<List<TableCell>> cells = buildCells(
                    textSpans,
                    rowBoundaries,
                    columnBoundaries,
                    horizontalLines,
                    verticalLines
            );

            tables.add(
                    new DetectedTable(
                            pageIndex,
                            left,
                            top,
                            right - left,
                            bottom - top,
                            rowBoundaries.size() - 1,
                            columnBoundaries.size() - 1,
                            cells
                    )
            );
        }

        return tables;
    }

    private List<int[]> findClosedRectangles(
            List<Float> rowPositions,
            List<Float> columnPositions,
            List<HorizontalLine> horizontalLines,
            List<VerticalLine> verticalLines
    ) {
        List<int[]> rectangles = new ArrayList<>();

        for (int topIdx = 0; topIdx < rowPositions.size(); topIdx++) {
            for (int bottomIdx = topIdx + MIN_ROWS;
                 bottomIdx < rowPositions.size();
                 bottomIdx++) {

                float top = rowPositions.get(topIdx);
                float bottom = rowPositions.get(bottomIdx);

                for (int leftIdx = 0; leftIdx < columnPositions.size(); leftIdx++) {
                    for (int rightIdx = leftIdx + MIN_COLUMNS;
                         rightIdx < columnPositions.size();
                         rightIdx++) {

                        float left = columnPositions.get(leftIdx);
                        float right = columnPositions.get(rightIdx);

                        if (horizontalEdgeExists(horizontalLines, top, left, right)
                                && horizontalEdgeExists(horizontalLines, bottom, left, right)
                                && verticalEdgeExists(verticalLines, left, top, bottom)
                                && verticalEdgeExists(verticalLines, right, top, bottom)) {

                            rectangles.add(
                                    new int[]{topIdx, bottomIdx, leftIdx, rightIdx}
                            );
                        }
                    }
                }
            }
        }

        return rectangles;
    }

    private List<int[]> discardNestedRectangles(List<int[]> rectangles) {

        List<int[]> sorted = new ArrayList<>(rectangles);

        sorted.sort(
                Comparator.<int[]>comparingInt(
                        r -> (r[1] - r[0]) * (r[3] - r[2])
                ).reversed()
        );

        List<int[]> kept = new ArrayList<>();

        for (int[] candidate : sorted) {

            boolean containedInKept = false;

            for (int[] existing : kept) {
                if (contains(existing, candidate)) {
                    containedInKept = true;
                    break;
                }
            }

            if (!containedInKept) {
                kept.add(candidate);
            }
        }

        return kept;
    }

    private boolean contains(int[] outer, int[] inner) {
        return outer[0] <= inner[0]
                && outer[1] >= inner[1]
                && outer[2] <= inner[2]
                && outer[3] >= inner[3];
    }

    private List<Float> clusterPositions(List<Float> positions) {
        List<Float> sorted = positions.stream()
                .sorted()
                .toList();

        List<Float> clustered = new ArrayList<>();

        for (Float position : sorted) {
            if (clustered.isEmpty()
                    || position - clustered.getLast() > LINE_POSITION_TOLERANCE) {
                clustered.add(position);
            }
        }

        return clustered;
    }

    private boolean horizontalEdgeExists(
            List<HorizontalLine> horizontalLines,
            float y,
            float xStart,
            float xEnd
    ) {
        List<float[]> segments = new ArrayList<>();

        for (HorizontalLine line : horizontalLines) {
            if (Math.abs(line.y() - y) <= LINE_POSITION_TOLERANCE) {
                segments.add(new float[]{line.xStart(), line.xEnd()});
            }
        }

        return unionCovers(segments, xStart, xEnd);
    }

    private boolean verticalEdgeExists(
            List<VerticalLine> verticalLines,
            float x,
            float yStart,
            float yEnd
    ) {
        List<float[]> segments = new ArrayList<>();

        for (VerticalLine line : verticalLines) {
            if (Math.abs(line.x() - x) <= LINE_POSITION_TOLERANCE) {
                segments.add(new float[]{line.yStart(), line.yEnd()});
            }
        }

        return unionCovers(segments, yStart, yEnd);
    }

    private boolean unionCovers(
            List<float[]> segments,
            float rangeStart,
            float rangeEnd
    ) {
        if (segments.isEmpty()) {
            return false;
        }

        List<float[]> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparing(segment -> segment[0]));

        float covered = rangeStart;

        for (float[] segment : sorted) {
            if (segment[0] > covered + BOUNDARY_COVERAGE_TOLERANCE) {
                return false;
            }

            covered = Math.max(covered, segment[1]);

            if (covered >= rangeEnd - BOUNDARY_COVERAGE_TOLERANCE) {
                return true;
            }
        }

        return covered >= rangeEnd - BOUNDARY_COVERAGE_TOLERANCE;
    }

    private List<List<TableCell>> buildCells(
            List<TextSpan> textSpans,
            List<Float> rowBoundaries,
            List<Float> columnBoundaries,
            List<HorizontalLine> horizontalLines,
            List<VerticalLine> verticalLines
    ) {
        int rowCount = rowBoundaries.size() - 1;
        int columnCount = columnBoundaries.size() - 1;

        int[] parent = new int[rowCount * columnCount];

        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }

        for (int row = 0; row < rowCount; row++) {
            for (int col = 0; col < columnCount; col++) {

                if (col + 1 < columnCount
                        && !verticalEdgeExists(
                        verticalLines,
                        columnBoundaries.get(col + 1),
                        rowBoundaries.get(row),
                        rowBoundaries.get(row + 1)
                )) {
                    union(
                            parent,
                            index(row, col, columnCount),
                            index(row, col + 1, columnCount)
                    );
                }

                if (row + 1 < rowCount
                        && !horizontalEdgeExists(
                        horizontalLines,
                        rowBoundaries.get(row + 1),
                        columnBoundaries.get(col),
                        columnBoundaries.get(col + 1)
                )) {
                    union(
                            parent,
                            index(row, col, columnCount),
                            index(row + 1, col, columnCount)
                    );
                }
            }
        }

        Map<Integer, int[]> groupBounds = new LinkedHashMap<>();

        for (int row = 0; row < rowCount; row++) {
            for (int col = 0; col < columnCount; col++) {

                int root = find(parent, index(row, col, columnCount));

                int[] bounds = groupBounds.get(root);

                if (bounds == null) {
                    bounds = new int[]{row, row, col, col};
                    groupBounds.put(root, bounds);
                }

                bounds[0] = Math.min(bounds[0], row);
                bounds[1] = Math.max(bounds[1], row);
                bounds[2] = Math.min(bounds[2], col);
                bounds[3] = Math.max(bounds[3], col);
            }
        }

        Map<Integer, List<TextSpan>> spansByGroup = new LinkedHashMap<>();

        float left = columnBoundaries.getFirst();
        float right = columnBoundaries.getLast();
        float top = rowBoundaries.getFirst();
        float bottom = rowBoundaries.getLast();

        for (TextSpan span : textSpans) {
            float centerX = span.getX() + (span.getWidth() / 2f);
            float centerY = span.getY() + (span.getHeight() / 2f);

            if (centerX < left || centerX > right
                    || centerY < top || centerY > bottom) {
                continue;
            }

            int row = locateBand(rowBoundaries, centerY);
            int col = locateBand(columnBoundaries, centerX);

            if (row < 0 || col < 0) {
                continue;
            }

            int root = find(parent, index(row, col, columnCount));

            spansByGroup
                    .computeIfAbsent(root, key -> new ArrayList<>())
                    .add(span);
        }

        List<List<TableCell>> cells = new ArrayList<>();

        for (int row = 0; row < rowCount; row++) {
            List<TableCell> rowCells = new ArrayList<>(columnCount);

            for (int col = 0; col < columnCount; col++) {
                rowCells.add(null);
            }

            cells.add(rowCells);
        }

        for (Map.Entry<Integer, int[]> entry : groupBounds.entrySet()) {

            int[] bounds = entry.getValue();

            int minRow = bounds[0];
            int maxRow = bounds[1];
            int minCol = bounds[2];
            int maxCol = bounds[3];

            List<TextSpan> groupSpans = new ArrayList<>(
                    spansByGroup.getOrDefault(entry.getKey(), List.of())
            );

            groupSpans.sort(
                    Comparator.comparing(TextSpan::getY)
                            .thenComparing(TextSpan::getX)
            );

            TableCell anchor = new TableCell(
                    minRow,
                    minCol,
                    (maxRow - minRow) + 1,
                    (maxCol - minCol) + 1,
                    buildCellText(groupSpans),
                    groupSpans
            );

            for (int row = minRow; row <= maxRow; row++) {
                for (int col = minCol; col <= maxCol; col++) {

                    TableCell entry2 = (row == minRow && col == minCol)
                            ? anchor
                            : new TableCell(
                                    minRow,
                                    minCol,
                                    0,
                                    0,
                                    "",
                                    List.of()
                            );

                    cells.get(row).set(col, entry2);
                }
            }
        }

        return cells;
    }

    private int index(int row, int col, int columnCount) {
        return (row * columnCount) + col;
    }

    private int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }

        return i;
    }

    private void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);

        if (rootA != rootB) {
            parent[rootA] = rootB;
        }
    }

    private int locateBand(List<Float> boundaries, float position) {
        for (int i = 0; i < boundaries.size() - 1; i++) {
            if (position >= boundaries.get(i)
                    && position <= boundaries.get(i + 1)) {
                return i;
            }
        }

        return -1;
    }

    private String buildCellText(List<TextSpan> spans) {
        StringBuilder builder = new StringBuilder();

        for (TextSpan span : spans) {
            String text = span.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(text.strip());
        }

        return builder.toString();
    }
}
