package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.*;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.apache.xmlbeans.XmlCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridCol;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHeightRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabTlc;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class WordWriterServiceImpl implements WordWriterService {

    private static final Logger log =
            LoggerFactory.getLogger(WordWriterServiceImpl.class);

    private static final int TWIPS_PER_POINT = 20;

    private static final float DEFAULT_PAGE_WIDTH_POINTS = 612f;
    private static final float DEFAULT_PAGE_HEIGHT_POINTS = 792f;
    private static final float DEFAULT_MARGIN_POINTS = 36f;
    private static final float MAX_FLOW_VERTICAL_MARGIN_POINTS = 18f;
    private static final double EMU_PER_POINT =
            12700.0;
    private static final float IMAGE_ROW_TOLERANCE =
            12f;
    private static final float MAX_RUNNING_BLOCK_HEIGHT_RATIO =
            0.08f;

    private static final int MAX_RUNNING_TEXT_LENGTH =
            240;

    private static final Pattern NUMBERED_PATTERN =
            Pattern.compile(
                    "^\\s*(?:\\d+|[a-z]|[ivx]+|[IVX]{2,}|[A-Z])[.)]\\s*"
            );

    private static final Pattern LIST_MARKER_PATTERN =
            Pattern.compile(
                    "^\\s*(?:"
                            + "[•●◦▪‣⁃∙·✓*\\-]"
                            + "|"
                            + "(?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)[.)]"
                            + "|"
                            + "\\((?:\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)\\)"
                            + ")\\s*"
            );

    private static final Pattern INDEX_ENTRY_PATTERN =
            Pattern.compile(
                    "^(.+?)(?:\\s*[.…]{2,}[.…\\s]*|\\s{3,})(\\d{1,3})$"
            );

    private void applyFormatting(
            XWPFRun run,
            TextSpan span
    ) {

        run.setBold(span.isBold());
        run.setItalic(span.isItalic());

        String fontFamily = FontFamilyResolver.resolve(span.getFontName());
        if (fontFamily != null) {
            run.setFontFamily(fontFamily);
        }

        run.setUnderline(
                span.isUnderline()
                        ? UnderlinePatterns.SINGLE
                        : UnderlinePatterns.NONE
        );

        if (span.getFontSize() > 0) {
            run.setFontSize(span.getFontSize());
        }
        if (span.getColorHex() != null
                && !span.getColorHex().isBlank()) {

            run.setColor(
                    span.getColorHex()
            );
        }
    }

    private boolean isPlausibleRunningBlock(
            PageExtraction page,
            StructuredBlock block
    ) {
        if (page == null
                || block == null) {
            return false;
        }

        String text =
                normalizedBlockText(block);

        if (text.isBlank()
                || text.length()
                > MAX_RUNNING_TEXT_LENGTH) {
            return false;
        }

        float pageHeight =
                page.getCropHeight() > 0f
                        ? page.getCropHeight()
                        : DEFAULT_PAGE_HEIGHT_POINTS;

        return block.getHeight()
                <= pageHeight
                * MAX_RUNNING_BLOCK_HEIGHT_RATIO;
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
            BigInteger start,
            float markerFontSize,
            String markerFontFamily
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

        if (markerFontSize > 0f || markerFontFamily != null) {
            var markerProperties = level.addNewRPr();
            if (markerFontSize > 0f) {
                markerProperties.addNewSz()
                        .setVal(BigInteger.valueOf(Math.round(markerFontSize * 2)));
            }
            if (markerFontFamily != null) {
                var markerFont = markerProperties.addNewRFonts();
                markerFont.setAscii(markerFontFamily);
                markerFont.setHAnsi(markerFontFamily);
                markerFont.setCs(markerFontFamily);
            }
        }

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
            String bulletGlyph,
            float markerFontSize,
            String markerFontFamily
    ) {
        return createNumbering(
                document,
                abstractNumId,
                STNumberFormat.BULLET,
                bulletGlyph,
                null,
                markerFontSize,
                markerFontFamily
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
            String firstItemText,
            float markerFontSize,
            String markerFontFamily
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
                BigInteger.ONE,
                markerFontSize,
                markerFontFamily
        );
    }

    /**
     * Word family of the first span on a block. List markers and heading
     * styles use it so they do not fall back to the document theme font.
     *
     * @param block block being written
     * @return resolved family, or null when the block has no font
     */
    private String blockFontFamily(StructuredBlock block) {
        if (block == null || block.getSpans() == null) {
            return null;
        }
        for (TextSpan span : block.getSpans()) {
            String family = FontFamilyResolver.resolve(span.getFontName());
            if (family != null) {
                return family;
            }
        }
        return null;
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
        TextSpan previousSpan = null;

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

            if (needsRunSpace(previousSpan, previousText, span, text)) {
                text = " " + text;
            }

            XWPFRun run =
                    paragraph.createRun();

            run.setText(text);

            applyFormatting(run, span);

            previousText = text;
            previousSpan = span;
            firstContentWritten = true;
        }
    }

    private void applyParagraphSpacing(
            XWPFParagraph paragraph,
            int spacingBefore
    ) {
        paragraph.setSpacingBefore(spacingBefore);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.0, LineSpacingRule.AUTO);
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
            Set<String> repeatedHeaders = findRepeatedPageBlocks(pages, true);
            Set<String> repeatedFooters = findRepeatedPageBlocks(pages, false);
            boolean repeatedPageNumbers = hasRepeatedPageNumbers(pages);
            PageMargins pageMargins = determinePageMargins(
                    pages,
                    repeatedHeaders,
                    repeatedFooters,
                    repeatedPageNumbers
            );

            int shapeId = 1;

            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {

                PageExtraction page = pages.get(pageIndex);

                StructuredBlock previousBlock = null;

                List<StructuredBlock> pageBlocks = page.getStructuredBlocks();

                List<List<ExtractedImage>> imageRows =
                        groupImagesIntoRows(
                                page,
                                flowImages(page)
                        );

                int nextImageRowIndex =
                        0;

                ColumnRegion activeColumnRegion =
                        null;

                boolean writingRightColumn =
                        false;

                for (int blockIndex = 0; blockIndex < pageBlocks.size(); blockIndex++) {

                    StructuredBlock block =
                            pageBlocks.get(blockIndex);

                    if (shouldSuppressRunningBlock(
                            page,
                            block,
                            repeatedHeaders,
                            repeatedFooters,
                            repeatedPageNumbers
                    )) {
                        continue;
                    }

                    if (isIndexLevelMarker(block)
                            && blockIndex + 1 < pageBlocks.size()) {

                        StructuredBlock candidate =
                                block;

                        int candidateIndex =
                                blockIndex;

                        boolean foundIndexEntry =
                                false;

                        while (candidateIndex + 1
                                < pageBlocks.size()) {

                            StructuredBlock nextBlock =
                                    pageBlocks.get(
                                            candidateIndex + 1
                                    );

                            if (shouldSuppressRunningBlock(
                                    page,
                                    nextBlock,
                                    repeatedHeaders,
                                    repeatedFooters,
                                    repeatedPageNumbers
                            )) {
                                break;
                            }

                            if (!isAdjacentIndexEntry(
                                    candidate,
                                    nextBlock
                            )) {
                                break;
                            }

                            candidate =
                                    mergeIndexLevelMarker(
                                            candidate,
                                            nextBlock
                                    );

                            candidateIndex++;

                            if (parseIndexEntry(candidate)
                                    != null) {

                                foundIndexEntry =
                                        true;

                                break;
                            }
                        }

                        if (foundIndexEntry) {
                            block =
                                    candidate;

                            blockIndex =
                                    candidateIndex;
                        }
                    }

                    ColumnRegion blockColumnRegion =
                            findColumnRegion(
                                    page,
                                    block
                            );

                    if (blockColumnRegion != activeColumnRegion) {

                        if (activeColumnRegion != null) {
                            insertContinuousColumnSectionBreak(
                                    document,
                                    page,
                                    pageMargins,
                                    activeColumnRegion.columnCount(),
                                    activeColumnRegion
                            );

                            activeColumnRegion = null;
                            writingRightColumn = false;
                            previousBlock = null;
                        }

                        if (blockColumnRegion != null) {

                            while (nextImageRowIndex
                                    < imageRows.size()) {

                                List<ExtractedImage> imageRow =
                                        imageRows.get(
                                                nextImageRowIndex
                                        );

                                float imageY =
                                        imageAnchorY(
                                                page,
                                                imageRow.getFirst()
                                        );

                                if (imageY
                                        >= blockColumnRegion.startY()) {
                                    break;
                                }

                                writeFlowImageRow(
                                        document,
                                        page,
                                        imageRow,
                                        pageMargins.left(),
                                        pageContentWidth(
                                                pageMargins,
                                                page
                                        )
                                );

                                nextImageRowIndex++;

                                previousBlock =
                                        null;
                            }

                            if (!document.getParagraphs().isEmpty()) {
                                insertContinuousColumnSectionBreak(
                                        document,
                                        page,
                                        pageMargins,
                                        1,
                                        null
                                );
                            }

                            activeColumnRegion =
                                    blockColumnRegion;

                            writingRightColumn =
                                    false;

                            previousBlock =
                                    null;
                        }
                    }

                    if (activeColumnRegion != null
                            && !writingRightColumn
                            && block.getX()
                            > activeColumnRegion.splitX()) {

                        insertColumnBreak(
                                document
                        );

                        writingRightColumn =
                                true;

                        previousBlock =
                                null;
                    }

                    while (nextImageRowIndex
                            < imageRows.size()) {

                        final ColumnRegion currentColumnRegion =
                                activeColumnRegion;

                        final boolean currentRightColumn =
                                writingRightColumn;

                        List<ExtractedImage> imageRow =
                                imageRows.get(
                                        nextImageRowIndex
                                );

                        float imageY =
                                imageAnchorY(
                                        page,
                                        imageRow.getFirst()
                                );

                        if (imageY
                                > block.getY()) {
                            break;
                        }

                        if (currentColumnRegion != null
                                && !imageRow.stream().allMatch(
                                image -> imageInCurrentColumn(
                                        page,
                                        currentColumnRegion,
                                        image,
                                        currentRightColumn
                                )
                        )) {
                            break;
                        }

                        float availableWidth =
                                pageContentWidth(
                                        pageMargins,
                                        page
                                );

                        if (currentColumnRegion != null) {
                            availableWidth =
                                    columnContentWidth(
                                            pageMargins,
                                            page,
                                            currentColumnRegion,
                                            currentRightColumn
                                    );
                        }

                        writeFlowImageRow(
                                document,
                                page,
                                imageRow,
                                flowContentLeft(
                                        pageMargins,
                                        currentColumnRegion,
                                        currentRightColumn
                                ),
                                availableWidth
                        );

                        nextImageRowIndex++;

                        previousBlock =
                                null;
                    }


                    float blockWidth = activeColumnRegion == null
                            ? pageContentWidth(pageMargins, page)
                            : columnContentWidth(
                            pageMargins,
                            page,
                            activeColumnRegion,
                            writingRightColumn
                    );
                    float blockLeft = flowContentLeft(
                            pageMargins,
                            activeColumnRegion,
                            writingRightColumn
                    );

                    int spacingBefore =
                            calculateSpacingBefore(
                                    previousBlock,
                                    block
                            );

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
                                bodyFontSize,
                                footnotesByKey,
                                spacingBefore,
                                blockWidth,
                                blockLeft
                        );


                        previousBlock = block;

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

                            float markerFontSize =
                                    getBlockMaxFontSize(block) > 0f
                                            ? getBlockMaxFontSize(block)
                                            : bodyFontSize;

                            activeNumberedNumId =
                                    createNumberedNumbering(
                                            document,
                                            abstractNumId,
                                            block.getText(),
                                            markerFontSize,
                                            blockFontFamily(block)
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
                                        || !currentBulletGlyph.equals(
                                        activeBulletGlyph
                                );

                        if (newBulletGroup) {

                            BigInteger abstractNumId =
                                    BigInteger.valueOf(
                                            nextAbstractNumId++
                                    );

                            float markerFontSize =
                                    getBlockMaxFontSize(block) > 0f
                                            ? getBlockMaxFontSize(block)
                                            : bodyFontSize;

                            activeBulletNumId =
                                    createBulletNumbering(
                                            document,
                                            abstractNumId,
                                            currentBulletGlyph,
                                            markerFontSize,
                                            blockFontFamily(block)
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
                            bodyFontSize,
                            footnotesByKey,
                            spacingBefore,
                            blockWidth,
                            blockLeft
                    );

                    previousBlock = block;
                }

                if (activeColumnRegion != null) {

                    insertContinuousColumnSectionBreak(
                            document,
                            page,
                            pageMargins,
                            activeColumnRegion.columnCount(),
                            activeColumnRegion
                    );

                    activeColumnRegion =
                            null;
                }

                while (nextImageRowIndex
                        < imageRows.size()) {

                    writeFlowImageRow(
                            document,
                            page,
                            imageRows.get(
                                    nextImageRowIndex
                            ),
                            pageMargins.left(),
                            pageContentWidth(
                                    pageMargins,
                                    page
                            )
                    );

                    nextImageRowIndex++;
                }

                List<ExtractedImage> backgroundImages =
                        page.getImages().stream()
                                .filter(image ->
                                        isBackgroundImage(
                                                page,
                                                image
                                        )
                                )
                                .toList();

                if (!backgroundImages.isEmpty()) {

                    XWPFParagraph carrier =
                            document.createParagraph();

                    carrier.setSpacingBefore(0);
                    carrier.setSpacingAfter(0);
                    carrier.setSpacingBetween(
                            0.0,
                            LineSpacingRule.EXACT
                    );

                    for (ExtractedImage image :
                            backgroundImages) {

                        insertFloatingImage(
                                carrier,
                                page,
                                image,
                                shapeId++
                        );
                    }
                }

                boolean isLastPage = pageIndex == pages.size() - 1;

                if (isLastPage) {
                    applyPageSize(
                            document,
                            page,
                            findRepeatedBlock(page, repeatedHeaders, true),
                            findRepeatedBlock(page, repeatedFooters, false),
                            repeatedPageNumbers,
                            pageMargins
                    );
                } else {
                    PageExtraction nextPage = pages.get(pageIndex + 1);

                    if (hasSamePageGeometry(page, nextPage)
                            && !requiresExplicitPageBoundary(
                            page,
                            repeatedHeaders,
                            repeatedFooters,
                            repeatedPageNumbers
                    )) {
                        insertPageBreak(document);
                    } else {
                        insertSectionBreak(
                                document,
                                page,
                                findRepeatedBlock(page, repeatedHeaders, true),
                                findRepeatedBlock(page, repeatedFooters, false),
                                repeatedPageNumbers,
                                pageMargins
                        );
                    }
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
            PageExtraction page,
            StructuredBlock headerBlock,
            StructuredBlock footerBlock,
            boolean pageNumberFooter,
            PageMargins pageMargins
    ) {
        CTBody body =
                document.getDocument().getBody();

        CTSectPr sectPr =
                body.isSetSectPr()
                        ? body.getSectPr()
                        : body.addNewSectPr();

        if (sectPr.isSetType()) {
            sectPr.getType().setVal(STSectionMark.CONTINUOUS);
        } else {
            sectPr.addNewType().setVal(STSectionMark.CONTINUOUS);
        }

        setPageSize(
                document,
                sectPr,
                page,
                headerBlock,
                footerBlock,
                pageNumberFooter,
                pageMargins
        );
    }

    private void insertColumnBreak(
            XWPFDocument document
    ) {
        XWPFParagraph paragraph =
                document.createParagraph();

        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(0.0, LineSpacingRule.EXACT);

        XWPFRun run =
                paragraph.createRun();

        run.addBreak(
                BreakType.COLUMN
        );
    }

    private void insertContinuousColumnSectionBreak(
            XWPFDocument document,
            PageExtraction page,
            PageMargins pageMargins,
            int columnCount,
            ColumnRegion activeColumnRegion
    ) {
        XWPFParagraph paragraph =
                document.createParagraph();

        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(0.0, LineSpacingRule.EXACT);

        CTPPr pPr =
                paragraph.getCTP().isSetPPr()
                        ? paragraph.getCTP().getPPr()
                        : paragraph.getCTP().addNewPPr();

        CTSectPr sectPr =
                pPr.addNewSectPr();

        sectPr.addNewType()
                .setVal(
                        STSectionMark.CONTINUOUS
                );

        applySectionGeometry(
                sectPr,
                page,
                pageMargins
        );

        var columns =
                sectPr.isSetCols()
                        ? sectPr.getCols()
                        : sectPr.addNewCols();

        columns.setNum(
                BigInteger.valueOf(
                        Math.max(1, columnCount)
                )
        );

        if (columnCount > 1 && activeColumnRegion != null) {
            columns.setSpace(
                    toTwips(
                            determineColumnGap(
                                    page,
                                    activeColumnRegion
                            )
                    )
            );
        }
    }

    private boolean hasContentAfterLastColumnRegion(
            PageExtraction page
    ) {
        if (page.getColumnRegions() == null
                || page.getColumnRegions().isEmpty()) {
            return false;
        }

        float lastRegionEndY =
                page.getColumnRegions().stream()
                        .map(ColumnRegion::endY)
                        .max(Float::compare)
                        .orElse(Float.MAX_VALUE);

        for (StructuredBlock block :
                page.getStructuredBlocks()) {

            float blockCenterY =
                    block.getY()
                            + block.getHeight() / 2f;

            if (blockCenterY
                    > lastRegionEndY) {
                return true;
            }
        }

        return false;
    }

    private float determineColumnGap(
            PageExtraction page,
            ColumnRegion region
    ) {
        if (page == null || region == null) {
            return 18f;
        }

        float leftEdge = Float.NEGATIVE_INFINITY;
        float rightEdge = Float.POSITIVE_INFINITY;

        for (StructuredBlock block :
                page.getStructuredBlocks()) {

            float centerY =
                    block.getY()
                            + block.getHeight() / 2f;

            if (centerY < region.startY()
                    || centerY > region.endY()) {
                continue;
            }

            float blockRight =
                    block.getX()
                            + block.getWidth();

            if (blockRight <= region.splitX()) {
                leftEdge =
                        Math.max(
                                leftEdge,
                                blockRight
                        );
            } else if (block.getX() >= region.splitX()) {
                rightEdge =
                        Math.min(
                                rightEdge,
                                block.getX()
                        );
            }
        }

        if (leftEdge > Float.NEGATIVE_INFINITY
                && rightEdge < Float.POSITIVE_INFINITY
                && rightEdge > leftEdge) {
            return rightEdge - leftEdge;
        }

        return 18f;
    }

    private void applySectionGeometry(
            CTSectPr sectPr,
            PageExtraction page,
            PageMargins pageMargins
    ) {
        boolean swapped =
                page.getRotation() == 90
                        || page.getRotation() == 270;

        float rawWidth =
                swapped
                        ? page.getCropHeight()
                        : page.getCropWidth();

        float rawHeight =
                swapped
                        ? page.getCropWidth()
                        : page.getCropHeight();

        float widthPt =
                rawWidth > 0f
                        ? rawWidth
                        : DEFAULT_PAGE_WIDTH_POINTS;

        float heightPt =
                rawHeight > 0f
                        ? rawHeight
                        : DEFAULT_PAGE_HEIGHT_POINTS;

        CTPageSz pageSz =
                sectPr.isSetPgSz()
                        ? sectPr.getPgSz()
                        : sectPr.addNewPgSz();

        pageSz.setW(
                toTwips(widthPt)
        );

        pageSz.setH(
                toTwips(heightPt)
        );

        pageSz.setOrient(
                widthPt > heightPt
                        ? STPageOrientation.LANDSCAPE
                        : STPageOrientation.PORTRAIT
        );

        CTPageMar pageMargin =
                sectPr.isSetPgMar()
                        ? sectPr.getPgMar()
                        : sectPr.addNewPgMar();

        float topMargin =
                Math.min(
                        pageMargins.top(),
                        MAX_FLOW_VERTICAL_MARGIN_POINTS
                );

        float bottomMargin =
                Math.min(
                        pageMargins.bottom(),
                        MAX_FLOW_VERTICAL_MARGIN_POINTS
                );

        pageMargin.setTop(
                toTwips(topMargin)
        );

        pageMargin.setBottom(
                toTwips(bottomMargin)
        );

        pageMargin.setLeft(
                toTwips(
                        pageMargins.left()
                )
        );

        pageMargin.setRight(
                toTwips(
                        pageMargins.right()
                )
        );

        pageMargin.setHeader(
                toTwips(12f)
        );

        pageMargin.setFooter(
                toTwips(12f)
        );

        pageMargin.setGutter(
                BigInteger.ZERO
        );
    }

    private void insertSectionBreak(
            XWPFDocument document,
            PageExtraction page,
            StructuredBlock headerBlock,
            StructuredBlock footerBlock,
            boolean pageNumberFooter,
            PageMargins pageMargins
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        CTPPr pPr =
                paragraph.getCTP().isSetPPr()
                        ? paragraph.getCTP().getPPr()
                        : paragraph.getCTP().addNewPPr();

        CTSectPr sectPr = pPr.addNewSectPr();
        sectPr.addNewType().setVal(STSectionMark.NEXT_PAGE);
        setPageSize(
                document,
                sectPr,
                page,
                headerBlock,
                footerBlock,
                pageNumberFooter,
                pageMargins
        );
    }

    private void setPageSize(
            XWPFDocument document,
            CTSectPr sectPr,
            PageExtraction page,
            StructuredBlock headerBlock,
            StructuredBlock footerBlock,
            boolean pageNumberFooter,
            PageMargins pageMargins
    ) {
        applySectionGeometry(
                sectPr,
                page,
                pageMargins
        );

        XWPFHeaderFooterPolicy policy =
                new XWPFHeaderFooterPolicy(
                        document,
                        sectPr
                );

        if (headerBlock != null) {
            writeHeaderFooter(
                    policy.createHeader(
                            STHdrFtr.DEFAULT
                    ),
                    headerBlock,
                    false
            );
        } else {
            policy.createHeader(
                    STHdrFtr.DEFAULT
            );
        }

        if (footerBlock != null
                || pageNumberFooter) {

            writeHeaderFooter(
                    policy.createFooter(
                            STHdrFtr.DEFAULT
                    ),
                    footerBlock,
                    pageNumberFooter
            );

        } else {
            policy.createFooter(
                    STHdrFtr.DEFAULT
            );
        }
    }

    private ColumnRegion findColumnRegion(
            PageExtraction page,
            StructuredBlock block
    ) {
        if (page == null
                || block == null) {
            return null;
        }

        float blockCenterY =
                block.getY()
                        + block.getHeight() / 2f;

        for (ColumnRegion region :
                page.getColumnRegions()) {

            if (blockCenterY
                    >= region.startY()
                    && blockCenterY
                    <= region.endY()) {

                return region;
            }
        }

        return null;
    }

    private void insertPageBreak(
            XWPFDocument document
    ) {
        XWPFParagraph paragraph =
                document.createParagraph();

        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(0.0, LineSpacingRule.EXACT);

        XWPFRun run =
                paragraph.createRun();

        run.addBreak(
                BreakType.PAGE
        );
    }

    private boolean hasSamePageGeometry(
            PageExtraction first,
            PageExtraction second
    ) {
        if (!hasKnownPageDimensions(first)
                || !hasKnownPageDimensions(second)) {
            return false;
        }

        return optionalGeometryEqual(
                first.getCropX(),
                second.getCropX()
        )
                && optionalGeometryEqual(
                first.getCropY(),
                second.getCropY()
        )
                && nearlyEqual(
                first.getCropWidth(),
                second.getCropWidth()
        )
                && nearlyEqual(
                first.getCropHeight(),
                second.getCropHeight()
        )
                && first.getRotation()
                == second.getRotation();
    }

    private boolean hasKnownPageDimensions(
            PageExtraction page
    ) {
        return Float.isFinite(page.getCropWidth())
                && page.getCropWidth() > 0f
                && Float.isFinite(page.getCropHeight())
                && page.getCropHeight() > 0f;
    }

    private boolean optionalGeometryEqual(
            float first,
            float second
    ) {
        if (Float.isNaN(first)
                && Float.isNaN(second)) {
            return true;
        }

        return nearlyEqual(first, second);
    }

    private boolean requiresExplicitPageBoundary(
            PageExtraction page,
            Set<String> repeatedHeaders,
            Set<String> repeatedFooters,
            boolean repeatedPageNumbers
    ) {
        boolean hasBackgroundImage =
                page.getImages().stream()
                        .anyMatch(image ->
                                isBackgroundImage(
                                        page,
                                        image
                                )
                        );

        if (hasBackgroundImage) {
            return true;
        }

        boolean writesNoContent =
                page.getStructuredBlocks().stream()
                        .allMatch(block ->
                                shouldSuppressRunningBlock(
                                        page,
                                        block,
                                        repeatedHeaders,
                                        repeatedFooters,
                                        repeatedPageNumbers
                                )
                        );

        if (writesNoContent) {
            return true;
        }

        return false;
    }

    private boolean nearlyEqual(
            float first,
            float second
    ) {
        return Float.isFinite(first)
                && Float.isFinite(second)
                && Math.abs(first - second) <= 0.01f;
    }



    private void writeHeaderFooter(
            XWPFHeaderFooter container,
            StructuredBlock block,
            boolean pageNumber
    ) {
        XWPFParagraph paragraph = container.createParagraph();
        if (block != null) {
            paragraph.setAlignment(toParagraphAlignment(block.getAlignment()));
            writeRuns(paragraph, block);
        }
        if (pageNumber) {
            XWPFRun run = paragraph.createRun();
            run.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            run.getCTR().addNewInstrText().setStringValue(" PAGE ");
            run.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            run.getCTR().addNewT().setStringValue("1");
            run.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
        }
    }

    private Set<String> findRepeatedPageBlocks(
            List<PageExtraction> pages,
            boolean header
    ) {
        Map<String, Set<Integer>>
                pageIndexesByText =
                new HashMap<>();

        for (PageExtraction page : pages) {

            float pageHeight =
                    page.getCropHeight() > 0
                            ? page.getCropHeight()
                            : DEFAULT_PAGE_HEIGHT_POINTS;

            for (StructuredBlock block :
                    page.getStructuredBlocks()) {

                boolean inBand =
                        header
                                ? block.getY()
                                <= pageHeight * 0.18f
                                : block.getY()
                                + block.getHeight()
                                >= pageHeight * 0.82f;

                if (!inBand
                        || !isPlausibleRunningBlock(
                        page,
                        block
                )) {
                    continue;
                }

                String text =
                        normalizedRunningText(
                                block,
                                header
                        );

                if (!text.isBlank()
                        && !(isPageNumberText(text)
                        && !header)) {

                    pageIndexesByText
                            .computeIfAbsent(
                                    text,
                                    ignored ->
                                            new HashSet<>()
                            )
                            .add(
                                    page.getPageIndex()
                            );
                }
            }
        }

        Set<String> repeated =
                new HashSet<>();

        for (Map.Entry<String, Set<Integer>>
                entry :
                pageIndexesByText.entrySet()) {

            if (entry.getValue().size() >= 2) {
                repeated.add(entry.getKey());
            }
        }

        return repeated;
    }

    private boolean hasRepeatedPageNumbers(List<PageExtraction> pages) {
        Set<Integer> pageIndexes = new HashSet<>();

        for (PageExtraction page : pages) {
            for (StructuredBlock block : page.getStructuredBlocks()) {
                if (isPageNumberBlock(page, block)) {
                    pageIndexes.add(page.getPageIndex());
                }
            }
        }

        return pageIndexes.size() >= 2;
    }

    private boolean isPageNumberBlock(
            PageExtraction page,
            StructuredBlock block
    ) {
        float pageHeight = page.getCropHeight() > 0
                ? page.getCropHeight()
                : DEFAULT_PAGE_HEIGHT_POINTS;

        return block.getY() + block.getHeight() >= pageHeight * 0.85f
                && isPageNumberText(normalizedBlockText(block));
    }

    private boolean isPageNumberText(String text) {
        return text != null && text.matches("\\d{1,4}");
    }

    private StructuredBlock findRepeatedBlock(
            PageExtraction page,
            Set<String> repeatedBlocks,
            boolean header
    ) {
        float pageHeight =
                page.getCropHeight() > 0f
                        ? page.getCropHeight()
                        : DEFAULT_PAGE_HEIGHT_POINTS;

        for (StructuredBlock block
                : page.getStructuredBlocks()) {

            boolean inBand =
                    header
                            ? block.getY()
                            <= pageHeight * 0.18f
                            : block.getY()
                            + block.getHeight()
                            >= pageHeight * 0.82f;

            if (!inBand) {
                continue;
            }

            if (!isPlausibleRunningBlock(
                    page,
                    block
            )) {
                continue;
            }

            String text =
                    normalizedRunningText(
                            block,
                            header
                    );

            if (repeatedBlocks.contains(text)) {
                return block;
            }
        }

        return null;
    }

    private String normalizedBlockText(
            StructuredBlock block
    ) {
        if (block == null
                || block.getText() == null) {
            return "";
        }

        return block.getText()
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .strip();
    }

    private String normalizedRunningText(
            StructuredBlock block,
            boolean header
    ) {
        String text =
                normalizedBlockText(block);

        if (header) {
            return text;
        }

        return text
                .replaceFirst("\\s+\\d{1,4}$", "")
                .strip();
    }

    private boolean containsEquivalentRunningText(
            Set<String> repeatedBlocks,
            String text,
            String runningText,
            boolean header
    ) {
        if (repeatedBlocks.contains(text)
                || repeatedBlocks.contains(
                runningText
        )) {
            return true;
        }

        for (String repeated :
                repeatedBlocks) {

            String normalizedRepeated =
                    repeated
                            .replace('\u00A0', ' ')
                            .replaceAll("\\s+", " ")
                            .strip();

            if (!header) {
                normalizedRepeated =
                        normalizedRepeated
                                .replaceFirst(
                                        "\\s+\\d{1,4}$",
                                        ""
                                )
                                .strip();
            }

            if (normalizedRepeated.equals(
                    runningText
            )) {
                return true;
            }
        }

        return false;
    }

    private List<ExtractedImage> flowImages(
            PageExtraction page
    ) {
        return page.getImages().stream()
                .filter(image ->
                        !isBackgroundImage(
                                page,
                                image
                        )
                )
                .sorted(
                        Comparator
                                .comparingDouble(
                                        (ExtractedImage image) ->
                                                getImageFlowY(
                                                        page,
                                                        image
                                                )
                                )
                                .thenComparingDouble(
                                        (ExtractedImage image) ->
                                                image.getX()
                                )
                )
                .toList();
    }

    private List<List<ExtractedImage>> groupImagesIntoRows(
            PageExtraction page,
            List<ExtractedImage> images
    ) {
        List<List<ExtractedImage>> rows =
                new ArrayList<>();

        for (ExtractedImage image : images) {

            List<ExtractedImage> matchingRow =
                    null;

            for (List<ExtractedImage> row :
                    rows) {

                if (row.isEmpty()) {
                    continue;
                }

                float referenceY =
                        getImageFlowY(
                                page,
                                row.getFirst()
                        );

                float imageY =
                        getImageFlowY(
                                page,
                                image
                        );

                if (Math.abs(
                        imageY - referenceY
                ) <= IMAGE_ROW_TOLERANCE) {

                    matchingRow = row;
                    break;
                }
            }

            if (matchingRow == null) {
                matchingRow =
                        new ArrayList<>();

                rows.add(
                        matchingRow
                );
            }

            matchingRow.add(
                    image
            );
        }

        for (List<ExtractedImage> row :
                rows) {

            row.sort(
                    Comparator.comparing(
                            ExtractedImage::getX
                    )
            );
        }

        rows.sort(
                Comparator.comparingDouble(
                        row ->
                                getImageFlowY(
                                        page,
                                        row.getFirst()
                                )
                )
        );

        return rows;
    }

    private float columnContentWidth(
            PageMargins pageMargins,
            PageExtraction page,
            ColumnRegion region,
            boolean rightColumn
    ) {
        float pageWidth = pageContentWidth(pageMargins, page);
        float splitX = region.splitX();
        float halfGap = determineColumnGap(page, region) / 2f;

        if (rightColumn) {
            return Math.max(
                    1f,
                    pageWidth - (splitX - pageMargins.left()) - halfGap
            );
        }

        return Math.max(
                1f,
                splitX - pageMargins.left() - halfGap
        );
    }

    private boolean imageInCurrentColumn(
            PageExtraction page,
            ColumnRegion region,
            ExtractedImage image,
            boolean rightColumn
    ) {
        if (page == null || region == null || image == null) {
            return true;
        }

        float imageY =
                imageAnchorY(
                        page,
                        image
                );

        if (imageY < region.startY()
                || imageY > region.endY()) {
            return false;
        }

        float centerX =
                image.getX() + image.getWidth() / 2f;

        return rightColumn
                ? centerX > region.splitX()
                : centerX <= region.splitX();
    }

    private void writeFlowImageRow(
            XWPFDocument document,
            PageExtraction page,
            List<ExtractedImage> images,
            float contentLeft,
            float availableWidth
    ) {
        if (images == null
                || images.isEmpty()) {
            return;
        }

        if (images.size() == 1) {
            writeSingleFlowImage(
                    document,
                    page,
                    images.getFirst(),
                    contentLeft,
                    availableWidth
            );
            return;
        }

        writeSideBySideImages(
                document,
                page,
                images,
                contentLeft,
                availableWidth
        );
    }

    private void writeSingleFlowImage(
            XWPFDocument document,
            PageExtraction page,
            ExtractedImage image,
            float contentLeft,
            float availableWidth
    ) {
        float indent = imageIndent(page, image, contentLeft, availableWidth);
        float room = Math.max(1f, availableWidth - indent);
        float scale = image.getWidth() > room ? room / image.getWidth() : 1f;

        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setIndentationLeft(Math.round(indent * TWIPS_PER_POINT));

        XWPFRun run = paragraph.createRun();

        try {
            run.addPicture(
                    new ByteArrayInputStream(image.getData()),
                    pictureType(image),
                    image.getImageName() == null ? "image" : image.getImageName(),
                    Units.toEMU(image.getWidth() * scale),
                    Units.toEMU(image.getHeight() * scale)
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to insert image '" + image.getImageName() + "'.",
                    e
            );
        }
    }

    private int pictureType(
            ExtractedImage image
    ) {
        return switch (image.getMimeType()) {

            case "image/jpeg" ->
                    Document.PICTURE_TYPE_JPEG;

            case "image/png" ->
                    Document.PICTURE_TYPE_PNG;

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported image type: "
                                    + image.getMimeType()
                    );
        };
    }

    private void writeSideBySideImages(
            XWPFDocument document,
            PageExtraction page,
            List<ExtractedImage> images,
            float contentLeft,
            float availableWidth
    ) {
        List<ExtractedImage> slots = new ArrayList<>();
        List<Float> slotWidths = new ArrayList<>();
        float cursor = imageLeft(page, images.getFirst());

        for (int i = 0; i < images.size(); i++) {
            ExtractedImage image = images.get(i);
            float left = imageLeft(page, image);
            float gap = left - cursor;
            if (i > 0 && gap > 1f) {
                slots.add(null);
                slotWidths.add(gap);
            }
            slots.add(image);
            slotWidths.add(Math.max(1f, image.getWidth()));
            cursor = Math.max(cursor, left + image.getWidth());
        }

        float total = 0f;
        for (float width : slotWidths) {
            total += width;
        }

        float indent = imageIndent(page, images.getFirst(), contentLeft, availableWidth);
        float room = Math.max(1f, availableWidth - indent);
        float scale = total > room ? room / total : 1f;

        XWPFTable table = document.createTable(1, slots.size());
        table.setWidthType(TableWidthType.DXA);
        table.setWidth(toTwips(total * scale).toString());

        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }
        var layout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        var tableIndent = tblPr.isSetTblInd() ? tblPr.getTblInd() : tblPr.addNewTblInd();
        tableIndent.setType(STTblWidth.DXA);
        tableIndent.setW(toTwips(indent));
        var cellMar = tblPr.isSetTblCellMar() ? tblPr.getTblCellMar() : tblPr.addNewTblCellMar();
        zeroTableEdge(cellMar.isSetLeft() ? cellMar.getLeft() : cellMar.addNewLeft());
        zeroTableEdge(cellMar.isSetRight() ? cellMar.getRight() : cellMar.addNewRight());
        zeroTableEdge(cellMar.isSetTop() ? cellMar.getTop() : cellMar.addNewTop());
        zeroTableEdge(cellMar.isSetBottom() ? cellMar.getBottom() : cellMar.addNewBottom());

        XWPFTableRow row = table.getRow(0);
        for (int i = 0; i < slots.size(); i++) {
            float cellWidth = slotWidths.get(i) * scale;
            XWPFTableCell cell = row.getCell(i);
            cell.setWidthType(TableWidthType.DXA);
            cell.setWidth(toTwips(cellWidth).toString());

            ExtractedImage image = slots.get(i);
            if (image == null) {
                continue;
            }

            XWPFParagraph paragraph = cell.getParagraphArray(0);
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(0);
            paragraph.setAlignment(ParagraphAlignment.LEFT);

            try {
                paragraph.createRun().addPicture(
                        new ByteArrayInputStream(image.getData()),
                        pictureType(image),
                        image.getImageName() == null ? "image-" + i : image.getImageName(),
                        Units.toEMU(image.getWidth() * scale),
                        Units.toEMU(image.getHeight() * scale)
                );
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to insert image '" + image.getImageName() + "'.",
                        e
                );
            }
        }

        removeTableBorders(table);
    }

    private void removeTableBorders(
            XWPFTable table
    ) {
        CTTblPr tblPr =
                table.getCTTbl()
                        .getTblPr();

        if (tblPr == null) {
            tblPr =
                    table.getCTTbl()
                            .addNewTblPr();
        }

        var borders =
                tblPr.isSetTblBorders()
                        ? tblPr.getTblBorders()
                        : tblPr.addNewTblBorders();

        setNilBorder(borders, "top");
        setNilBorder(borders, "bottom");
        setNilBorder(borders, "left");
        setNilBorder(borders, "right");
        setNilBorder(borders, "insideH");
        setNilBorder(borders, "insideV");
    }

    private void setNilBorder(
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders borders,
            String edgeName
    ) {
        switch (edgeName) {
            case "top" -> {
                if (borders.isSetTop()) {
                    borders.getTop().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                } else {
                    borders.addNewTop().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                }
            }
            case "bottom" -> {
                if (borders.isSetBottom()) {
                    borders.getBottom().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                } else {
                    borders.addNewBottom().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                }
            }
            case "left" -> {
                if (borders.isSetLeft()) {
                    borders.getLeft().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                } else {
                    borders.addNewLeft().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                }
            }
            case "right" -> {
                if (borders.isSetRight()) {
                    borders.getRight().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                } else {
                    borders.addNewRight().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                }
            }
            case "insideH" -> {
                if (borders.isSetInsideH()) {
                    borders.getInsideH().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                } else {
                    borders.addNewInsideH().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                }
            }
            case "insideV" -> {
                if (borders.isSetInsideV()) {
                    borders.getInsideV().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                } else {
                    borders.addNewInsideV().setVal(
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL
                    );
                }
            }
            default -> {
            }
        }
    }

    private boolean shouldSuppressRunningBlock(
            PageExtraction page,
            StructuredBlock block,
            Set<String> repeatedHeaders,
            Set<String> repeatedFooters,
            boolean repeatedPageNumbers
    ) {
        if (block == null) {
            return false;
        }

        String text =
                normalizedBlockText(block);

        String headerRunningText =
                normalizedRunningText(
                        block,
                        true
                );

        String footerRunningText =
                normalizedRunningText(
                        block,
                        false
                );

        if (text.isBlank()) {
            return false;
        }

        if (repeatedPageNumbers
                && isPageNumberBlock(page, block)) {
            return true;
        }

        float pageHeight =
                page.getCropHeight() > 0f
                        ? page.getCropHeight()
                        : DEFAULT_PAGE_HEIGHT_POINTS;

        float blockTop =
                block.getY();

        float blockBottom =
                block.getY()
                        + block.getHeight();

        boolean inHeaderBand =
                blockTop <= pageHeight * 0.18f;

        boolean inFooterBand =
                blockBottom >= pageHeight * 0.82f;

        boolean plausibleRunningBlock =
                isPlausibleRunningBlock(
                        page,
                        block
                );

        if (plausibleRunningBlock
                && inHeaderBand
                && containsEquivalentRunningText(
                repeatedHeaders,
                text,
                headerRunningText,
                true
        )) {
            return true;
        }

        return plausibleRunningBlock
                && inFooterBand
                && containsEquivalentRunningText(
                repeatedFooters,
                text,
                footerRunningText,
                false
        );
    }

    private PageMargins determinePageMargins(
            List<PageExtraction> pages,
            Set<String> repeatedHeaders,
            Set<String> repeatedFooters,
            boolean repeatedPageNumbers
    ) {
        List<Float> leftMargins = new ArrayList<>();
        List<Float> rightMargins = new ArrayList<>();
        List<Float> topMargins = new ArrayList<>();
        List<Float> bottomMargins = new ArrayList<>();

        for (PageExtraction page : pages) {
            float pageWidth = page.getCropWidth() > 0
                    ? page.getCropWidth()
                    : DEFAULT_PAGE_WIDTH_POINTS;
            float pageHeight = page.getCropHeight() > 0
                    ? page.getCropHeight()
                    : DEFAULT_PAGE_HEIGHT_POINTS;
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxRight = 0f;
            float maxBottom = 0f;
            boolean hasContent = false;

            for (StructuredBlock block : page.getStructuredBlocks()) {
                if (shouldSuppressRunningBlock(
                        page,
                        block,
                        repeatedHeaders,
                        repeatedFooters,
                        repeatedPageNumbers
                )) {
                    continue;
                }

                minX = Math.min(minX, block.getX());
                minY = Math.min(minY, block.getY());
                maxRight = Math.max(maxRight, block.getX() + block.getWidth());
                maxBottom = Math.max(maxBottom, block.getY() + block.getHeight());
                hasContent = true;
            }

            if (hasContent) {
                leftMargins.add(clampMargin(minX));
                rightMargins.add(clampMargin(pageWidth - maxRight));
                topMargins.add(clampMargin(minY));
                bottomMargins.add(clampMargin(pageHeight - maxBottom));
            }
        }

        return new PageMargins(
                stableMargin(leftMargins),
                stableMargin(rightMargins),
                stableMargin(topMargins),
                stableMargin(bottomMargins)
        );
    }

    private float stableMargin(List<Float> margins) {
        if (margins.isEmpty()) {
            return DEFAULT_MARGIN_POINTS;
        }

        margins.sort(Float::compare);
        float median = margins.get(margins.size() / 2);
        return median > 0f ? median : DEFAULT_MARGIN_POINTS;
    }

    private float clampMargin(float margin) {
        if (!Float.isFinite(margin)) {
            return DEFAULT_MARGIN_POINTS;
        }

        return Math.clamp(margin, 0f, 144f);
    }

    private record PageMargins(
            float left,
            float right,
            float top,
            float bottom
    ) {
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

        boolean backgroundImage =
                isBackgroundImage(page, image);

        if (placement.extentXEmu() <= 0 || placement.extentYEmu() <= 0) {
            log.warn(
                    "Dropping image '{}' on page {}: computed non-positive "
                            + "extent (extentXEmu={}, extentYEmu={}).",
                    image.getImageName(),
                    page.getPageIndex(),
                    placement.extentXEmu(),
                    placement.extentYEmu()
            );
            return;
        }

        try {
            int pictureType =
                    switch (image.getMimeType()) {
                        case "image/jpeg" -> Document.PICTURE_TYPE_JPEG;

                        case "image/png" -> Document.PICTURE_TYPE_PNG;

                        default -> throw new IllegalArgumentException(
                                "Unsupported image type: "
                                        + image.getMimeType()
                        );
                    };

            String relationId =
                    carrier.getDocument().addPictureData(
                            image.getData(),
                            pictureType
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
                    placement,
                    backgroundImage
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
            ImagePositionMapper.Placement placement,
            boolean backgroundImage
    ) {
        return """
                <w:drawing xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <wp:anchor xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                             xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                             xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                             distT="0" distB="0" distL="0" distR="0" simplePos="0"
                             relativeHeight="%1$d" behindDoc="%10$s" locked="0" layoutInCell="1" allowOverlap="1">
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
                placement.flipHorizontal() ? "1" : "0",
                backgroundImage ? "1" : "0"
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
            float bodyFontSize,
            Map<String, XWPFFootnote> footnotesByKey,
            int spacingBefore,
            float availableWidth,
            float contentLeft
    ) {

        if (block.getType() == BlockType.TABLE) {
            writeTable(document, block, footnotesByKey, availableWidth, contentLeft);
            return;
        }

        XWPFParagraph paragraph =
                document.createParagraph();

        IndexEntry indexEntry = parseIndexEntry(block);

        applyParagraphSpacing(
                paragraph,
                indexEntry == null ? spacingBefore : 0
        );

        if (indexEntry != null) {
            configureIndexEntryTab(paragraph, availableWidth);
        }

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
            pinParagraphFont(paragraph, block);

            return;
        }

        if (indexEntry != null) {
            XWPFRun run =
                    paragraph.createRun();

            run.setText(
                    indexEntry.title()
            );

            run.addTab();

            run.setText(
                    indexEntry.pageNumber()
            );

            if (block.getSpans() != null
                    && !block.getSpans().isEmpty()) {
                applyFormatting(
                        run,
                        block.getSpans().getFirst()
                );
            }

            return;
        }

        writeRuns(paragraph, block);
        pinParagraphFont(paragraph, block);
    }

    private IndexEntry parseIndexEntry(StructuredBlock block) {
        if (block.getType() != BlockType.PARAGRAPH || block.getText() == null) {
            return null;
        }

        var matcher = INDEX_ENTRY_PATTERN.matcher(block.getText().strip());
        return matcher.matches()
                ? new IndexEntry(matcher.group(1).strip(), matcher.group(2))
                : null;
    }

    private boolean isIndexLevelMarker(StructuredBlock block) {
        return block.getType() == BlockType.PARAGRAPH
                && block.getText() != null
                && block.getText().strip().matches("\\d+(?:\\.\\d+)*");
    }

    private boolean isAdjacentIndexEntry(
            StructuredBlock marker,
            StructuredBlock entry
    ) {
        return entry.getType() == BlockType.PARAGRAPH
                && entry.getText() != null
                && entry.getPageIndex() == marker.getPageIndex()
                && entry.getY() >= marker.getY()
                && entry.getY() - marker.getY() <= Math.max(48f, marker.getHeight() * 3f);
    }

    private StructuredBlock mergeIndexLevelMarker(
            StructuredBlock marker,
            StructuredBlock entry
    ) {
        StructuredBlock merged = new StructuredBlock(
                marker.getPageIndex(),
                BlockType.PARAGRAPH,
                marker.getText().strip() + " " + entry.getText().strip(),
                marker.getX(),
                marker.getY(),
                Math.max(
                        marker.getWidth(),
                        entry.getX() + entry.getWidth() - marker.getX()
                ),
                Math.max(
                        marker.getHeight(),
                        entry.getY() + entry.getHeight() - marker.getY()
                ),
                entry.getSpans()
        );
        merged.setAlignment(entry.getAlignment());
        return merged;
    }

    private void configureIndexEntryTab(
            XWPFParagraph paragraph,
            float availableWidth
    ) {
        CTPPr pPr = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr()
                : paragraph.getCTP().addNewPPr();
        var spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(BigInteger.ZERO);
        spacing.setLine(BigInteger.valueOf(240));
        spacing.setLineRule(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule.AUTO
        );
        var tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
        var tab = tabs.addNewTab();
        tab.setVal(STTabJc.RIGHT);
        tab.setLeader(STTabTlc.DOT);
        tab.setPos(toTwips(Math.max(1f, availableWidth)));
    }

    private record IndexEntry(String title, String pageNumber) {
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

    /**
     * Copies the source font onto the paragraph mark. A Heading style would
     * otherwise replace it with the theme heading face and size.
     *
     * @param paragraph heading paragraph whose runs already carry the source font
     * @param block heading block
     */
    private void pinParagraphFont(
            XWPFParagraph paragraph,
            StructuredBlock block
    ) {
        String family = blockFontFamily(block);
        float size = 0f;
        if (block.getSpans() != null) {
            for (TextSpan span : block.getSpans()) {
                if (span.getFontSize() > size) {
                    size = span.getFontSize();
                }
            }
        }
        if (family == null && size <= 0f) {
            return;
        }

        CTPPr properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr()
                : paragraph.getCTP().addNewPPr();
        var runProperties = properties.isSetRPr()
                ? properties.getRPr()
                : properties.addNewRPr();
        if (family != null) {
            var fonts = runProperties.sizeOfRFontsArray() == 0
                    ? runProperties.addNewRFonts()
                    : runProperties.getRFontsArray(0);
            fonts.setAscii(family);
            fonts.setHAnsi(family);
            fonts.setCs(family);
        }
        if (size > 0f) {
            var fontSize = runProperties.sizeOfSzArray() == 0
                    ? runProperties.addNewSz()
                    : runProperties.getSzArray(0);
            fontSize.setVal(BigInteger.valueOf(Math.round(size * 2f)));
        }
    }

    /**
     * How far a component sits to the right of the current content edge.
     * A negative value pulls it into the page margin, never past the page edge.
     *
     * @param componentLeft left edge of the component in page points
     * @param contentLeft left edge of the current column or body
     * @param availableWidth width of that content area
     * @return indent in points
     */
    private float componentOffset(
            float componentLeft,
            float contentLeft,
            float availableWidth
    ) {
        float indent = componentLeft - contentLeft;
        float minIndent = -Math.max(0f, contentLeft);
        float maxIndent = Math.max(minIndent, availableWidth - 1f);
        return Math.clamp(indent, minIndent, maxIndent);
    }

    private void applyTableIndent(XWPFTable table, float indentPoints) {
        if (Math.abs(indentPoints) < 1f) {
            return;
        }
        CTTblPr properties = table.getCTTbl().getTblPr();
        if (properties == null) {
            properties = table.getCTTbl().addNewTblPr();
        }
        var indent = properties.isSetTblInd() ? properties.getTblInd() : properties.addNewTblInd();
        indent.setType(STTblWidth.DXA);
        indent.setW(toTwips(indentPoints));
    }

    private void clearTableCellMargins(XWPFTable table) {
        CTTblPr properties = table.getCTTbl().getTblPr();
        if (properties == null) {
            properties = table.getCTTbl().addNewTblPr();
        }
        var cellMar = properties.isSetTblCellMar()
                ? properties.getTblCellMar()
                : properties.addNewTblCellMar();
        zeroTableEdge(cellMar.isSetLeft() ? cellMar.getLeft() : cellMar.addNewLeft());
        zeroTableEdge(cellMar.isSetRight() ? cellMar.getRight() : cellMar.addNewRight());
        zeroTableEdge(cellMar.isSetTop() ? cellMar.getTop() : cellMar.addNewTop());
        zeroTableEdge(cellMar.isSetBottom() ? cellMar.getBottom() : cellMar.addNewBottom());
    }

    /**
     * Keeps a PDF row at least as tall as the source, and lets the row grow
     * when the font needs more room than an exact height would allow.
     *
     * @param tableRow row being sized
     * @param heightPoints source row height in points
     */
    private void setRowHeightAtLeast(XWPFTableRow tableRow, float heightPoints) {
        var rowProperties = tableRow.getCtRow().isSetTrPr()
                ? tableRow.getCtRow().getTrPr()
                : tableRow.getCtRow().addNewTrPr();
        var rowHeight = rowProperties.sizeOfTrHeightArray() == 0
                ? rowProperties.addNewTrHeight()
                : rowProperties.getTrHeightArray(0);
        rowHeight.setVal(toTwips(Math.max(1f, heightPoints)));
        rowHeight.setHRule(STHeightRule.AT_LEAST);
    }

    private void writeTable(
            XWPFDocument document,
            StructuredBlock block,
            Map<String, XWPFFootnote> footnotesByKey,
            float availableWidth,
            float contentLeft
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

        float indent = componentOffset(block.getX(), contentLeft, availableWidth);
        float room = Math.max(1f, availableWidth - indent);

        XWPFTable table =
                document.createTable(rows.size(), columnCount);

        List<Float> columnWidths = scaleTableColumnWidths(
                block.getColumnWidths(),
                room
        );
        List<Float> rowHeights = block.getRowHeights();

        applyTableGrid(table, columnWidths, columnCount);
        configureFixedTableLayout(table, columnWidths);
        applyTableIndent(table, indent);
        clearTableCellMargins(table);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {

            XWPFTableRow tableRow =
                    table.getRow(rowIndex);

            if (rowHeights != null && rowIndex < rowHeights.size()) {
                setRowHeightAtLeast(tableRow, rowHeights.get(rowIndex));
            }

            List<TableCell> row =
                    rows.get(rowIndex);

            for (int columnIndex = row.size() - 1;
                 columnIndex >= 0;
                 columnIndex--) {

                boolean removed =
                        writeTableCell(rows, tableRow, rowIndex, columnIndex, footnotesByKey);

                if (removed) {
                    continue;
                }

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

    private void configureFixedTableLayout(
            XWPFTable table,
            List<Float> columnWidths
    ) {
        if (columnWidths == null || columnWidths.isEmpty()) {
            return;
        }

        float tableWidth = columnWidths.stream()
                .filter(width -> width != null && width > 0f)
                .reduce(0f, Float::sum);

        if (tableWidth <= 0f) {
            return;
        }

        table.setWidthType(TableWidthType.DXA);
        table.setWidth(toTwips(tableWidth).toString());

        CTTblPr tableProperties = table.getCTTbl().getTblPr();
        if (tableProperties == null) {
            tableProperties = table.getCTTbl().addNewTblPr();
        }
        var layout = tableProperties.isSetTblLayout()
                ? tableProperties.getTblLayout()
                : tableProperties.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
    }

    private float pageContentWidth(
            PageMargins pageMargins,
            PageExtraction page
    ) {
        float pageWidth = page.getRotation() == 90 || page.getRotation() == 270
                ? page.getCropHeight()
                : page.getCropWidth();

        if (pageWidth <= 0f) {
            pageWidth = DEFAULT_PAGE_WIDTH_POINTS;
        }

        return Math.max(1f, pageWidth - pageMargins.left() - pageMargins.right());
    }

    private List<Float> scaleTableColumnWidths(
            List<Float> columnWidths,
            float availableWidth
    ) {
        if (columnWidths == null || columnWidths.isEmpty()) {
            return columnWidths;
        }

        float totalWidth = columnWidths.stream()
                .filter(width -> width != null && width > 0f)
                .reduce(0f, Float::sum);

        if (totalWidth <= 0f || totalWidth <= availableWidth) {
            return columnWidths;
        }

        float scale = availableWidth / totalWidth;
        return columnWidths.stream()
                .map(width -> width == null ? 0f : width * scale)
                .toList();
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
        if (columnWidths == null || columnWidths.isEmpty()) {
            return;
        }

        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) {
            grid = table.getCTTbl().addNewTblGrid();
        }

        List<CTTblGridCol> gridColumns = grid.getGridColList();

        for (int column = 0; column < columnCount; column++) {

            float widthPoints =
                    column < columnWidths.size()
                            ? columnWidths.get(column)
                            : 0f;

            if (column >= gridColumns.size()) {
                gridColumns.add(grid.addNewGridCol());
            }

            gridColumns.get(column).setW(toTwips(widthPoints));
        }
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

    private boolean writeTableCell(
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

            setCellText(
                    tableCell,
                    cell.text(),
                    cell.spans(),
                    cell.footnoteKey(),
                    footnotesByKey
            );

            if (cell.columnSpan() > 1) {
                setGridSpan(tableCell, cell.columnSpan());
            }

            if (cell.rowSpan() > 1) {
                setVerticalMerge(tableCell, STMerge.RESTART);
            }

            return false;
        }

        TableCell anchor =
                rows.get(cell.row()).get(cell.column());

        boolean sameRowAsAnchor =
                cell.row() == rowIndex;

        if (sameRowAsAnchor) {
            tableRow.removeCell(columnIndex);
            return true;
        }

        if (columnIndex != cell.column()) {
            tableRow.removeCell(columnIndex);
            return true;
        }

        XWPFTableCell tableCell =
                tableRow.getCell(columnIndex);

        setCellText(tableCell, "", List.of(), null, footnotesByKey);
        setVerticalMerge(tableCell, STMerge.CONTINUE);

        if (anchor.columnSpan() > 1) {
            setGridSpan(tableCell, anchor.columnSpan());
        }

        return false;
    }

    private void setCellText(
            XWPFTableCell tableCell,
            String text,
            List<TextSpan> spans,
            String footnoteKey,
            Map<String, XWPFFootnote> footnotesByKey
    ) {

        XWPFParagraph cellParagraph =
                tableCell.getParagraphArray(0) != null
                        ? tableCell.getParagraphArray(0)
                        : tableCell.addParagraph();

        cellParagraph.setSpacingBefore(0);
        cellParagraph.setSpacingAfter(0);
        cellParagraph.setSpacingBetween(1.0, LineSpacingRule.AUTO);

        if (footnoteKey != null) {
            writeFootnoteReference(cellParagraph, footnoteKey, footnotesByKey);
        }

        if (spans == null || spans.isEmpty()) {
            XWPFRun run = cellParagraph.createRun();
            run.setText(text);
            return;
        }

        String previousText = null;
        TextSpan previousSpan = null;

        for (TextSpan span : spans) {
            if (span.getText() == null || span.getText().isEmpty()) {
                continue;
            }

            String spanText = span.getText();
            if (needsRunSpace(previousSpan, previousText, span, spanText)) {
                spanText = " " + spanText;
            }

            XWPFRun run = cellParagraph.createRun();
            run.setText(spanText);
            applyFormatting(run, span);
            previousText = spanText;
            previousSpan = span;
        }
    }

    private boolean needsRunSpace(
            TextSpan previousSpan,
            String previousText,
            TextSpan span,
            String text
    ) {
        if (previousText == null
                || previousText.isEmpty()
                || text == null
                || text.isEmpty()
                || Character.isWhitespace(previousText.charAt(previousText.length() - 1))
                || Character.isWhitespace(text.charAt(0))) {
            return false;
        }

        if (span.isWordSeparatorBefore()) {
            return true;
        }

        if (previousSpan == null) {
            return false;
        }

        float gap = span.getX() - (previousSpan.getX() + previousSpan.getWidth());
        return gap > 1.2f;
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
        TextSpan previousSpan = null;

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

            if (needsRunSpace(previousSpan, previousText, span, text)) {
                text = " " + text;
            }

            XWPFRun run =
                    paragraph.createRun();

            run.setText(text);

            applyFormatting(run, span);

            previousText = text;
            previousSpan = span;
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

    private int calculateSpacingBefore(
            StructuredBlock previousBlock,
            StructuredBlock currentBlock
    ) {
        if (previousBlock == null
                || currentBlock == null) {
            return 0;
        }

        if (previousBlock.getType() == BlockType.LIST_ITEM
                && currentBlock.getType() == BlockType.LIST_ITEM
                && previousBlock.getListType()
                == currentBlock.getListType()) {
            return 0;
        }

        float previousBottom =
                previousBlock.getY()
                        + previousBlock.getHeight();

        float sourceGap =
                currentBlock.getY()
                        - previousBottom;

        if (sourceGap <= 0f) {
            return 0;
        }

        float lineHeight = Math.max(
                previousBlock.getHeight(),
                currentBlock.getHeight()
        );
        float baselineDelta = currentBlock.getY() - previousBlock.getY();
        if (baselineDelta > 0f
                && baselineDelta <= Math.max(16f, lineHeight * 2.2f)) {
            return 0;
        }
        float naturalSpacingAllowance = Math.max(4f, lineHeight * 0.85f);

        float effectiveGap =
                Math.max(
                        0f,
                        sourceGap
                                - naturalSpacingAllowance
                );

        int spacingTwips =
                Math.round(
                        effectiveGap * 20f
                );

        return Math.min(
                spacingTwips,
                720
        );
    }

    /**
     * Page X where the current column or body text starts. Image indents are
     * measured from this edge so a picture keeps its PDF horizontal position.
     *
     * @param pageMargins margins of the Word section
     * @param region active column region, or null on a single-column stretch
     * @param rightColumn true when the cursor is in the right column
     * @return left edge in PDF points
     */
    private float flowContentLeft(
            PageMargins pageMargins,
            ColumnRegion region,
            boolean rightColumn
    ) {
        if (region == null || !rightColumn) {
            return pageMargins.left();
        }
        return region.splitX();
    }

    /**
     * Distance from the content edge to the image's left edge, clamped so the
     * picture still fits in the column.
     *
     * @param page page that owns the image
     * @param image image being placed
     * @param contentLeft left edge of the current content area
     * @param availableWidth width of that content area
     * @return left indent in points
     */
    private float imageIndent(
            PageExtraction page,
            ExtractedImage image,
            float contentLeft,
            float availableWidth
    ) {
        float indent = imageLeft(page, image) - contentLeft;
        float minIndent = -Math.max(0f, contentLeft);
        float maxIndent = Math.max(minIndent, availableWidth - 1f);
        return Math.clamp(indent, minIndent, maxIndent);
    }

    /**
     * Left edge of the image in top-down page points, matching text X.
     *
     * @param page page that owns the image
     * @param image image being measured
     * @return left edge in points
     */
    private float imageLeft(
            PageExtraction page,
            ExtractedImage image
    ) {
        return (float) (
                ImagePositionMapper.map(image, page).offsetXEmu()
                        / EMU_PER_POINT
        );
    }

    /**
     * Vertical center of the image in the same top-down space as text
     * baselines. Ordering on the center keeps a figure with the paragraph it
     * sits beside, instead of inserting it a full image-height too early.
     *
     * @param page page that owns the image
     * @param image image being measured
     * @return vertical center in points from the top of the page
     */
    private float imageAnchorY(
            PageExtraction page,
            ExtractedImage image
    ) {
        return getImageFlowY(page, image)
                + Math.max(0f, image.getHeight()) / 2f;
    }

    private void zeroTableEdge(
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth edge
    ) {
        edge.setType(STTblWidth.DXA);
        edge.setW(BigInteger.ZERO);
    }

    private float getImageFlowY(
            PageExtraction page,
            ExtractedImage image
    ) {
        ImagePositionMapper.Placement placement =
                ImagePositionMapper.map(
                        image,
                        page
                );

        return (float) (
                placement.offsetYEmu()
                        / EMU_PER_POINT
        );
    }

    private boolean isBackgroundImage(
            PageExtraction page,
            ExtractedImage image
    ) {
        if (page == null || image == null) {
            return false;
        }

        if (page.getCropWidth() <= 0 || page.getCropHeight() <= 0) {
            return false;
        }

        float widthRatio =
                image.getWidth() / page.getCropWidth();

        float heightRatio =
                image.getHeight() / page.getCropHeight();

        if (widthRatio >= 0.80f && heightRatio >= 0.80f) {
            return true;
        }

        List<StructuredBlock> blocks = page.getStructuredBlocks();

        if (blocks.isEmpty()
                || widthRatio < 0.50f
                || heightRatio < 0.50f) {
            return false;
        }

        float imageTop = getImageFlowY(page, image);
        float imageBottom = imageTop + image.getHeight();
        float imageLeft = image.getX();
        float imageRight = image.getX() + image.getWidth();
        int coveredBlocks = 0;

        for (StructuredBlock block : blocks) {
            float blockCenterX = block.getX() + block.getWidth() / 2f;
            float blockCenterY = block.getY() + block.getHeight() / 2f;

            if (blockCenterX >= imageLeft
                    && blockCenterX <= imageRight
                    && blockCenterY >= imageTop
                    && blockCenterY <= imageBottom) {
                coveredBlocks++;
            }
        }

        return coveredBlocks >= Math.ceil(blocks.size() * 0.80f);
    }

}