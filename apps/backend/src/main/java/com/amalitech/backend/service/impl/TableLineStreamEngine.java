package com.amalitech.backend.service.impl;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

class TableLineStreamEngine extends PDFGraphicsStreamEngine {

    private static final float AXIS_TOLERANCE = 0.75f;
    private static final float MIN_LINE_LENGTH = 4f;
    private static final float PAGE_EDGE_TOLERANCE = 1.5f;
    private static final float MAX_FILL_LINE_THICKNESS = 6f;

    private final float pageWidth;
    private final float pageHeight;
    private final List<HorizontalLine> horizontalLines = new ArrayList<>();
    private final List<VerticalLine> verticalLines = new ArrayList<>();

    private GeneralPath currentPath = new GeneralPath();

    TableLineStreamEngine(PDPage page) {
        super(page);
        this.pageWidth = page.getCropBox().getWidth();
        this.pageHeight = page.getCropBox().getHeight();
    }

    List<HorizontalLine> getHorizontalLines() {
        return horizontalLines;
    }

    List<VerticalLine> getVerticalLines() {
        return verticalLines;
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        currentPath.moveTo(p0.getX(), p0.getY());
        currentPath.lineTo(p1.getX(), p1.getY());
        currentPath.lineTo(p2.getX(), p2.getY());
        currentPath.lineTo(p3.getX(), p3.getY());
        currentPath.closePath();
    }

    @Override
    public void drawImage(PDImage pdImage) {
        // Images are not part of table border geometry.
    }

    @Override
    public void clip(int windingRule) {
        // Clipping paths do not represent visible borders.
    }

    @Override
    public void moveTo(float x, float y) {
        currentPath.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        currentPath.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        currentPath.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
        return currentPath.getCurrentPoint();
    }

    @Override
    public void closePath() {
        currentPath.closePath();
    }

    @Override
    public void endPath() {
        currentPath = new GeneralPath();
    }

    @Override
    public void strokePath() {
        extractLines(currentPath);
        currentPath = new GeneralPath();
    }

    @Override
    public void fillPath(int windingRule) {
        extractThinFillLine(currentPath);
        currentPath = new GeneralPath();
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        if (!extractThinFillLine(currentPath)) {
            extractLines(currentPath);
        }

        currentPath = new GeneralPath();
    }


    private boolean extractThinFillLine(GeneralPath path) {
        Rectangle2D bounds = path.getBounds2D();

        float width = (float) bounds.getWidth();
        float height = (float) bounds.getHeight();

        boolean thinHorizontalBar =
                height <= MAX_FILL_LINE_THICKNESS
                        && width >= MIN_LINE_LENGTH
                        && width > height;

        boolean thinVerticalBar =
                width <= MAX_FILL_LINE_THICKNESS
                        && height >= MIN_LINE_LENGTH
                        && height > width;

        if (thinHorizontalBar) {
            float y = flipY(bounds.getCenterY());

            if (!isPageEdge(y, 0f, pageHeight)) {
                horizontalLines.add(
                        new HorizontalLine(
                                y,
                                (float) bounds.getMinX(),
                                (float) bounds.getMaxX()
                        )
                );
            }

            return true;
        }

        if (thinVerticalBar) {
            float x = (float) bounds.getCenterX();

            if (!isPageEdge(x, 0f, pageWidth)) {
                verticalLines.add(
                        new VerticalLine(
                                x,
                                flipY(bounds.getMaxY()),
                                flipY(bounds.getMinY())
                        )
                );
            }

            return true;
        }

        return false;
    }

    @Override
    public void shadingFill(COSName shadingName) {
        // Shading fills do not represent table borders.
    }

    private float flipY(double y) {
        return (float) (pageHeight - y);
    }

    private void extractLines(GeneralPath path) {
        PathIterator iterator = path.getPathIterator(null);
        double[] coords = new double[6];

        double startX = 0;
        double startY = 0;
        double currentX = 0;
        double currentY = 0;

        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);

            switch (type) {
                case PathIterator.SEG_MOVETO -> {
                    startX = currentX = coords[0];
                    startY = currentY = coords[1];
                }
                case PathIterator.SEG_LINETO -> {
                    addSegment(currentX, currentY, coords[0], coords[1]);
                    currentX = coords[0];
                    currentY = coords[1];
                }
                case PathIterator.SEG_CLOSE -> {
                    addSegment(currentX, currentY, startX, startY);
                    currentX = startX;
                    currentY = startY;
                }
                default -> {
                    currentX = coords[0];
                    currentY = coords[1];
                }
            }

            iterator.next();
        }
    }

    private void addSegment(double x1, double y1, double x2, double y2) {
        float fx1 = (float) x1;
        float fx2 = (float) x2;
        float fy1 = flipY(y1);
        float fy2 = flipY(y2);

        float dx = Math.abs(fx2 - fx1);
        float dy = Math.abs(fy2 - fy1);

        if (dy <= AXIS_TOLERANCE && dx >= MIN_LINE_LENGTH) {

            float y = (fy1 + fy2) / 2f;

            if (isPageEdge(y, 0f, pageHeight)) {
                return;
            }

            horizontalLines.add(
                    new HorizontalLine(
                            y,
                            Math.min(fx1, fx2),
                            Math.max(fx1, fx2)
                    )
            );
        } else if (dx <= AXIS_TOLERANCE && dy >= MIN_LINE_LENGTH) {

            float x = (fx1 + fx2) / 2f;

            if (isPageEdge(x, 0f, pageWidth)) {
                return;
            }

            verticalLines.add(
                    new VerticalLine(
                            x,
                            Math.min(fy1, fy2),
                            Math.max(fy1, fy2)
                    )
            );
        }
    }

    private boolean isPageEdge(float position, float low, float high) {
        return Math.abs(position - low) <= PAGE_EDGE_TOLERANCE
                || Math.abs(position - high) <= PAGE_EDGE_TOLERANCE;
    }
}
