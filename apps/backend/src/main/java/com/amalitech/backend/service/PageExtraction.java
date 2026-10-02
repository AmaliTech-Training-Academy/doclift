package com.amalitech.backend.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PageExtraction {
    private int pageIndex;
    private final List<TextSpan> textSpans = new ArrayList<>();
    private final List<ExtractedImage> images = new ArrayList<>();
    private final List<TableRegion> candidateTableRegions = new ArrayList<>();
    private final List<StructuredBlock> structuredBlocks = new ArrayList<>();

    // CropBox origin/size, in PDF points, pre-rotation (PDF user space, Y-up). Defaults to US Letter.
    private float cropX;
    private float cropY;
    private float cropWidth = 612f;
    private float cropHeight = 792f;

    /** Clockwise page rotation in degrees as declared by the PDF (one of 0, 90, 180, 270). */
    private int rotation;

    public PageExtraction(int pageIndex) {
        this.pageIndex = pageIndex;
    }
}
