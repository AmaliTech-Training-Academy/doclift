package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.TableCell;

import java.util.List;

record DetectedTable(
        int pageIndex,
        float x,
        float y,
        float width,
        float height,
        int rowCount,
        int columnCount,
        List<List<TableCell>> cells,
        List<Float> columnWidths,
        List<Float> rowHeights
) {
}
