package com.amalitech.backend.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
import com.amalitech.backend.service.PdfExtractionService;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.util.Matrix;


import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        new ImageLocationStreamEngine(pageIndex, images).processPage(page);
        return images;
    }

    /**
     * Walks the page content stream (including nested Form XObjects) and records, for every
     * image "Do" invocation, the image's actual on-page position and size in PDF point-space,
     * derived from the current transformation matrix (CTM) at that point in the stream —
     * as opposed to the resource's raw pixel dimensions.
     */
    private static class ImageLocationStreamEngine extends PDFStreamEngine {

        private final int pageIndex;
        private final List<ExtractedImage> images;
        private final Deque<COSBase> formsInProgress = new ArrayDeque<>();

        ImageLocationStreamEngine(int pageIndex, List<ExtractedImage> images) {
            this.pageIndex = pageIndex;
            this.images = images;
            addOperator(new Concatenate(this));
            addOperator(new SetGraphicsStateParameters(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
            addOperator(new SetMatrix(this));
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (!"Do".equals(operator.getName()) || operands.isEmpty()
                    || !(operands.get(0) instanceof COSName objectName)) {
                super.processOperator(operator, operands);
                return;
            }

            PDXObject xObject = getResources().getXObject(objectName);
            if (xObject == null) {
                return;
            }

            if (xObject instanceof PDImageXObject image) {
                recordImagePlacement(objectName.getName(), image);
            } else if (xObject instanceof PDFormXObject form) {
                COSBase formKey = form.getCOSObject();
                if (formsInProgress.contains(formKey)) {
                    // Self-referential form; skip to avoid infinite recursion.
                    return;
                }
                formsInProgress.push(formKey);
                try {
                    showForm(form);
                } finally {
                    formsInProgress.pop();
                }
            }
        }

        private void recordImagePlacement(String imageName, PDImageXObject image) {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();

            // Transform all four corners of the unit square (the space an image is drawn into)
            // rather than reading getScaleX()/getScaleY() directly, so rotated or sheared
            // placements still produce a correct axis-aligned bounding box.
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;

            float[][] unitCorners = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            for (float[] corner : unitCorners) {
                Point2D.Float p = ctm.transformPoint(corner[0], corner[1]);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }

            images.add(new ExtractedImage(
                    pageIndex,
                    imageName,
                    minX,
                    minY,
                    maxX - minX,
                    maxY - minY,
                    image.getWidth(),
                    image.getHeight(),
                    image.getSuffix()
            ));
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
