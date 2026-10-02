package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.PdfExtractionServiceImpl;
import com.amalitech.backend.service.impl.StructureRecoveryServiceImpl;
import com.amalitech.backend.service.impl.WordWriterServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageRecoveryIntegrationTest {

    private final PdfExtractionServiceImpl extractionService =
            new PdfExtractionServiceImpl(new StructureRecoveryServiceImpl());

    private final WordWriterServiceImpl wordWriterService = new WordWriterServiceImpl();

    // --- CTM decomposition correctness (extraction layer) -----------------------------

    @Test
    void decomposesAxisAlignedPlacementWithoutInflatingBounds() throws Exception {
        byte[] pdfBytes = buildPdfWithMatrixPlacedImage(
                400, 400,
                new Matrix(100, 0, 0, 50, 50, 300)
        );

        ExtractedImage image = extractSingleImage(pdfBytes);

        assertThat(image.getWidth()).isCloseTo(100f, offsetFloat());
        assertThat(image.getHeight()).isCloseTo(50f, offsetFloat());
        assertThat(image.getX()).isCloseTo(50f, offsetFloat());
        assertThat(image.getY()).isCloseTo(300f, offsetFloat());
        assertThat(image.getRotationDegrees()).isCloseTo(0f, offsetFloat());
        assertThat(image.isFlipHorizontal()).isFalse();
    }

    @Test
    void decomposesNinetyDegreeRotatedImageWithoutBoundingBoxInflation() throws Exception {
        // u=(0,100) -> width 100 rotated 90 CCW, v=(-50,0) -> height 50, centered at (200,200).
        byte[] pdfBytes = buildPdfWithMatrixPlacedImage(
                400, 400,
                new Matrix(0, 100, -50, 0, 225, 150)
        );

        ExtractedImage image = extractSingleImage(pdfBytes);

        assertThat(image.getWidth()).isCloseTo(100f, offsetFloat());
        assertThat(image.getHeight()).isCloseTo(50f, offsetFloat());
        assertThat(image.getRotationDegrees()).isCloseTo(90f, offsetFloat());
        // Center must stay (200,200): x = centerX - width/2, y = centerY - height/2.
        assertThat(image.getX()).isCloseTo(150f, offsetFloat());
        assertThat(image.getY()).isCloseTo(175f, offsetFloat());
        assertThat(image.isFlipHorizontal()).isFalse();
    }

    @Test
    void detectsMirroredImageViaNegativeDeterminant() throws Exception {
        // a<0, d>0, b=c=0 => pure horizontal mirror, determinant negative.
        byte[] pdfBytes = buildPdfWithMatrixPlacedImage(
                400, 400,
                new Matrix(-100, 0, 0, 50, 150, 175)
        );

        ExtractedImage image = extractSingleImage(pdfBytes);

        assertThat(image.isFlipHorizontal()).isTrue();
        assertThat(image.getRotationDegrees()).isCloseTo(0f, offsetFloat());
        assertThat(image.getWidth()).isCloseTo(100f, offsetFloat());
        assertThat(image.getHeight()).isCloseTo(50f, offsetFloat());
    }

    @Test
    void capturedImageBytesAreDecodableAndMatchIntrinsicPixelSize() throws Exception {
        byte[] pdfBytes = buildPdfWithMatrixPlacedImage(
                200, 200,
                new Matrix(80, 0, 0, 40, 10, 10)
        );

        ExtractedImage image = extractSingleImage(pdfBytes);

        assertThat(image.getData()).isNotNull().isNotEmpty();

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.getData()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(image.getPixelsWidth());
        assertThat(decoded.getHeight()).isEqualTo(image.getPixelsHeight());
    }

    // --- Writer: images become absolutely-positioned, page-anchored drawings ----------

    @Test
    void writesSingleImageAsPageAnchoredFloatingPicture() throws Exception {
        byte[] pdfBytes = buildPdfWithMatrixPlacedImage(
                400, 800,
                new Matrix(100, 0, 0, 50, 50, 700)
        );

        byte[] docx = convertToDocx(pdfBytes);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(document.getAllPictures()).hasSize(1);

            String xml = document.getDocument().xmlText();
            assertThat(xml).contains("wp:anchor");
            assertThat(xml).contains("relativeFrom=\"page\"");
            assertThat(xml).contains("<wp:wrapNone/>");
        }
    }

    @Test
    void writesMultipleImagesAtDistinctRelativePositions() throws Exception {
        byte[] pdfBytes = buildPdfWithTwoImages();

        byte[] docx = convertToDocx(pdfBytes);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(document.getAllPictures()).hasSize(2);

            List<Long> offsetsX = extractPosOffsetsX(document);
            assertThat(offsetsX).hasSize(2);
            assertThat(offsetsX.get(0)).isNotEqualTo(offsetsX.get(1));
        }
    }

    @Test
    void imageSurvivesAlongsideTextOnSamePage() throws Exception {
        byte[] pdfBytes = buildPdfWithImageAndText();

        byte[] docx = convertToDocx(pdfBytes);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(document.getAllPictures()).hasSize(1);

            String fullText = document.getParagraphs().stream()
                    .map(p -> p.getText())
                    .reduce("", String::concat);

            assertThat(fullText).contains("Caption above image");
        }
    }

    @Test
    void insertsPageBreakBetweenMultiPagePdfPages() throws Exception {
        byte[] pdfBytes = buildTwoPagePdfEachWithAnImage();

        byte[] docx = convertToDocx(pdfBytes);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(countAnchors(document)).isEqualTo(2);

            boolean hasPageBreak = document.getParagraphs().stream()
                    .flatMap(p -> p.getRuns().stream())
                    .anyMatch(run -> run.getCTR().getBrList().stream()
                            .anyMatch(br -> br.isSetType()
                                    && br.getType().toString().equals("page")));

            assertThat(hasPageBreak).isTrue();
        }
    }

    @Test
    void handlesRealWorldCroppedRotatedScaledPdfWithoutCrashing() throws Exception {
        Path path = Path.of(
                "src/test/resources/sample-files-main/027-cropped-rotated-scaled/cropped-rotated-scaled.pdf"
        );
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(path));

        byte[] pdfBytes = Files.readAllBytes(path);
        PdfExtractionResult result = extractionService.extract(pdfBytes);

        // Pages are rotated 0/90/180/270 in source order (see create.py).
        int[] expectedRotations = {0, 90, 180, 270};
        for (int i = 0; i < result.getPages().size() && i < expectedRotations.length; i++) {
            assertThat(result.getPages().get(i).getRotation())
                    .isEqualTo(expectedRotations[i]);
        }

        int totalImages = result.getPages().stream()
                .mapToInt(p -> p.getImages().size())
                .sum();
        assertThat(totalImages).isGreaterThan(0);

        byte[] docx = wordWriterService.write(result);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(countAnchors(document)).isEqualTo(totalImages);
        }
    }

    // --- Corpus smoke tests: real-world encodings must not crash and must round-trip --

    @Test
    void corpusImagePdfsExtractDecodableImagesAndProduceValidDocx() {
        for (Path pdfPath : corpusImagePdfs()) {
            try {
                byte[] pdfBytes = Files.readAllBytes(pdfPath);
                PdfExtractionResult result = extractionService.extract(pdfBytes);

                int totalImages = result.getPages().stream()
                        .mapToInt(p -> p.getImages().size())
                        .sum();

                for (PageExtraction page : result.getPages()) {
                    for (ExtractedImage image : page.getImages()) {
                        assertThat(image.getData())
                                .as("image bytes for %s", pdfPath)
                                .isNotNull()
                                .isNotEmpty();

                        BufferedImage decoded = ImageIO.read(
                                new ByteArrayInputStream(image.getData())
                        );

                        assertThat(decoded)
                                .as("decodable PNG for %s", pdfPath)
                                .isNotNull();
                    }
                }

                byte[] docx = wordWriterService.write(result);

                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
                    // Anchor count (not getAllPictures().size()): POI deduplicates picture
                    // parts with identical byte content, so visually-identical images across
                    // different source filters can legitimately share one underlying part
                    // while still each getting their own positioned anchor.
                    assertThat(countAnchors(document))
                            .as("anchored image count for %s", pdfPath)
                            .isEqualTo(totalImages);
                }

            } catch (Exception e) {
                throw new AssertionError("Failed processing " + pdfPath, e);
            }
        }
    }

    // --- helpers ------------------------------------------------------------------

    private static org.assertj.core.data.Offset<Float> offsetFloat() {
        return org.assertj.core.data.Offset.offset(0.01f);
    }

    private ExtractedImage extractSingleImage(byte[] pdfBytes) throws IOException {
        PdfExtractionResult result = extractionService.extract(pdfBytes);
        List<ExtractedImage> images = result.getPages().getFirst().getImages();
        assertThat(images).hasSize(1);
        return images.getFirst();
    }

    private byte[] convertToDocx(byte[] pdfBytes) throws IOException {
        PdfExtractionResult result = extractionService.extract(pdfBytes);
        return wordWriterService.write(result);
    }

    private int countAnchors(XWPFDocument document) {
        String xml = document.getDocument().xmlText();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<wp:anchor[ >]")
                .matcher(xml);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private List<Long> extractPosOffsetsX(XWPFDocument document) {
        String xml = document.getDocument().xmlText();
        List<Long> offsets = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("positionH relativeFrom=\"page\"><wp:posOffset>(-?\\d+)</wp:posOffset>")
                .matcher(xml);
        while (matcher.find()) {
            offsets.add(Long.parseLong(matcher.group(1)));
        }
        return offsets;
    }

    private List<Path> corpusImagePdfs() {
        Path root = Path.of(
                "src/test/resources/sample-files-main"
        );

        String[] relativePaths = {
                "003-pdflatex-image/pdflatex-image.pdf",
                "007-imagemagick-images/imagemagick-images.pdf",
                "007-imagemagick-images/imagemagick-lzw.pdf",
                "008-reportlab-inline-image/inline-image.pdf",
                "018-base64-image/base64image.pdf",
                "019-grayscale-image/grayscale-image.pdf",
                "023-cmyk-image/cmyk-image.pdf",
                "028-image-references-deduplication/wrong-references.pdf"
        };

        return Stream.of(relativePaths)
                .map(root::resolve)
                .filter(Files::isRegularFile)
                .toList();
    }

    private PDImageXObject sampleImage(PDDocument document, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.WHITE);
        graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", stream);
        return PDImageXObject.createFromByteArray(document, stream.toByteArray(), "sample");
    }

    private byte[] buildPdfWithMatrixPlacedImage(float pageWidth, float pageHeight, Matrix matrix)
            throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(sampleImage(document, 100, 50), matrix);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPdfWithTwoImages() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(sampleImage(document, 80, 40), 50, 700, 80, 40);
                content.drawImage(sampleImage(document, 120, 60), 350, 300, 120, 60);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPdfWithImageAndText() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(80, 760);
                content.showText("Caption above image");
                content.endText();

                content.drawImage(sampleImage(document, 150, 75), 80, 600, 150, 75);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildTwoPagePdfEachWithAnImage() throws IOException {
        try (PDDocument document = new PDDocument()) {
            // Different pixel dimensions per page so the re-encoded PNG bytes differ -
            // POI deduplicates identical picture data across addPictureData() calls, which
            // would otherwise collapse getAllPictures() below the actual anchor count.
            int[] sizes = {100, 140};

            for (int size : sizes) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(sampleImage(document, size, size / 2), 80, 600, size, size / 2);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
