package com.amalitech.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePositionMapperTest {

    private static final double EMU_PER_POINT = 12700.0;

    private ExtractedImage image(
            float x, float y, float width, float height,
            float rotationDegrees, boolean flipHorizontal
    ) {
        return new ExtractedImage(
                0, "img", x, y, width, height,
                100, 100, "png", rotationDegrees, flipHorizontal, new byte[]{1}
        );
    }

    private PageExtraction page(
            float cropX, float cropY, float cropWidth, float cropHeight, int rotation
    ) {
        PageExtraction page = new PageExtraction(0);
        page.setCropX(cropX);
        page.setCropY(cropY);
        page.setCropWidth(cropWidth);
        page.setCropHeight(cropHeight);
        page.setRotation(rotation);
        return page;
    }

    @Test
    void unrotatedPageFlipsYAndConvertsToEmu() {
        // 400x800pt page, image is a 100x50 box whose bottom-left PDF corner is (50,700),
        // i.e. its top edge sits 50pt below the top of the page and 50pt from the left.
        PageExtraction page = page(0, 0, 400, 800, 0);
        ExtractedImage image = image(50, 700, 100, 50, 0, false);

        ImagePositionMapper.Placement placement = ImagePositionMapper.map(image, page);

        assertThat(placement.offsetXEmu()).isEqualTo(Math.round(50 * EMU_PER_POINT));
        assertThat(placement.offsetYEmu()).isEqualTo(Math.round(50 * EMU_PER_POINT));
        assertThat(placement.extentXEmu()).isEqualTo(Math.round(100 * EMU_PER_POINT));
        assertThat(placement.extentYEmu()).isEqualTo(Math.round(50 * EMU_PER_POINT));
        assertThat(placement.rotation60000ths()).isZero();
        assertThat(placement.flipHorizontal()).isFalse();
    }

    @Test
    void cropBoxOffsetIsSubtractedBeforeFlipping() {
        // Same geometry as above, but the visible page is cropped starting at (10,10)
        // in raw PDF space, so everything shifts left/down by 10pt relative to the crop.
        PageExtraction page = page(10, 10, 400, 800, 0);
        ExtractedImage image = image(60, 710, 100, 50, 0, false);

        ImagePositionMapper.Placement placement = ImagePositionMapper.map(image, page);

        assertThat(placement.offsetXEmu()).isEqualTo(Math.round(50 * EMU_PER_POINT));
        assertThat(placement.offsetYEmu()).isEqualTo(Math.round(50 * EMU_PER_POINT));
    }

    @Test
    void ninetyDegreePageRotationMovesTopLeftContentToTopRight() {
        // Rotating a page 90deg clockwise sends its original top-left corner to the new
        // page's top-right corner (physically verified: grab the top-left corner of a
        // sheet and spin it clockwise - it ends up at the top-right).
        PageExtraction page = page(0, 0, 400, 800, 90);

        // A small image pinned to the original top-left area (near PDF x=0, y=page top).
        ExtractedImage image = image(0, 790, 10, 10, 0, false);

        ImagePositionMapper.Placement placement = ImagePositionMapper.map(image, page);

        // New (rotated) page is 800x400. Top-right means offsetX near (800-10)pt and
        // offsetY near 0.
        long expectedX = Math.round((800 - 10) * EMU_PER_POINT);
        long expectedY = 0L;

        assertThat(placement.offsetXEmu()).isEqualTo(expectedX);
        assertThat(placement.offsetYEmu()).isEqualTo(expectedY);
    }

    @Test
    void imageOwnRotationFromCtmCombinesWithPageRotation() {
        // CTM decomposed to a 100x50 image rotated 90deg CCW (PDF space) centered at
        // (200,200) on an unrotated 400x400 page.
        PageExtraction page = page(0, 0, 400, 400, 0);
        ExtractedImage image = image(150, 175, 100, 50, 90f, false);

        ImagePositionMapper.Placement placement = ImagePositionMapper.map(image, page);

        // Page rotation 0, image rotation 90 CCW -> OOXML clockwise rotation = 0 - 90 = -90
        // normalized to 270 degrees.
        int expectedRotation = (int) Math.round(270.0 * 60000.0);
        assertThat(placement.rotation60000ths()).isEqualTo(expectedRotation);

        // Center (200,200) on a 400-tall page -> docx center (200, 200); unrotated extent
        // (100x50) is unaffected by rotation (rot attribute handles the visual spin).
        assertThat(placement.offsetXEmu()).isEqualTo(Math.round(150 * EMU_PER_POINT));
        assertThat(placement.offsetYEmu()).isEqualTo(Math.round(175 * EMU_PER_POINT));
        assertThat(placement.extentXEmu()).isEqualTo(Math.round(100 * EMU_PER_POINT));
        assertThat(placement.extentYEmu()).isEqualTo(Math.round(50 * EMU_PER_POINT));
    }

    @Test
    void flipHorizontalIsPassedThrough() {
        PageExtraction page = page(0, 0, 400, 400, 0);
        ExtractedImage image = image(50, 50, 100, 50, 0f, true);

        ImagePositionMapper.Placement placement = ImagePositionMapper.map(image, page);

        assertThat(placement.flipHorizontal()).isTrue();
    }
}
