package com.amalitech.backend.service.implementation;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import com.amalitech.backend.service.ExtractedImage;
import com.amalitech.backend.service.PageExtraction;
import com.amalitech.backend.service.PdfExtractionResult;
import com.amalitech.backend.service.TableRegion;
import com.amalitech.backend.service.TextSpan;
import com.amalitech.backend.service.interfaces.PdfExtractionService;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PdfExtractionServiceImpl implements PdfExtractionService {

    @Override
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
                float maxX = -Float.MAX_VALUE;
                float minY = Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                String fontName = null;
                float fontSize = 0f;

                TextPosition previousPosition = null;
                for (TextPosition position : textPositions) {
                    String unicode = position.getUnicode();
                    if (unicode != null) {
                        if (needsWordSeparator(previousPosition, position, textBuilder)) {
                            textBuilder.append(' ');
                        }
                        textBuilder.append(unicode);
                    }

                    minX = Math.min(minX, position.getXDirAdj());
                    maxX = Math.max(maxX, position.getXDirAdj() + position.getWidthDirAdj());
                    minY = Math.min(minY, position.getYDirAdj());
                    maxY = Math.max(maxY, position.getYDirAdj() + position.getHeightDir());

                    if (position.getFont() != null && position.getFont().getName() != null) {
                        fontName = position.getFont().getName();
                    }
                    fontSize = Math.max(fontSize, position.getFontSizeInPt());
                    previousPosition = position;
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
        collectImages(pageIndex, page.getResources(), images, new HashSet<>());
        return images;
    }

    private void collectImages(
            int pageIndex,
            PDResources resources,
            List<ExtractedImage> images,
            Set<COSBase> visited
    ) throws IOException {
        if (resources == null) {
            return;
        }

        for (COSName resourceName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(resourceName);
            COSBase xObjectKey = xObject.getCOSObject();
            if (!visited.add(xObjectKey)) {
                continue;
            }

            if (xObject instanceof PDImageXObject image) {
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
            } else if (xObject instanceof PDFormXObject form) {
                collectImages(pageIndex, form.getResources(), images, visited);
            }
        }
    }

    private boolean needsWordSeparator(
            TextPosition previous,
            TextPosition current,
            StringBuilder textBuilder
    ) {
        if (previous == null
                || textBuilder.isEmpty()
                || Character.isWhitespace(textBuilder.charAt(textBuilder.length() - 1))
                || current.getUnicode() == null
                || current.getUnicode().isBlank()) {
            return false;
        }

        float gap = current.getXDirAdj()
                - (previous.getXDirAdj() + previous.getWidthDirAdj());
        float spaceWidth = Math.max(previous.getWidthOfSpace(), current.getWidthOfSpace());
        return gap > Math.max(1f, spaceWidth * 0.5f);
    }

    private List<TableRegion> detectCandidateTableRegions(int pageIndex, List<TextSpan> textSpans) {
        List<TableRegion> regions = new ArrayList<>();
        if (textSpans.size() < 3) {
            return regions;
        }

        Map<Integer, Integer> xBuckets = new HashMap<>();
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

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
