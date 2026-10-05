package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.BlockType;
import com.amalitech.backend.service.ExtractedImage;
import com.amalitech.backend.service.ImagePositionMapper;
import com.amalitech.backend.service.PageExtraction;
import com.amalitech.backend.service.PdfExtractionResult;
import com.amalitech.backend.service.StructuredBlock;
import com.amalitech.backend.service.TextSpan;
import com.amalitech.backend.service.WordWriterService;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.math.BigInteger;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.List;

@Service
public class WordWriterServiceImpl implements WordWriterService {


    private static final Pattern NUMBERED_PATTERN =
            Pattern.compile(
                    "^\\s*(?:\\d+|[a-z]|[ivx]+|[IVX]{2,}|[A-Z])[.)]\\s*"
            );

    private static final Pattern LIST_MARKER_PATTERN =
            Pattern.compile(
                    "^\\s*(?:[•◦▪‣⁃∙*-]|(?:\\d+|[a-z]|[ivx]+|[IVX]{2,}|[A-Z])[.)])\\s*"
            );

    private boolean isNumberedListItem(String text) {

        if (text == null || text.isBlank()) {
            return false;
        }

        return NUMBERED_PATTERN
                .matcher(text)
                .find();
    }

    private void applyFormatting(
            XWPFRun run,
            TextSpan span
    ) {

        run.setBold(span.isBold());
        run.setItalic(span.isItalic());

        run.setUnderline(
                span.isUnderline()
                        ? UnderlinePatterns.SINGLE
                        : UnderlinePatterns.NONE
        );

        if (span.getFontSize() > 0) {
            run.setFontSize(
                    Math.round(span.getFontSize())
            );
        }
    }



    private boolean hasLaterNonBlankListContent(
            List<TextSpan> spans,
            int startIndex
    ) {

        for (int i = startIndex; i < spans.size(); i++) {

            String text =
                    spans.get(i).getText();

            if (text == null) {
                continue;
            }

            String cleaned =
                    LIST_MARKER_PATTERN
                            .matcher(text)
                            .replaceFirst("");

            if (!cleaned.isBlank()) {
                return true;
            }
        }

        return false;
    }

    private BigInteger createNumbering(
            XWPFDocument document,
            STNumberFormat.Enum format,
            String levelText,
            BigInteger start
    ) {

        XWPFNumbering numbering =
                document.getNumbering();

        if (numbering == null) {
            numbering =
                    document.createNumbering();
        }

        CTAbstractNum abstractNum =
                CTAbstractNum.Factory.newInstance();

        CTLvl level =
                abstractNum.addNewLvl();

        level.setIlvl(
                BigInteger.ZERO
        );

        if (start != null) {
            level.addNewStart()
                    .setVal(start);
        }

        level.addNewNumFmt()
                .setVal(format);

        level.addNewLvlText()
                .setVal(levelText);

        level.addNewLvlJc()
                .setVal(STJc.LEFT);

        BigInteger abstractNumId =
                numbering.addAbstractNum(
                        new XWPFAbstractNum(
                                abstractNum
                        )
                );

        return numbering.addNum(
                abstractNumId
        );
    }

    private BigInteger createBulletNumbering(
            XWPFDocument document
    ) {
        return createNumbering(
                document,
                STNumberFormat.BULLET,
                "•",
                null
        );
    }

    private BigInteger createNumberedNumbering(
            XWPFDocument document
    ) {
        return createNumbering(
                document,
                STNumberFormat.DECIMAL,
                "%1.",
                BigInteger.ONE
        );
    }


    private void writeListRuns(
            XWPFParagraph paragraph,
            StructuredBlock block
    ) {

        if (block.getSpans() == null
                || block.getSpans().isEmpty()) {

            XWPFRun run =
                    paragraph.createRun();

            run.setText(
                    removeListMarker(block.getText())
                            .strip()
            );

            return;
        }

        boolean markerRemoved = false;
        boolean firstContentWritten = false;

        String previousText = null;

        List<TextSpan> spans =
                block.getSpans();

        for (int i = 0; i < spans.size(); i++) {

            TextSpan span =
                    spans.get(i);

            String text =
                    span.getText();

            if (text == null) {
                continue;
            }

            if (!markerRemoved) {

                String cleaned =
                        removeListMarker(text);

                if (!cleaned.equals(text)
                        || LIST_MARKER_PATTERN
                        .matcher(text)
                        .find()) {

                    markerRemoved = true;
                    text = cleaned;
                }
            }

            if (text.isBlank()) {
                continue;
            }

            if (!firstContentWritten) {
                text = text.stripLeading();
            }
            if (!hasLaterNonBlankListContent(
                    spans,
                    i + 1
            )) {
                text = text.stripTrailing();
            }

            if (text.isEmpty()) {
                continue;
            }

            if (span.isWordSeparatorBefore()
                    && previousText != null
                    && !previousText.isEmpty()
                    && !Character.isWhitespace(
                    previousText.charAt(
                            previousText.length() - 1
                    )
            )
                    && !Character.isWhitespace(
                    text.charAt(0)
            )) {

                text = " " + text;
            }

            XWPFRun run =
                    paragraph.createRun();

            run.setText(text);

            applyFormatting(run, span);

            previousText = text;
            firstContentWritten = true;
        }
    }

