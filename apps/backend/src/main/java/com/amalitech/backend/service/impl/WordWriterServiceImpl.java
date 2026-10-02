package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.*;
import com.amalitech.backend.service.BlockAlignment;
import com.amalitech.backend.service.BlockType;
import com.amalitech.backend.service.ExtractedImage;
import com.amalitech.backend.service.ImagePositionMapper;
import com.amalitech.backend.service.Footnote;
import com.amalitech.backend.service.PageExtraction;
import com.amalitech.backend.service.PdfExtractionResult;
import com.amalitech.backend.service.StructuredBlock;
import com.amalitech.backend.service.TableCell;
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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFramePr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHAnchor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVAnchor;

import java.math.BigInteger;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WordWriterServiceImpl implements WordWriterService {

    private static final int TWIPS_PER_POINT = 20;

    private static final float DEFAULT_PAGE_WIDTH_POINTS = 612f;
    private static final float DEFAULT_PAGE_HEIGHT_POINTS = 792f;

    private static final float ASCENT_RATIO = 0.8f;

    private static final Pattern NUMBERED_PATTERN =
            Pattern.compile(
                    "^\\s*(?:\\d+|[a-z]|[ivx]+|[IVX]{2,}|[A-Z])[.)]\\s*"
            );

    private static final Pattern LIST_MARKER_PATTERN =
            Pattern.compile(
                    "^\\s*(?:"
                            + "[•◦▪‣⁃∙·✓*\\-]"
                            + "|"
                            + "(?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)[.)]"
                            + "|"
                            + "\\((?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)\\)"
                            + ")\\s*"
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
            BigInteger abstractNumId,
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

        abstractNum.setAbstractNumId(abstractNumId);

        CTLvl level =
                abstractNum.addNewLvl();

        level.setIlvl(BigInteger.ZERO);

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

        var pPr = level.addNewPPr();

        var tabs = pPr.addNewTabs();
        var tab = tabs.addNewTab();
        tab.setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.NUM
        );
        tab.setPos(BigInteger.valueOf(720));

        var ind = pPr.addNewInd();
        ind.setLeft(BigInteger.valueOf(720));
        ind.setHanging(BigInteger.valueOf(360));

        numbering.addAbstractNum(
                new XWPFAbstractNum(abstractNum)
        );

        return numbering.addNum(
                abstractNumId
        );
    }

    private BigInteger createBulletNumbering(
            XWPFDocument document,
            BigInteger abstractNumId,
            String bulletGlyph
    ) {
        return createNumbering(
                document,
                abstractNumId,
                STNumberFormat.BULLET,
                bulletGlyph,
                null
        );
    }

    private float getBlockMaxFontSize(
            StructuredBlock block
    ) {
        if (block.getSpans() == null
                || block.getSpans().isEmpty()) {
            return 0f;
        }

        return block.getSpans().stream()
                .map(TextSpan::getFontSize)
                .filter(size -> size > 0f)
                .max(Float::compare)
                .orElse(0f);
    }

    private float determineBodyFontSize(
            PdfExtractionResult extractionResult
    ) {
        List<Float> sizes =
                extractionResult.getPages().stream()
                        .flatMap(page ->
                                page.getStructuredBlocks().stream()
                        )
                        .filter(block ->
                                block.getType() == BlockType.PARAGRAPH
                                        || block.getType()
                                        == BlockType.LIST_ITEM
                        )
                        .flatMap(block ->
                                block.getSpans().stream()
                        )
                        .map(TextSpan::getFontSize)
                        .filter(size -> size > 0f)
                        .sorted()
                        .toList();

        if (sizes.isEmpty()) {
            return 0f;
        }

        return sizes.get(
                (sizes.size() - 1) / 2
        );
    }

    private String resolveHeadingStyle(
            StructuredBlock block,
            float bodyFontSize
    ) {
        float headingSize =
                getBlockMaxFontSize(block);

        if (bodyFontSize <= 0f
                || headingSize <= 0f) {
            return "Heading2";
        }

        float ratio =
                headingSize / bodyFontSize;

        if (ratio >= 1.60f) {
            return "Heading1";
        }

        if (ratio >= 1.30f) {
            return "Heading2";
        }

        return "Heading3";
    }

    private BigInteger createNumberedNumbering(
            XWPFDocument document,
            BigInteger abstractNumId,
            String firstItemText
    ) {
        return createNumbering(
                document,
                abstractNumId,
                resolveOrderedNumberFormat(
                        firstItemText
                ),
                resolveOrderedLevelText(
                        firstItemText
                ),
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

    private void applyParagraphSpacing(
            XWPFParagraph paragraph,
            StructuredBlock block
    ) {
        if (block.getType() == BlockType.HEADING) {
            paragraph.setSpacingBefore(240); // 12 pt
            paragraph.setSpacingAfter(120);  // 6 pt
            return;
        }

        if (block.getType() == BlockType.LIST_ITEM) {
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(40);   // 2 pt
            return;
        }

        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(120);      // 6 pt
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

        float bodyFontSize =
                determineBodyFontSize(
                        extractionResult
                );
        int nextAbstractNumId = 0;

        try (
                XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            BigInteger activeBulletNumId = null;
            BigInteger activeNumberedNumId = null;
            ListType activeListType = null;
            String activeBulletGlyph = null;

            Map<String, XWPFFootnote> footnotesByKey =
                    createFootnotes(document, extractionResult.getFootnotes());

            List<PageExtraction> pages = extractionResult.getPages();

            int shapeId = 1;

            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {

                PageExtraction page = pages.get(pageIndex);

                for (StructuredBlock block :
                        page.getStructuredBlocks()) {

                    if (block.getType() != BlockType.LIST_ITEM) {
                        activeListType = null;
                        activeBulletNumId = null;
                        activeNumberedNumId = null;
                        activeBulletGlyph = null;

                        writeBlock(
                                document,
                                block,
                                null,
                                null,
                                bodyFontSize
                        );

                        continue;
                    }

                    ListType currentType =
                            block.getListType();

                    if (currentType == ListType.ORDERED) {

                        if (activeListType != ListType.ORDERED) {

                            BigInteger abstractNumId =
                                    BigInteger.valueOf(
                                            nextAbstractNumId++
                                    );

                            activeNumberedNumId =
                                    createNumberedNumbering(
                                            document,
                                            abstractNumId,
                                            block.getText()
                                    );
                        }

                        activeListType =
                                ListType.ORDERED;

                        activeBulletGlyph =
                                null;

                    } else if (currentType == ListType.UNORDERED) {

                        String currentBulletGlyph =
                                resolveBulletGlyph(
                                        block.getText()
                                );

                        boolean newBulletGroup =
                                activeListType != ListType.UNORDERED
                                        || activeBulletGlyph == null
                                        || !currentBulletGlyph.equals(
                                        activeBulletGlyph
                                );

                        if (newBulletGroup) {

                            BigInteger abstractNumId =
                                    BigInteger.valueOf(
                                            nextAbstractNumId++
                                    );

                            activeBulletNumId =
                                    createBulletNumbering(
                                            document,
                                            abstractNumId,
                                            currentBulletGlyph
                                    );
                        }

                        activeListType =
                                ListType.UNORDERED;

                        activeBulletGlyph =
                                currentBulletGlyph;
                    }

                    writeBlock(
                            document,
                            block,
                            activeBulletNumId,
                            activeNumberedNumId,
                            bodyFontSize
                            numberedNumId,
                            footnotesByKey
                    );
                }

                if (!page.getImages().isEmpty()) {
                    XWPFParagraph carrier = document.createParagraph();

                    for (ExtractedImage image : page.getImages()) {
                        insertFloatingImage(carrier, page, image, shapeId++);
                    }
                }

                boolean isLastPage = pageIndex == pages.size() - 1;

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

        float rawWidth =
                swapped ? page.getCropHeight() : page.getCropWidth();

        float rawHeight =
                swapped ? page.getCropWidth() : page.getCropHeight();

        float widthPt = rawWidth > 0f ? rawWidth : DEFAULT_PAGE_WIDTH_POINTS;
        float heightPt = rawHeight > 0f ? rawHeight : DEFAULT_PAGE_HEIGHT_POINTS;

        CTPageSz pageSz =
                sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();

        pageSz.setW(toTwips(widthPt));
        pageSz.setH(toTwips(heightPt));

        pageSz.setOrient(
                widthPt > heightPt
                        ? STPageOrientation.LANDSCAPE
                        : STPageOrientation.PORTRAIT
        );

        CTPageMar pageMargin =
                sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();

        pageMargin.setTop(BigInteger.ZERO);
        pageMargin.setBottom(BigInteger.ZERO);
        pageMargin.setLeft(BigInteger.ZERO);
        pageMargin.setRight(BigInteger.ZERO);
        pageMargin.setHeader(BigInteger.ZERO);
        pageMargin.setFooter(BigInteger.ZERO);
        pageMargin.setGutter(BigInteger.ZERO);
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

    private BigInteger toTwips(float points) {
        return BigInteger.valueOf(Math.round(points * TWIPS_PER_POINT));
    }

    private Map<String, XWPFFootnote> createFootnotes(
            XWPFDocument document,
            List<Footnote> footnotes
    ) {
        Map<String, XWPFFootnote> footnotesByKey = new HashMap<>();

        for (Footnote footnote : footnotes) {

            XWPFFootnote wordFootnote = document.createFootnote();

            XWPFParagraph footnoteParagraph =
                    wordFootnote.getParagraphArray(0) != null
                            ? wordFootnote.getParagraphArray(0)
                            : wordFootnote.createParagraph();

            XWPFRun textRun = footnoteParagraph.createRun();
            textRun.setText(" " + footnote.text());

            footnotesByKey.put(footnote.key(), wordFootnote);
        }

        return footnotesByKey;
    }

    private void writeFootnoteReference(
            XWPFParagraph paragraph,
            String footnoteKey,
            Map<String, XWPFFootnote> footnotesByKey
    ) {
        XWPFFootnote footnote = footnotesByKey.get(footnoteKey);

        if (footnote == null) {
            return;
        }

        XWPFRun run = paragraph.createRun();
        run.setSubscript(VerticalAlign.SUPERSCRIPT);
        run.getCTR()
                .addNewFootnoteReference()
                .setId(footnote.getCTFtnEdn().getId());
    }

private void writeBlock(
        XWPFDocument document,
        StructuredBlock block,
        BigInteger bulletNumId,
        BigInteger numberedNumId,
        Map<String, XWPFFootnote> footnotesByKey
) {

    if (block.getType() == BlockType.TABLE) {
        writeTable(document, block, footnotesByKey);
        return;
    }
    private void writeBlock(
            XWPFDocument document,
            StructuredBlock block,
            BigInteger bulletNumId,
            BigInteger numberedNumId,
            float bodyFontSize
    ) {

    XWPFParagraph paragraph =
            document.createParagraph();

    applyParagraphSpacing(
            paragraph,
            block
    );

        if (block.getType() == BlockType.HEADING) {

            paragraph.setStyle(
                    resolveHeadingStyle(
                            block,
                            bodyFontSize
                    )
            );

        } else {
            paragraph.setStyle("Normal");
        }

    paragraph.setAlignment(toParagraphAlignment(block.getAlignment()));

    applyAbsolutePosition(paragraph, block);

    if (block.getFootnoteKey() != null) {
        writeFootnoteReference(paragraph, block.getFootnoteKey(), footnotesByKey);
    }

    if (block.getType() == BlockType.LIST_ITEM) {

        if (block.getListType() == ListType.ORDERED) {
            paragraph.setNumID(numberedNumId);
            paragraph.setNumILvl(BigInteger.ZERO);

        } else if (block.getListType() == ListType.UNORDERED) {
            paragraph.setNumID(bulletNumId);
            paragraph.setNumILvl(BigInteger.ZERO);
        }

        writeListRuns(paragraph, block);

        return;
    }

    writeRuns(paragraph, block);
}


    private void applyAbsolutePosition(
            XWPFParagraph paragraph,
            StructuredBlock block
    ) {
        CTFramePr framePr = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr().addNewFramePr()
                : paragraph.getCTP().addNewPPr().addNewFramePr();

        framePr.setX(toTwips(block.getX()));
        framePr.setY(toTwips(block.getY() - estimateAscent(block)));
        framePr.setW(toTwips(block.getWidth()));
        framePr.setHAnchor(STHAnchor.PAGE);
        framePr.setVAnchor(STVAnchor.PAGE);

        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.0, LineSpacingRule.AUTO);
    }

    private float estimateAscent(StructuredBlock block) {

        float fontSize = block.getSpans().stream()
                .map(TextSpan::getFontSize)
                .filter(size -> size > 0)
                .max(Float::compareTo)
                .orElse(0f);

        return fontSize * ASCENT_RATIO;
    }

    private ParagraphAlignment toParagraphAlignment(BlockAlignment alignment) {

        if (alignment == null) {
            return ParagraphAlignment.LEFT;
        }

        return switch (alignment) {
            case CENTER -> ParagraphAlignment.CENTER;
            case RIGHT -> ParagraphAlignment.RIGHT;
            case JUSTIFY -> ParagraphAlignment.BOTH;
            case LEFT -> ParagraphAlignment.LEFT;
        };
    }

    private void writeTable(
            XWPFDocument document,
            StructuredBlock block,
            Map<String, XWPFFootnote> footnotesByKey
    ) {

        List<List<TableCell>> rows =
                block.getTableRows();

        if (rows == null || rows.isEmpty()) {
            return;
        }

        int columnCount = rows.getFirst().size();

        if (columnCount == 0) {
            return;
        }

        XWPFTable table =
                document.createTable(rows.size(), columnCount);

        applyTablePosition(table, block);

        List<Float> columnWidths = block.getColumnWidths();
        List<Float> rowHeights = block.getRowHeights();

        applyTableGrid(table, columnWidths, columnCount);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {

            XWPFTableRow tableRow =
                    table.getRow(rowIndex);

            if (rowHeights != null && rowIndex < rowHeights.size()) {
                tableRow.setHeight(
                        toTwips(rowHeights.get(rowIndex)).intValueExact()
                );
            }

            List<TableCell> row =
                    rows.get(rowIndex);

            for (int columnIndex = row.size() - 1;
                 columnIndex >= 0;
                 columnIndex--) {

                writeTableCell(rows, tableRow, rowIndex, columnIndex, footnotesByKey);

                if (columnWidths != null && columnIndex < columnWidths.size()) {

                    int span = effectiveColumnSpan(rows, rowIndex, columnIndex);

                    setColumnWidth(
                            tableRow,
                            columnIndex,
                            sumColumnWidths(columnWidths, columnIndex, span)
                    );
                }
            }
        }
    }

    private int effectiveColumnSpan(
            List<List<TableCell>> rows,
            int rowIndex,
            int columnIndex
    ) {
        TableCell cell = rows.get(rowIndex).get(columnIndex);

        if (cell.rowSpan() >= 1) {
            return Math.max(1, cell.columnSpan());
        }

        TableCell anchor = rows.get(cell.row()).get(cell.column());

        return Math.max(1, anchor.columnSpan());
    }

    private float sumColumnWidths(
            List<Float> columnWidths,
            int startColumn,
            int span
    ) {
        float total = 0f;

        for (int i = startColumn;
             i < startColumn + span && i < columnWidths.size();
             i++) {

            total += columnWidths.get(i);
        }

        return total;
    }

    private void applyTableGrid(
            XWPFTable table,
            List<Float> columnWidths,
            int columnCount
    ) {
        CTTblGrid grid = table.getCTTbl().addNewTblGrid();

        for (int column = 0; column < columnCount; column++) {

            float widthPoints =
                    columnWidths != null && column < columnWidths.size()
                            ? columnWidths.get(column)
                            : 0f;

            grid.addNewGridCol().setW(toTwips(widthPoints));
        }
    }

    private void applyTablePosition(
            XWPFTable table,
            StructuredBlock block
    ) {
        CTTblPr tblPr = table.getCTTbl().getTblPr() != null
                ? table.getCTTbl().getTblPr()
                : table.getCTTbl().addNewTblPr();

        CTTblPPr tblpPr = tblPr.addNewTblpPr();
        tblpPr.setTblpX(toTwips(block.getX()));
        tblpPr.setTblpY(toTwips(block.getY()));
        tblpPr.setHorzAnchor(STHAnchor.PAGE);
        tblpPr.setVertAnchor(STVAnchor.PAGE);

        tblPr.addNewTblLayout().setType(STTblLayoutType.FIXED);
    }

    private void setColumnWidth(
            XWPFTableRow tableRow,
            int columnIndex,
            float widthPoints
    ) {
        XWPFTableCell tableCell = tableRow.getCell(columnIndex);

        if (tableCell == null) {
            return;
        }

        tableCell.setWidthType(TableWidthType.DXA);
        tableCell.setWidth(toTwips(widthPoints).toString());
    }

    private void writeTableCell(
            List<List<TableCell>> rows,
            XWPFTableRow tableRow,
            int rowIndex,
            int columnIndex,
            Map<String, XWPFFootnote> footnotesByKey
    ) {

        TableCell cell =
                rows.get(rowIndex).get(columnIndex);

        if (cell.rowSpan() >= 1) {

            XWPFTableCell tableCell =
                    tableRow.getCell(columnIndex);

            setCellText(tableCell, cell.text(), cell.footnoteKey(), footnotesByKey);

            if (cell.columnSpan() > 1) {
                setGridSpan(tableCell, cell.columnSpan());
            }

            if (cell.rowSpan() > 1) {
                setVerticalMerge(tableCell, STMerge.RESTART);
            }

            return;
        }

        TableCell anchor =
                rows.get(cell.row()).get(cell.column());

        boolean sameRowAsAnchor =
                cell.row() == rowIndex;

        if (sameRowAsAnchor) {
            tableRow.removeCell(columnIndex);
            return;
        }

        if (columnIndex != cell.column()) {
            tableRow.removeCell(columnIndex);
            return;
        }

        XWPFTableCell tableCell =
                tableRow.getCell(columnIndex);

        setCellText(tableCell, "", null, footnotesByKey);
        setVerticalMerge(tableCell, STMerge.CONTINUE);

        if (anchor.columnSpan() > 1) {
            setGridSpan(tableCell, anchor.columnSpan());
        }
    }

    private void setCellText(
            XWPFTableCell tableCell,
            String text,
            String footnoteKey,
            Map<String, XWPFFootnote> footnotesByKey
    ) {

        XWPFParagraph cellParagraph =
                tableCell.getParagraphArray(0) != null
                        ? tableCell.getParagraphArray(0)
                        : tableCell.addParagraph();

        if (footnoteKey != null) {
            writeFootnoteReference(cellParagraph, footnoteKey, footnotesByKey);
        }

        XWPFRun run =
                cellParagraph.createRun();

        run.setText(text);
    }

    private void setGridSpan(
            XWPFTableCell tableCell,
            int span
    ) {

        CTTcPr tcPr =
                tableCell.getCTTc().isSetTcPr()
                        ? tableCell.getCTTc().getTcPr()
                        : tableCell.getCTTc().addNewTcPr();

        tcPr.addNewGridSpan()
                .setVal(BigInteger.valueOf(span));
    }

    private void setVerticalMerge(
            XWPFTableCell tableCell,
            STMerge.Enum mergeType
    ) {

        CTTcPr tcPr =
                tableCell.getCTTc().isSetTcPr()
                        ? tableCell.getCTTc().getTcPr()
                        : tableCell.getCTTc().addNewTcPr();

        tcPr.addNewVMerge()
                .setVal(mergeType);
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

    private STNumberFormat.Enum resolveOrderedNumberFormat(
            String text
    ) {
        if (text == null) {
            return STNumberFormat.DECIMAL;
        }

        String trimmed =
                text.stripLeading();

        String marker =
                trimmed.split("\\s+", 2)[0];

        String normalized =
                marker
                        .replace("(", "")
                        .replace(")", "")
                        .replace(".", "");

        if (normalized.matches(
                "[ivxlcdm]+"
        )) {
            return STNumberFormat.LOWER_ROMAN;
        }

        if (normalized.matches(
                "[IVXLCDM]+"
        )) {
            return STNumberFormat.UPPER_ROMAN;
        }

        if (normalized.matches(
                "[a-z]"
        )) {
            return STNumberFormat.LOWER_LETTER;
        }

        if (normalized.matches(
                "[A-Z]"
        )) {
            return STNumberFormat.UPPER_LETTER;
        }

        return STNumberFormat.DECIMAL;
    }

    private String resolveOrderedLevelText(
            String text
    ) {
        if (text == null) {
            return "%1.";
        }

        String trimmed = text.stripLeading();

        if (trimmed.matches(
                "^\\((?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)\\)\\s+.*"
        )) {
            return "(%1)";
        }

        if (trimmed.matches(
                "^(?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)\\)\\s+.*"
        )) {
            return "%1)";
        }

        return "%1.";
    }

    private String resolveBulletGlyph(String text) {

        if (text == null || text.isBlank()) {
            return "•";
        }

        String trimmed =
                text.stripLeading();

        if (trimmed.startsWith("▪")) {
            return "▪";
        }

        if (trimmed.startsWith("✓")) {
            return "✓";
        }

        if (trimmed.startsWith("·")) {
            return "·";
        }

        if (trimmed.startsWith("◦")) {
            return "◦";
        }

        if (trimmed.startsWith("‣")) {
            return "‣";
        }

        if (trimmed.startsWith("⁃")) {
            return "⁃";
        }

        if (trimmed.startsWith("∙")) {
            return "∙";
        }

        if (trimmed.startsWith("-")) {
            return "-";
        }

        if (trimmed.startsWith("*")) {
            return "*";
        }

        return "•";
    }
}
