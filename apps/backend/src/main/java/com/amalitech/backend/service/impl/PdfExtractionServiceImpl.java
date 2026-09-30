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

    private List<DetectionCell> reconstructDetectionCells(
            List<TextSpan> row
    ) {

        List<DetectionCell> cells =
                new ArrayList<>();

        if (row == null || row.isEmpty()) {
            return cells;
        }

        List<TextSpan> sorted =
                new ArrayList<>(row);

        sorted.sort(
                Comparator.comparing(TextSpan::getX)
        );

        StringBuilder currentText =
                new StringBuilder();

        float currentLeft = sorted.getFirst().getX();
        float currentRight =
                sorted.getFirst().getX()
                        + sorted.getFirst().getWidth();

        float previousFontSize =
                sorted.getFirst().getFontSize();

        currentText.append(
                sorted.getFirst().getText()
        );

        for (int i = 1; i < sorted.size(); i++) {

            TextSpan previous =
                    sorted.get(i - 1);

            TextSpan current =
                    sorted.get(i);

            float previousRight =
                    previous.getX()
                            + previous.getWidth();

            float gap =
                    current.getX()
                            - previousRight;

            float threshold =
                    Math.max(
                            TABLE_MIN_CELL_GAP,
                            Math.max(
                                    previousFontSize,
                                    current.getFontSize()
                            ) * TABLE_CELL_GAP_FONT_FACTOR
                    );

            if (gap > threshold) {

                cells.add(
                        new DetectionCell(
                                currentText.toString(),
                                currentLeft,
                                Math.max(
                                        0f,
                                        currentRight - currentLeft
                                )
                        )
                );

                currentText =
                        new StringBuilder();

                currentLeft =
                        current.getX();

                currentRight =
                        current.getX()
                                + current.getWidth();
            }

            currentText.append(
                    current.getText()
            );

            currentRight =
                    Math.max(
                            currentRight,
                            current.getX()
                                    + current.getWidth()
                    );

            previousFontSize =
                    current.getFontSize();
        }

        cells.add(
                new DetectionCell(
                        currentText.toString(),
                        currentLeft,
                        Math.max(
                                0f,
                                currentRight - currentLeft
                        )
                )
        );

        return cells;
    }

    private boolean isWhitespaceOnlyRun(
            List<TextPosition> positions
    ) {

        if (positions == null
                || positions.isEmpty()) {
            return false;
        }

        for (TextPosition position : positions) {

            String unicode =
                    position.getUnicode();

            if (unicode != null
                    && !unicode.isBlank()) {
                return false;
            }
        }

        return true;
    }

    private boolean isBoldFont(String fontName) {
        if (fontName == null) {
            return false;
        }

        String normalized = fontName.toLowerCase();

        return normalized.contains("bold")
                || normalized.contains("black")
                || normalized.contains("heavy");
    }

    private boolean isBold(TextPosition position) {

        if (position.getFont() != null
                && position.getFont().getFontDescriptor() != null
                && position.getFont()
                .getFontDescriptor()
                .isForceBold()) {

            return true;
        }

        return isBoldFont(
                getFontName(position)
        );
    }

    private boolean isItalic(TextPosition position) {

        if (position.getFont() != null
                && position.getFont().getFontDescriptor() != null
                && position.getFont()
                .getFontDescriptor()
                .isItalic()) {

            return true;
        }

        return isItalicFont(
                getFontName(position)
        );
    }

    private boolean isItalicFont(String fontName) {
        if (fontName == null) {
            return false;
        }

        String normalized = fontName.toLowerCase();

        return normalized.contains("italic")
                || normalized.contains("oblique");
    }


    private boolean hasSameFormatting(
            TextPosition first,
            TextPosition second
    ) {
        String firstFont = getFontName(first);
        String secondFont = getFontName(second);

        boolean sameFont =
                firstFont.equals(secondFont);

        boolean sameSize =
                Math.abs(
                        first.getFontSizeInPt()
                                - second.getFontSizeInPt()
                ) < 0.01f;

        return sameFont && sameSize;
    }

    private String getFontName(TextPosition position) {
        if (position.getFont() == null
                || position.getFont().getName() == null) {
            return "unknown";
        }

        return position.getFont().getName();
    }

    private boolean addTextSpanFromRun(
            List<TextSpan> spans,
            int pageIndex,
            List<TextPosition> positions,
            TextPosition previousPositionBeforeRun,
            boolean forceWordSeparatorBefore
    ) {

        if (positions == null || positions.isEmpty()) {
            return false;
        }

        boolean wordSeparatorBefore =
                forceWordSeparatorBefore
                        || (
                        previousPositionBeforeRun != null
                                && needsWordSeparator(
                                previousPositionBeforeRun,
                                positions.getFirst()
                        )
                );

        StringBuilder textBuilder =
                new StringBuilder();

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        TextPosition previousPosition = null;

        for (TextPosition position : positions) {

            String unicode =
                    position.getUnicode();

            if (unicode != null) {

                if (needsWordSeparator(
                        previousPosition,
                        position
                )) {
                    textBuilder.append(' ');
                }

                textBuilder.append(unicode);
            }

            minX = Math.min(
                    minX,
                    position.getXDirAdj()
            );

            maxX = Math.max(
                    maxX,
                    position.getXDirAdj()
                            + position.getWidthDirAdj()
            );

            minY = Math.min(
                    minY,
                    position.getYDirAdj()
            );

            maxY = Math.max(
                    maxY,
                    position.getYDirAdj()
                            + position.getHeightDir()
            );

            previousPosition = position;
        }

        String extractedText =
                textBuilder.toString();

        if (extractedText.isBlank()
                || minX == Float.MAX_VALUE) {
            return false;
        }

        TextPosition first =
                positions.getFirst();

        spans.add(
                new TextSpan(
                        pageIndex,
                        extractedText,
                        minX,
                        minY,
                        Math.max(
                                0f,
                                maxX - minX
                        ),
                        Math.max(
                                0f,
                                maxY - minY
                        ),
                        getFontName(first),
                        first.getFontSizeInPt(),
                        isBold(first),
                        isItalic(first),

                        // TODO: Implement underline detection and recovery
                        false,
                        wordSeparatorBefore
                )
        );

        return true;
    }

    private boolean looksLikeTwoColumnTable(
            List<List<TextSpan>> rows
    ) {

        if (rows.size() < MIN_TWO_COLUMN_TABLE_ROWS) {
            return false;
        }

        Float expectedLeftX = null;
        Float expectedRightX = null;

        int alignedRows = 0;
        int tabularRows = 0;

        for (List<TextSpan> row : rows) {

            List<DetectionCell> cells =
                    reconstructDetectionCells(row);

            if (cells.size() != 2) {
                continue;
            }

            DetectionCell left =
                    cells.get(0);

            DetectionCell right =
                    cells.get(1);

            if (expectedLeftX == null) {
                expectedLeftX = left.x();
                expectedRightX = right.x();
            }

            boolean leftAligned =
                    Math.abs(
                            left.x()
                                    - expectedLeftX
                    ) <= COLUMN_ALIGNMENT_TOLERANCE;

            boolean rightAligned =
                    Math.abs(
                            right.x()
                                    - expectedRightX
                    ) <= COLUMN_ALIGNMENT_TOLERANCE;

            if (!leftAligned || !rightAligned) {
                continue;
            }

            alignedRows++;

            if (looksLikeTabularValue(left.text())
                    || looksLikeTabularValue(right.text())) {
                tabularRows++;
            }
        }

        return alignedRows >= MIN_TWO_COLUMN_TABLE_ROWS
                && tabularRows
                >= MIN_TWO_COLUMN_TABLE_ROWS - 1;
    }

    private boolean looksLikeMultiColumnTable(
            List<List<TextSpan>> rows
    ) {

        if (rows.size() < MIN_TABLE_ROWS) {
            return false;
        }

        int tabularRows = 0;

        for (List<TextSpan> row : rows) {

            List<DetectionCell> cells =
                    reconstructDetectionCells(row);

            boolean hasTabularCell = false;

            for (DetectionCell cell : cells) {

                if (looksLikeTabularValue(
                        cell.text()
                )) {
                    hasTabularCell = true;
                    break;
                }
            }

            if (hasTabularCell) {
                tabularRows++;
            }
        }

        return tabularRows >= MIN_TABLE_ROWS - 1;
    }

    private boolean looksLikeTabularValue(String text) {
        if (text == null) {
            return false;
        }

        String value = text.trim();

        if (value.isEmpty()) {
            return false;
        }

        return value.matches(
                "^[\\p{Sc}]?\\d[\\d,]*(?:\\.\\d+)?%?$"
                        + "|^\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?$"
                        + "|^[A-Z0-9_-]{1,12}$"
        );
    }

    private List<TextSpan> extractTextSpans(
            int pageIndex,
            PDPage page
    ) throws IOException {

        final List<TextSpan> spans = new ArrayList<>();

        PDFTextStripper stripper = new PDFTextStripper() {

            private TextPosition lastTextPosition;
            private boolean pendingWhitespaceSeparator;

            {
                this.output = new StringWriter();
            }

            @Override
            protected void writeString(
                    String string,
                    List<TextPosition> textPositions
            ) {

                if (textPositions == null
                        || textPositions.isEmpty()) {
                    return;
                }

                List<TextPosition> currentRun =
                        new ArrayList<>();

                TextPosition previousPositionBeforeRun =
                        lastTextPosition;

                for (TextPosition position : textPositions) {

                    if (currentRun.isEmpty()) {
                        currentRun.add(position);
                        continue;
                    }

                    TextPosition previous =
                            currentRun.getLast();

                    if (hasSameFormatting(
                            previous,
                            position
                    )) {

                        currentRun.add(position);

                    } else {

                        boolean whitespaceOnly =
                                isWhitespaceOnlyRun(
                                        currentRun
                                );

                        boolean added =
                                addTextSpanFromRun(
                                        spans,
                                        pageIndex,
                                        currentRun,
                                        previousPositionBeforeRun,
                                        pendingWhitespaceSeparator
                                );

                        if (whitespaceOnly) {
                            pendingWhitespaceSeparator = true;
                        } else if (added) {
                            pendingWhitespaceSeparator = false;
                        }

                        previousPositionBeforeRun =
                                currentRun.getLast();

                        currentRun =
                                new ArrayList<>();

                        currentRun.add(position);
                    }
                }

                if (!currentRun.isEmpty()) {

                    boolean whitespaceOnly =
                            isWhitespaceOnlyRun(
                                    currentRun
                            );

                    boolean added =
                            addTextSpanFromRun(
                                    spans,
                                    pageIndex,
                                    currentRun,
                                    previousPositionBeforeRun,
                                    pendingWhitespaceSeparator
                            );

                    if (whitespaceOnly) {
                        pendingWhitespaceSeparator = true;
                    } else if (added) {
                        pendingWhitespaceSeparator = false;
                    }
                }

                lastTextPosition =
                        textPositions.getLast();
            }
        @Override
        protected void writeLineSeparator()
        throws IOException {

            lastTextPosition = null;
            pendingWhitespaceSeparator = false;

            super.writeLineSeparator();
        }
        };

        stripper.setSortByPosition(true);
        stripper.processPage(page);

        return spans;
    }


    private List<ExtractedImage> extractImages(int pageIndex, PDPage page) throws IOException {
        List<ExtractedImage> images = new ArrayList<>();
        new ImageLocationStreamEngine(pageIndex, images).processPage(page);
        return images;
    }

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
                    || !(operands.getFirst() instanceof COSName objectName)) {
                super.processOperator(operator, operands);
                return;
            }

            PDXObject xObject = getResources().getXObject(objectName);
            switch (xObject) {
                case null -> {

                }
                case PDImageXObject image -> recordImagePlacement(objectName.getName(), image);
                case PDFormXObject form -> {
                    COSBase formKey = form.getCOSObject();
                    formsInProgress.push(formKey);
                    try {
                        showForm(form);
                    } finally {
                        formsInProgress.pop();
                    }
                }
                default -> {
                }
            }

        }


        private void recordImagePlacement(String imageName, PDImageXObject image) {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();

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
            TextPosition current
    ) {
        if (previous == null
                || current == null
                || previous.getUnicode() == null
                || previous.getUnicode().isEmpty()
                || current.getUnicode() == null
                || current.getUnicode().isBlank()) {
            return false;
        }

        String previousUnicode = previous.getUnicode();

        if (Character.isWhitespace(
                previousUnicode.charAt(previousUnicode.length() - 1)
        )) {
            return false;
        }

        float gap =
                current.getXDirAdj()
                        - (previous.getXDirAdj()
                        + previous.getWidthDirAdj());

        float spaceWidth =
                Math.max(
                        previous.getWidthOfSpace(),
                        current.getWidthOfSpace()
                );

        return gap > Math.max(
                1f,
                spaceWidth * 0.5f
        );
    }


    private List<TableRegion> detectCandidateTableRegions(
            int pageIndex,
            List<TextSpan> textSpans
    ) {
        List<TableRegion> regions = new ArrayList<>();

        if (textSpans.size() < 3) {
            return regions;
        }

        List<List<TextSpan>> rows = groupIntoRows(textSpans);

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
                    && channels.size() >= MIN_TABLE_CHANNELS
                    && looksLikeMultiColumnTable(rows.subList(start, end))) {

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


        for (int rowStart = 0; rowStart < rows.size(); rowStart++) {
            int rowEnd = rowStart;

            while (rowEnd < rows.size()
                    && reconstructDetectionCells(
                    rows.get(rowEnd)
            ).size() == 2) {

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
