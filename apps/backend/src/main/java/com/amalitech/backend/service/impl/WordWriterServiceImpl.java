package com.amalitech.backend.service.impl;

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
import com.amalitech.backend.service.ListType;
import com.amalitech.backend.service.WordWriterService;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.apache.xmlbeans.XmlCursor;
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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabTlc;

import java.math.BigInteger;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WordWriterServiceImpl implements WordWriterService {

    private static final int TWIPS_PER_POINT = 20;

    private static final float DEFAULT_PAGE_WIDTH_POINTS = 612f;
    private static final float DEFAULT_PAGE_HEIGHT_POINTS = 792f;
    private static final float DEFAULT_MARGIN_POINTS = 36f;
    private static final float MAX_FLOW_VERTICAL_MARGIN_POINTS = 18f;

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

    private static final Pattern FONT_SUBSET_PREFIX =
            Pattern.compile("^[A-Z]{6}\\+");

    private static final Map<String, String> FONT_FAMILY_ALIASES = Map.ofEntries(
            Map.entry("helvetica", "Arial"),
            Map.entry("helvetica-bold", "Arial"),
            Map.entry("helvetica-oblique", "Arial"),
            Map.entry("helvetica-boldoblique", "Arial"),
            Map.entry("times-roman", "Times New Roman"),
            Map.entry("times-bold", "Times New Roman"),
            Map.entry("times-italic", "Times New Roman"),
            Map.entry("times-bolditalic", "Times New Roman"),
            Map.entry("courier", "Courier New"),
            Map.entry("courier-bold", "Courier New"),
            Map.entry("courier-oblique", "Courier New"),
            Map.entry("courier-boldoblique", "Courier New")
    );

    private void applyFormatting(
            XWPFRun run,
            TextSpan span
    ) {

        run.setBold(span.isBold());
        run.setItalic(span.isItalic());

        if (span.getFontName() != null
                && !span.getFontName().isBlank()) {
            String normalizedFont = FONT_SUBSET_PREFIX
                    .matcher(span.getFontName())
                    .replaceFirst("");
            String fontFamily = FONT_FAMILY_ALIASES.getOrDefault(
                    normalizedFont.toLowerCase(),
                    normalizedFont
            );
            run.setFontFamily(
                    fontFamily
            );
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

                        while (blockIndex + 1 < pageBlocks.size()) {

                            StructuredBlock nextBlock =
                                    pageBlocks.get(blockIndex + 1);

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
                                    block,
                                    nextBlock
                            )) {
                                break;
                            }

                            if (parseIndexEntry(block) != null) {
                                break;
                            }

                            block = mergeIndexLevelMarker(
                                    block,
                                    pageBlocks.get(++blockIndex)
                            );
                        }
                    }

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
                                pageContentWidth(pageMargins, page)
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
                            bodyFontSize,
                            footnotesByKey,
                            spacingBefore,
                            pageContentWidth(pageMargins, page)
                    );

                    previousBlock = block;
                }

                if (!page.getImages().isEmpty()) {
                    XWPFParagraph carrier = document.createParagraph();
                    carrier.setSpacingBefore(0);
                    carrier.setSpacingAfter(0);
                    carrier.setSpacingBetween(0.0, LineSpacingRule.EXACT);

                    for (ExtractedImage image : page.getImages()) {
                        insertFloatingImage(carrier, page, image, shapeId++);
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
                body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();

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

        float topMargin = Math.min(
                pageMargins.top(),
                MAX_FLOW_VERTICAL_MARGIN_POINTS
        );
        float bottomMargin = Math.min(
                pageMargins.bottom(),
                MAX_FLOW_VERTICAL_MARGIN_POINTS
        );

        pageMargin.setTop(toTwips(topMargin));
        pageMargin.setBottom(toTwips(bottomMargin));
        pageMargin.setLeft(toTwips(pageMargins.left()));
        pageMargin.setRight(toTwips(pageMargins.right()));
        pageMargin.setHeader(toTwips(12f));
        pageMargin.setFooter(toTwips(12f));
        pageMargin.setGutter(BigInteger.ZERO);

        if (headerBlock != null || footerBlock != null || pageNumberFooter) {
            XWPFHeaderFooterPolicy policy =
                    new XWPFHeaderFooterPolicy(document, sectPr);

            if (headerBlock != null) {
                writeHeaderFooter(
                        policy.createHeader(STHdrFtr.DEFAULT),
                        headerBlock,
                        false
                );
            }

            if (footerBlock != null || pageNumberFooter) {
                writeHeaderFooter(
                        policy.createFooter(STHdrFtr.DEFAULT),
                        footerBlock,
                        pageNumberFooter
                );
            }
        }
    }

    private void insertPageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        XWPFRun breakRun = paragraph.createRun();
        breakRun.setFontSize(1);
        breakRun.addBreak(BreakType.PAGE);
    }

    private boolean hasSamePageGeometry(
            PageExtraction first,
            PageExtraction second
    ) {
        return nearlyEqual(first.getCropX(), second.getCropX())
                && nearlyEqual(first.getCropY(), second.getCropY())
                && nearlyEqual(first.getCropWidth(), second.getCropWidth())
                && nearlyEqual(first.getCropHeight(), second.getCropHeight())
                && first.getRotation() == second.getRotation();
    }

    private boolean requiresExplicitPageBoundary(
            PageExtraction page,
            Set<String> repeatedHeaders,
            Set<String> repeatedFooters,
            boolean repeatedPageNumbers
    ) {
        if (!page.getImages().isEmpty()) {
            return true;
        }

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

            float pageHeight = page.getCropHeight() > 0
                    ? page.getCropHeight()
                    : DEFAULT_PAGE_HEIGHT_POINTS;
            if (block.getY() + block.getHeight() < pageHeight * 0.85f) {
                return false;
            }
        }

        return true;
    }

    private boolean nearlyEqual(float first, float second) {
        return Math.abs(first - second) <= 0.01f;
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
        Map<String, Set<Integer>> pageIndexesByText = new HashMap<>();

        for (PageExtraction page : pages) {
            float pageHeight = page.getCropHeight() > 0
                    ? page.getCropHeight()
                    : DEFAULT_PAGE_HEIGHT_POINTS;

            for (StructuredBlock block : page.getStructuredBlocks()) {
                boolean inBand = header
                        ? block.getY() <= pageHeight * 0.18f
                        : block.getY() + block.getHeight()
                        >= pageHeight * 0.82f;

                String text =
                        normalizedRunningText(block);
                if (inBand && !text.isBlank()
                        && !(isPageNumberText(text) && !header)) {
                    pageIndexesByText
                            .computeIfAbsent(text, ignored -> new HashSet<>())
                            .add(page.getPageIndex());
                }
            }
        }

        Set<String> repeated = new HashSet<>();
        for (Map.Entry<String, Set<Integer>> entry : pageIndexesByText.entrySet()) {
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

            String text =
                    normalizedRunningText(block);

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
            StructuredBlock block
    ) {
        String text =
                normalizedBlockText(block);

        return text
                .replaceFirst("\\s+\\d{1,4}$", "")
                .strip();
    }

    private boolean containsEquivalentRunningText(
            Set<String> repeatedBlocks,
            String text,
            String runningText
    ) {
        if (repeatedBlocks.contains(text)
                || repeatedBlocks.contains(runningText)) {
            return true;
        }

        for (String repeated : repeatedBlocks) {

            String normalizedRepeated =
                    repeated
                            .replace('\u00A0', ' ')
                            .replaceAll("\\s+", " ")
                            .replaceFirst("\\s+\\d{1,4}$", "")
                            .strip();

            if (normalizedRepeated.equals(runningText)) {
                return true;
            }
        }

        return false;
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

        String runningText =
                normalizedRunningText(block);

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

        if (inHeaderBand
                && containsEquivalentRunningText(
                repeatedHeaders,
                text,
                runningText
        )) {
            return true;
        }

        return inFooterBand
                && containsEquivalentRunningText(
                repeatedFooters,
                text,
                runningText
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
            float availableWidth
    ) {

        if (block.getType() == BlockType.TABLE) {
            writeTable(document, block, footnotesByKey, availableWidth);
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

    private void writeTable(
            XWPFDocument document,
            StructuredBlock block,
            Map<String, XWPFFootnote> footnotesByKey,
            float availableWidth
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

        List<Float> columnWidths = scaleTableColumnWidths(
                block.getColumnWidths(),
                availableWidth
        );
        List<Float> rowHeights = block.getRowHeights();

        applyTableGrid(table, columnWidths, columnCount);
        configureFixedTableLayout(table, columnWidths);

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

        setCellText(tableCell, "", List.of(), null, footnotesByKey);
        setVerticalMerge(tableCell, STMerge.CONTINUE);

        if (anchor.columnSpan() > 1) {
            setGridSpan(tableCell, anchor.columnSpan());
        }
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


        if (footnoteKey != null) {
            writeFootnoteReference(cellParagraph, footnoteKey, footnotesByKey);
        }

        if (spans == null || spans.isEmpty()) {
            XWPFRun run = cellParagraph.createRun();
            run.setText(text);
            return;
        }

        for (TextSpan span : spans) {
            if (span.getText() == null || span.getText().isEmpty()) {
                continue;
            }

            XWPFRun run = cellParagraph.createRun();
            run.setText(span.getText());
            applyFormatting(run, span);
        }
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
                && previousBlock.getListType() == currentBlock.getListType()) {
            return 0;
        }

        float previousBottom =
                previousBlock.getY()
                        + previousBlock.getHeight();

        float gap =
                currentBlock.getY()
                        - previousBottom;

        if (gap <= 0f) {
            return 0;
        }

        int spacingTwips =
                Math.round(gap * 20f);

        return Math.min(
                spacingTwips,
                720
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

        float imageRight = image.getX() + image.getWidth();
        float imageBottom = image.getY() + image.getHeight();
        int coveredBlocks = 0;

        for (StructuredBlock block : blocks) {
            float blockCenterX = block.getX() + block.getWidth() / 2f;
            float blockCenterY = block.getY() + block.getHeight() / 2f;

            if (blockCenterX >= image.getX()
                    && blockCenterX <= imageRight
                    && blockCenterY >= image.getY()
                    && blockCenterY <= imageBottom) {
                coveredBlocks++;
            }
        }

        return coveredBlocks >= Math.ceil(blocks.size() * 0.80f);
    }
}