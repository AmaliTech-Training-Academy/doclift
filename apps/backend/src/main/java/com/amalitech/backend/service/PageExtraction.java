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
    private float pageWidth;
    private float pageHeight;
    private final List<TextSpan> textSpans = new ArrayList<>();
    private final List<ExtractedImage> images = new ArrayList<>();
    private final List<TableRegion> candidateTableRegions = new ArrayList<>();
    private final List<StructuredBlock> structuredBlocks = new ArrayList<>();

    // CropBox origin/size, in PDF points, pre-rotation (PDF user space, Y-up). Defaults to US Letter.
    private float cropX;
    private float cropY;
    private float cropWidth = 612f;
    private float cropHeight = 792f;

    private int rotation;

    public PageExtraction(int pageIndex) {
        this.pageIndex = pageIndex;
    }
}
