package com.amalitech.backend.service;

import java.util.List;

/**
 * A cell in a recovered table grid.
 *
 * <p>When {@code rowSpan >= 1} this is the anchor (top-left) cell of a
 * region, with {@code text}/{@code spans} populated and
 * {@code rowSpan}/{@code columnSpan} giving its extent (1 for an
 * unmerged cell).
 *
 * <p>When {@code rowSpan == 0} this position is covered by a merge and
 * carries no content of its own; {@code row}/{@code column} instead
 * point at the coordinates of the owning anchor cell.
 */
public record TableCell(
        int row,
        int column,
        int rowSpan,
        int columnSpan,
        String text,
        List<TextSpan> spans
) {
}
