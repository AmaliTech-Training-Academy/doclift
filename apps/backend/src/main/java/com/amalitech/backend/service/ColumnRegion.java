package com.amalitech.backend.service;

public record ColumnRegion(
        float startY,
        float endY,
        int columnCount,
        float splitX
){}