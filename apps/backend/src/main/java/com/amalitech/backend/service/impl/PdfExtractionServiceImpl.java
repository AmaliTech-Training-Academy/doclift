package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.*;
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
import java.util.Comparator;
import java.util.List;

@Service
public class PdfExtractionServiceImpl implements PdfExtractionService {

    private static final int MIN_TABLE_ROWS = 3;
    // Two shared whitespace channels = at least three aligned columns.
    private static final int MIN_TABLE_CHANNELS = 2;
    private static final float TABLE_MIN_CELL_GAP = 8f;
    private static final float TABLE_CELL_GAP_FONT_FACTOR = 0.9f;
    private static final float TABLE_MIN_CHANNEL_WIDTH = 2f;
    private static final float TABLE_MIN_ROW_TOLERANCE = 2f;
    private static final int MIN_TWO_COLUMN_TABLE_ROWS = 3;
    private static final float COLUMN_ALIGNMENT_TOLERANCE = 20f;

    private final StructureRecoveryService structureRecoveryService;

    public PdfExtractionServiceImpl(
            StructureRecoveryService structureRecoveryService
    ) {
        this.structureRecoveryService = structureRecoveryService;
    }

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

            structureRecoveryService.recoverStructure(pageExtraction);

            result.getPages().add(pageExtraction);
        }

        return result;
    }

    private boolean looksLikeTwoColumnTable(
            List<List<TextSpan>> rows
    ) {
        if (rows.size() < MIN_TWO_COLUMN_TABLE_ROWS) {
            return false;
        }

        Float expectedLeftX = null;
        Float expectedRightX = null;
        int matchingRows = 0;

        for (List<TextSpan> row : rows) {
            if (row.size() != 2) {
                continue;
            }

            TextSpan left = row.get(0);
            TextSpan right = row.get(1);

            if (expectedLeftX == null) {
                expectedLeftX = left.getX();
                expectedRightX = right.getX();
                matchingRows++;
                continue;
            }

            boolean leftAligned =
                    Math.abs(left.getX() - expectedLeftX)
                            <= COLUMN_ALIGNMENT_TOLERANCE;

            boolean rightAligned =
                    Math.abs(right.getX() - expectedRightX)
                            <= COLUMN_ALIGNMENT_TOLERANCE;

            if (leftAligned && rightAligned) {
                matchingRows++;
            }
        }

        return matchingRows >= MIN_TWO_COLUMN_TABLE_ROWS;
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
        // processPage() handles exactly this page. Setting start/end page here
        // would be compared against the stripper's own page counter, which a
        // fresh stripper never advances, so every page after the first would
        // be skipped.
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

    /**
     * Detects runs of consecutive text rows that share at least two vertical
     * whitespace channels, i.e. three or more aligned columns. Two-column body
     * text only shares a single channel (the gutter) and is not reported;
     * word and sentence gaps do not line up across rows.
     */
    private List<TableRegion> detectCandidateTableRegions(
            int pageIndex,
            List<TextSpan> textSpans
    ) {
        List<TableRegion> regions = new ArrayList<>();

        if (textSpans.size() < 3) {
            return regions;
        }

        List<List<TextSpan>> rows = groupIntoRows(textSpans);

        // First pass: detect tables with 3 or more columns.
        int start = 0;

        while (start < rows.size()) {
            List<float[]> channels = findCellGaps(rows.get(start));
            int end = start + 1;

            while (end < rows.size()
                    && channels.size() >= MIN_TABLE_CHANNELS) {

                List<float[]> narrowed =
                        intersectGaps(
                                channels,
                                findCellGaps(rows.get(end))
                        );

                if (narrowed.size() < MIN_TABLE_CHANNELS) {
                    break;
                }

                channels = narrowed;
                end++;
            }

            int rowCount = end - start;

            if (rowCount >= MIN_TABLE_ROWS
                    && channels.size() >= MIN_TABLE_CHANNELS) {

                regions.add(
                        toTableRegion(
                                pageIndex,
                                rows.subList(start, end),
                                channels.size() + 1
                        )
                );

                start = end;
            } else {
                start++;
            }
        }

        // Second pass: detect genuine 2-column tables.
        for (int rowStart = 0; rowStart < rows.size(); rowStart++) {
            int rowEnd = rowStart;

            while (rowEnd < rows.size()
                    && rows.get(rowEnd).size() == 2) {
                rowEnd++;
            }

            if (rowEnd - rowStart >= MIN_TWO_COLUMN_TABLE_ROWS) {
                List<List<TextSpan>> candidateRows =
                        rows.subList(rowStart, rowEnd);

                if (looksLikeTwoColumnTable(candidateRows)) {
                    regions.add(
                            toTableRegion(
                                    pageIndex,
                                    candidateRows,
                                    2
                            )
                    );
                }
            }

            if (rowEnd > rowStart) {
                rowStart = rowEnd - 1;
            }
        }

        return regions;
    }

    private List<List<TextSpan>> groupIntoRows(List<TextSpan> textSpans) {
        List<TextSpan> sorted = new ArrayList<>(textSpans);
        sorted.sort(Comparator.comparing(TextSpan::getY).thenComparing(TextSpan::getX));

        List<List<TextSpan>> rows = new ArrayList<>();
        List<TextSpan> current = new ArrayList<>();
        float currentY = 0f;

        for (TextSpan span : sorted) {
            float tolerance = Math.max(TABLE_MIN_ROW_TOLERANCE, span.getHeight() * 0.5f);
            if (!current.isEmpty() && Math.abs(span.getY() - currentY) > tolerance) {
                rows.add(current);
                current = new ArrayList<>();
            }
            if (current.isEmpty()) {
                currentY = span.getY();
            }
            current.add(span);
        }

        if (!current.isEmpty()) {
            rows.add(current);
        }

        for (List<TextSpan> row : rows) {
            row.sort(Comparator.comparing(TextSpan::getX));
        }

        return rows;
    }

    private List<float[]> findCellGaps(List<TextSpan> row) {
        List<float[]> gaps = new ArrayList<>();
        float right = -Float.MAX_VALUE;
        float fontSize = 0f;

        for (TextSpan span : row) {
            if (right != -Float.MAX_VALUE) {
                float threshold = Math.max(
                        TABLE_MIN_CELL_GAP,
                        Math.max(fontSize, span.getFontSize()) * TABLE_CELL_GAP_FONT_FACTOR
                );
                if (span.getX() - right > threshold) {
                    gaps.add(new float[]{right, span.getX()});
                }
            }
            right = Math.max(right, span.getX() + span.getWidth());
            fontSize = span.getFontSize();
        }

        return gaps;
    }

    private List<float[]> intersectGaps(List<float[]> channels, List<float[]> gaps) {
        List<float[]> result = new ArrayList<>();
        for (float[] channel : channels) {
            for (float[] gap : gaps) {
                float left = Math.max(channel[0], gap[0]);
                float right = Math.min(channel[1], gap[1]);
                if (right - left >= TABLE_MIN_CHANNEL_WIDTH) {
                    result.add(new float[]{left, right});
                }
            }
        }
        return result;
    }

    private TableRegion toTableRegion(int pageIndex, List<List<TextSpan>> rows, int columnCount) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (List<TextSpan> row : rows) {
            for (TextSpan span : row) {
                minX = Math.min(minX, span.getX());
                maxX = Math.max(maxX, span.getX() + span.getWidth());
                minY = Math.min(minY, span.getY());
                maxY = Math.max(maxY, span.getY() + span.getHeight());
            }
        }

        return new TableRegion(
                pageIndex,
                minX,
                minY,
                Math.max(0f, maxX - minX),
                Math.max(0f, maxY - minY),
                rows.size(),
                columnCount
        );
    }
}