    private String removeListMarker(String text) {

        if (text == null) {
            return "";
        }

        return LIST_MARKER_PATTERN
                .matcher(text)
                .replaceFirst("");
    }

    @Override
    public byte[] write(PdfExtractionResult extractionResult) {

        if (extractionResult == null) {
            throw new IllegalArgumentException(
                    "Extraction result cannot be null."
            );
        }

        try (
                XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            BigInteger bulletNumId =
                    createBulletNumbering(document);

            BigInteger numberedNumId =
                    createNumberedNumbering(document);

            List<PageExtraction> pages =
                    extractionResult.getPages();

            int shapeId = 1;

            for (int pageNumber = 0;
                 pageNumber < pages.size();
                 pageNumber++) {

                PageExtraction page = pages.get(pageNumber);

                for (StructuredBlock block :
                        page.getStructuredBlocks()) {

                    writeBlock(
                            document,
                            block,
                            bulletNumId,
                            numberedNumId
                    );
                }

                if (!page.getImages().isEmpty()) {
                    XWPFParagraph carrier = document.createParagraph();

                    for (ExtractedImage image : page.getImages()) {
                        insertFloatingImage(carrier, page, image, shapeId++);
                    }
                }

                boolean isLastPage = pageNumber == pages.size() - 1;

                if (isLastPage) {
                    applyPageSize(document, page);
                } else {
                    insertSectionBreak(document, page);
                }
            }

            document.write(output);

            return output.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to generate Word document.",
                    e
            );
        }
    }

    private static final double TWIPS_PER_POINT = 20.0;

    private void applyPageSize(
            XWPFDocument document,
            PageExtraction page
    ) {
        CTBody body =
                document.getDocument().getBody();

        CTSectPr sectPr =
                body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();

        setPageSize(sectPr, page);
    }

    private void insertSectionBreak(
            XWPFDocument document,
            PageExtraction page
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        CTPPr pPr =
                paragraph.getCTP().isSetPPr()
                        ? paragraph.getCTP().getPPr()
                        : paragraph.getCTP().addNewPPr();

        CTSectPr sectPr = pPr.addNewSectPr();
        setPageSize(sectPr, page);
    }

    private void setPageSize(
            CTSectPr sectPr,
            PageExtraction page
    ) {
        boolean swapped =
                page.getRotation() == 90
                        || page.getRotation() == 270;

        float widthPt =
                swapped ? page.getCropHeight() : page.getCropWidth();

        float heightPt =
                swapped ? page.getCropWidth() : page.getCropHeight();

        CTPageSz pageSz =
                sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();

        pageSz.setW(
                BigInteger.valueOf(Math.round(widthPt * TWIPS_PER_POINT))
        );

        pageSz.setH(
                BigInteger.valueOf(Math.round(heightPt * TWIPS_PER_POINT))
        );

        pageSz.setOrient(
                widthPt > heightPt
                        ? STPageOrientation.LANDSCAPE
                        : STPageOrientation.PORTRAIT
        );
    }

    private void insertFloatingImage(
            XWPFParagraph carrier,
            PageExtraction page,
            ExtractedImage image,
            int shapeId
    ) {
        if (image.getData() == null || image.getData().length == 0) {
            return;
        }

        ImagePositionMapper.Placement placement =
                ImagePositionMapper.map(image, page);

        if (placement.extentXEmu() <= 0 || placement.extentYEmu() <= 0) {
            return;
        }

        try {
            String relationId =
                    carrier.getDocument().addPictureData(
                            image.getData(),
                            Document.PICTURE_TYPE_PNG
                    );

            XWPFRun run = carrier.createRun();

            String pictureName =
                    image.getImageName() == null
                            ? "image-" + shapeId
                            : image.getImageName();

            String drawingXml = buildAnchorXml(
                    relationId,
                    pictureName,
                    shapeId,
                    placement
            );

            CTDrawing parsedDrawing = CTDrawing.Factory.parse(drawingXml);

            XmlCursor source = parsedDrawing.newCursor();
            source.toFirstChild();

            XmlCursor target = run.getCTR().newCursor();
            target.toEndToken();

            source.moveXml(target);
            source.dispose();
            target.dispose();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to embed image '" + image.getImageName() + "' into Word document.",
                    e
            );
        }
    }

    private String buildAnchorXml(
            String relationId,
            String name,
            int shapeId,
            ImagePositionMapper.Placement placement
    ) {
        return """
                <w:drawing xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <wp:anchor xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                             xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                             xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                             distT="0" distB="0" distL="0" distR="0" simplePos="0"
                             relativeHeight="%1$d" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1">
                    <wp:simplePos x="0" y="0"/>
                    <wp:positionH relativeFrom="page"><wp:posOffset>%2$d</wp:posOffset></wp:positionH>
                    <wp:positionV relativeFrom="page"><wp:posOffset>%3$d</wp:posOffset></wp:positionV>
                    <wp:extent cx="%4$d" cy="%5$d"/>
                    <wp:effectExtent l="0" t="0" r="0" b="0"/>
                    <wp:wrapNone/>
                    <wp:docPr id="%1$d" name="%6$s"/>
                    <wp:cNvGraphicFramePr>
                      <a:graphicFrameLocks noChangeAspect="1"/>
                    </wp:cNvGraphicFramePr>
                    <a:graphic>
                      <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                        <pic:pic>
                          <pic:nvPicPr>
                            <pic:cNvPr id="%1$d" name="%6$s"/>
                            <pic:cNvPicPr/>
                          </pic:nvPicPr>
                          <pic:blipFill>
                            <a:blip r:embed="%7$s"/>
                            <a:stretch><a:fillRect/></a:stretch>
                          </pic:blipFill>
                          <pic:spPr>
                            <a:xfrm rot="%8$d" flipH="%9$s">
                              <a:off x="0" y="0"/>
                              <a:ext cx="%4$d" cy="%5$d"/>
                            </a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                          </pic:spPr>
                        </pic:pic>
                      </a:graphicData>
                    </a:graphic>
                  </wp:anchor>
                </w:drawing>
                """.formatted(
                shapeId,
                placement.offsetXEmu(),
                placement.offsetYEmu(),
                placement.extentXEmu(),
                placement.extentYEmu(),
                escapeXml(name),
                relationId,
                placement.rotation60000ths(),
                placement.flipHorizontal() ? "1" : "0"
        );
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

private void writeBlock(
        XWPFDocument document,
        StructuredBlock block,
        BigInteger bulletNumId,
        BigInteger numberedNumId
) {

    XWPFParagraph paragraph =
            document.createParagraph();

    if (block.getType() == BlockType.HEADING) {
        paragraph.setStyle("Heading1");
    } else {
        paragraph.setStyle("Normal");
    }

    if (block.getType() == BlockType.LIST_ITEM) {

        if (isNumberedListItem(block.getText())) {
            paragraph.setNumID(numberedNumId);
        } else {
            paragraph.setNumID(bulletNumId);
        }

        writeListRuns(paragraph, block);

        return;
    }

    writeRuns(paragraph, block);
}

    private void writeRuns(
            XWPFParagraph paragraph,
            StructuredBlock block
    ) {

        if (block.getSpans() == null
                || block.getSpans().isEmpty()) {

            XWPFRun run =
                    paragraph.createRun();

            run.setText(
                    block.getText() == null
                            ? ""
                            : block.getText().strip()
            );

            return;
        }

        List<TextSpan> spans =
                block.getSpans().stream()
                        .filter(span ->
                                span.getText() != null
                                        && !span.getText().isBlank()
                        )
                        .toList();

        String previousText = null;

        for (int i = 0; i < spans.size(); i++) {

            TextSpan span =
                    spans.get(i);

            String text =
                    span.getText();

            if (i == 0) {
                text = text.stripLeading();
            }

            if (i == spans.size() - 1) {
                text = text.stripTrailing();
            }

            if (text.isEmpty()) {
                continue;
            }

            if (span.isWordSeparatorBefore()
                    && previousText != null
                    && !previousText.isEmpty()
                    && !Character.isWhitespace(
                    previousText.charAt(
                            previousText.length() - 1
                    )
            )
                    && !Character.isWhitespace(
                    text.charAt(0)
            )) {

                text = " " + text;
            }

            XWPFRun run =
                    paragraph.createRun();

            run.setText(text);

            applyFormatting(run, span);

            previousText = text;
        }
    }
}