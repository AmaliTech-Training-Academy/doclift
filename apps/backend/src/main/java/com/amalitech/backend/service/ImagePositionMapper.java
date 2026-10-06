package com.amalitech.backend.service;

public final class ImagePositionMapper {

    private static final double EMU_PER_POINT = 12700.0;
    private static final double OOXML_UNITS_PER_DEGREE = 60000.0;
    private static final double FULL_TURN_OOXML_UNITS = 360.0 * OOXML_UNITS_PER_DEGREE;

    private ImagePositionMapper() {
    }

    public record Placement(
            long offsetXEmu,
            long offsetYEmu,
            long extentXEmu,
            long extentYEmu,
            int rotation60000ths,
            boolean flipHorizontal
    ) {
    }

    public static Placement map(ExtractedImage image, PageExtraction page) {
        float cropWidth = page.getCropWidth() > 0 ? page.getCropWidth() : 612f;
        float cropHeight = page.getCropHeight() > 0 ? page.getCropHeight() : 792f;
        int rotation = normalizeDegrees(page.getRotation());

        float centerX = image.getX() + image.getWidth() / 2f;
        float centerY = image.getY() + image.getHeight() / 2f;

        double relX = centerX - page.getCropX();
        double relY = centerY - page.getCropY();

        double dx = relX - cropWidth / 2.0;
        double dy = relY - cropHeight / 2.0;

        double radians = Math.toRadians(rotation);
        double rotatedDx = dx * Math.cos(radians) + dy * Math.sin(radians);
        double rotatedDy = -dx * Math.sin(radians) + dy * Math.cos(radians);

        boolean swapped = rotation == 90 || rotation == 270;
        double visualPageWidth = swapped ? cropHeight : cropWidth;
        double visualPageHeight = swapped ? cropWidth : cropHeight;

        double visualCenterX = visualPageWidth / 2.0 + rotatedDx;
        double visualCenterYBottomUp = visualPageHeight / 2.0 + rotatedDy;

        double docxCenterX = visualCenterX;
        double docxCenterY = visualPageHeight - visualCenterYBottomUp;

        double docxTopLeftX = docxCenterX - image.getWidth() / 2.0;
        double docxTopLeftY = docxCenterY - image.getHeight() / 2.0;

        double totalClockwiseDegrees = rotation - image.getRotationDegrees();
        int rotation60000ths = (int) Math.round(
                normalizeOoxmlUnits(totalClockwiseDegrees * OOXML_UNITS_PER_DEGREE)
        );

        return new Placement(
                Math.round(docxTopLeftX * EMU_PER_POINT),
                Math.round(docxTopLeftY * EMU_PER_POINT),
                Math.round(image.getWidth() * EMU_PER_POINT),
                Math.round(image.getHeight() * EMU_PER_POINT),
                rotation60000ths,
                image.isFlipHorizontal()
        );
    }

    private static int normalizeDegrees(int degrees) {
        int normalized = degrees % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }

    private static double normalizeOoxmlUnits(double units) {
        double normalized = units % FULL_TURN_OOXML_UNITS;
        return normalized < 0 ? normalized + FULL_TURN_OOXML_UNITS : normalized;
    }
}
