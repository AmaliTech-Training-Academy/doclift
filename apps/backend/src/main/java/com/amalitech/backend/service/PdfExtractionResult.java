package com.amalitech.backend.service;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class PdfExtractionResult {
    private final List<PageExtraction> pages = new ArrayList<>();

}
