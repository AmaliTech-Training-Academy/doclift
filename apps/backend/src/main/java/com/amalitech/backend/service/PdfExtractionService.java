package com.amalitech.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfExtractionService {

    private final PdfValidationService pdfValidationService;

    public PdfExtractionService(PdfValidationService pdfValidationService) {
        this.pdfValidationService = pdfValidationService;
    }

    public PdfExtractionResult extract(MultipartFile file) throws IOException {
        pdfValidationService.validateAndGetPageCount(file);
        return extract(file.getBytes());
    }

    public PdfExtractionResult extract(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return extract(document);
        }
    }

    public PdfExtractionResult extract(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("The input stream is null.");
        }
        return extract(inputStream.readAllBytes());
    }

    public PdfExtractionResult extract(PDDocument document) throws IOException {
        PdfExtractionResult result = new PdfExtractionResult();

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            PageExtraction pageExtraction = new PageExtraction(pageIndex);

            List<TextSpan> textSpans = extractTextSpans(pageIndex, page);
            pageExtraction.getTextSpans().addAll(textSpans);

            pageExtraction.getImages().addAll(extractImages(pageIndex, page));
            pageExtraction.getCandidateTableRegions().addAll(detectCandidateTableRegions(pageIndex, textSpans));

            result.getPages().add(pageExtraction);
        }

        return result;
    }

    private List<TextSpan> extractTextSpans(int pageIndex, PDPage page) throws IOException {
        final List<TextSpan> spans = new ArrayList<>();

        PDFTextStripper stripper = new PDFTextStripper() {
            {
                this.output = new StringWriter();
            }

            @Override
            protected void writeString(String string, List<TextPosition> textPositions) {
                if (textPositions == null || textPositions.isEmpty()) {
                    return;
                }

                StringBuilder textBuilder = new StringBuilder();
                float minX = Float.MAX_VALUE;
                float maxX = Float.MIN_VALUE;
                float minY = Float.MAX_VALUE;
                float maxY = Float.MIN_VALUE;
                String fontName = null;
                float fontSize = 0f;

                for (TextPosition position : textPositions) {
                    if (position.getUnicode() != null && !position.getUnicode().isBlank()) {
                        textBuilder.append(position.getUnicode());
                    }

                    minX = Math.min(minX, position.getXDirAdj());
                    maxX = Math.max(maxX, position.getXDirAdj() + position.getWidthDirAdj());
                    minY = Math.min(minY, position.getYDirAdj());
                    maxY = Math.max(maxY, position.getYDirAdj() + position.getHeightDir());

                    if (position.getFont() != null && position.getFont().getName() != null) {
                        fontName = position.getFont().getName();
                    }
                    fontSize = Math.max(fontSize, position.getFontSizeInPt());
                }

                String extractedText = textBuilder.toString().trim();
                if (extractedText.isEmpty() || minX == Float.MAX_VALUE) {
                    return;
                }

                spans.add(new TextSpan(
                        pageIndex,
                        extractedText,
                        minX,
                        minY,
                        Math.max(0f, maxX - minX),
                        Math.max(0f, maxY - minY),
                        fontName == null ? "unknown" : fontName,
                        fontSize
                ));
            }
        };

        stripper.setSortByPosition(true);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.processPage(page);
        return spans;
    }

    private List<ExtractedImage> extractImages(int pageIndex, PDPage page) throws IOException {
        List<ExtractedImage> images = new ArrayList<>();
        if (page.getResources() == null) {
            return images;
        }

        for (COSName resourceName : page.getResources().getXObjectNames()) {
            var xObject = page.getResources().getXObject(resourceName);
            if (xObject instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image) {
                images.add(new ExtractedImage(
                        pageIndex,
                        resourceName.getName(),
                        0f,
                        0f,
                        image.getWidth(),
                        image.getHeight(),
                        image.getWidth(),
                        image.getHeight(),
                        image.getSuffix()
                ));
            }
        }
        return images;
    }

    private List<TableRegion> detectCandidateTableRegions(int pageIndex, List<TextSpan> textSpans) {
        List<TableRegion> regions = new ArrayList<>();
        if (textSpans.size() < 3) {
            return regions;
        }

        Map<Integer, Integer> xBuckets = new HashMap<>();
        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;

        for (TextSpan span : textSpans) {
            minX = Math.min(minX, span.getX());
            maxX = Math.max(maxX, span.getX() + span.getWidth());
            minY = Math.min(minY, span.getY());
            maxY = Math.max(maxY, span.getY() + span.getHeight());

            int bucket = Math.round((span.getX() + (span.getWidth() / 2f)) / 80f);
            xBuckets.put(bucket, xBuckets.getOrDefault(bucket, 0) + 1);
        }

        long distinctColumns = xBuckets.size();
        if (distinctColumns >= 2) {
            regions.add(new TableRegion(
                    pageIndex,
                    minX,
                    minY,
                    Math.max(0f, maxX - minX),
                    Math.max(0f, maxY - minY),
                    Math.max(2, (int) Math.ceil(textSpans.size() / 3.0)),
                    Math.max(2, (int) distinctColumns)
            ));
        }

        return regions;
    }
}
