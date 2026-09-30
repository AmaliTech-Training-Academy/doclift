package com.amalitech.backend.service;

import lombok.Getter;

import java.util.List;

@Getter
public class StructuredBlock {

    private int pageIndex;

    private BlockType type;

    private String text;

    private float x;
    private float y;
    private float width;
    private float height;

    private List<TextSpan> spans;

    /**
     * Populated only for {@link BlockType#TABLE} blocks: the recovered
     * grid of cells, outer list is rows, inner list is columns.
     */
    private List<List<TableCell>> tableRows;

    public StructuredBlock(
            int pageIndex,
            BlockType type,
            String text,
            float x,
            float y,
            float width,
            float height,
            List<TextSpan> spans
    ) {
        this(pageIndex, type, text, x, y, width, height, spans, null);
    }

    public StructuredBlock(
            int pageIndex,
            BlockType type,
            String text,
            float x,
            float y,
            float width,
            float height,
            List<TextSpan> spans,
            List<List<TableCell>> tableRows
    ) {
        this.pageIndex = pageIndex;
        this.type = type;
        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.spans = spans;
        this.tableRows = tableRows;
    }
}