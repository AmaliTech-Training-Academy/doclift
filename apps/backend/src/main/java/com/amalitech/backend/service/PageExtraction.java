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

    public PageExtraction(int pageIndex) {
        this.pageIndex = pageIndex;
    }
}
