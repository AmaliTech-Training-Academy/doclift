package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentMetricsServiceImpl implements DocumentMetricsService {

    private static final Pattern WORD_PATTERN =
            Pattern.compile(
                    "[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*"
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

    private String removeListMarker(String text) {

        if (text == null) {
            return "";
        }

        return LIST_MARKER_PATTERN
                .matcher(text)
                .replaceFirst("");
    }
    @Override
    public int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        Matcher matcher = WORD_PATTERN.matcher(text);

        int count = 0;

        while (matcher.find()) {
            count++;
        }

        return count;
    }

    private ListType resolveListType(
            XWPFParagraph paragraph
    ) {
        if (paragraph.getNumID() == null) {
            return null;
        }

        String format = paragraph.getNumFmt();

        if (format == null || format.isBlank()) {
            return null;
        }

        if ("bullet".equalsIgnoreCase(format)) {
            return ListType.UNORDERED;
        }

        return ListType.ORDERED;
    }

    @Override
    public int countSourceWords(
            PdfExtractionResult extractionResult
    ) {

        if (extractionResult == null
                || extractionResult.getPages() == null) {
            return 0;
        }

        int total = 0;

        for (PageExtraction page :
                extractionResult.getPages()) {

            if (page.getStructuredBlocks() == null) {
                continue;
            }

            for (StructuredBlock block :
                    page.getStructuredBlocks()) {

                String text =
                        block.getText();

                if (text == null || text.isBlank()) {
                    continue;
                }

                if (block.getType()
                        == BlockType.LIST_ITEM) {

                    text =
                            removeListMarker(text);
                }

                total += countWords(text);
            }
        }

        return total;
    }

    @Override
    public ListCountResult countSourceLists(
            PdfExtractionResult extractionResult
    ) {
        if (extractionResult == null) {
            return new ListCountResult(0, 0);
        }

        int ordered = 0;
        int unordered = 0;

        ListType activeType = null;

        for (PageExtraction page : extractionResult.getPages()) {

            if (page.getStructuredBlocks() == null) {
                continue;
            }


            for (StructuredBlock block : page.getStructuredBlocks()) {

                if (block.getType() != BlockType.LIST_ITEM) {
                    activeType = null;
                    continue;
                }

                ListType currentType =
                        block.getListType();

                if (currentType == null) {
                    activeType = null;
                    continue;
                }

                if (currentType != activeType) {

                    if (currentType == ListType.ORDERED) {
                        ordered++;
                    } else if (currentType == ListType.UNORDERED) {
                        unordered++;
                    }

                    activeType = currentType;
                }
            }
        }

        return new ListCountResult(
                ordered,
                unordered
        );
    }

    @Override
    public ListCountResult countOutputLists(
            byte[] docxBytes
    ) {
        if (docxBytes == null || docxBytes.length == 0) {
            return new ListCountResult(0, 0);
        }

        try (
                ByteArrayInputStream inputStream =
                        new ByteArrayInputStream(docxBytes);

                XWPFDocument document =
                        new XWPFDocument(inputStream)
        ) {
            int ordered = 0;
            int unordered = 0;

            ListType activeType = null;

            for (XWPFParagraph paragraph :
                    document.getParagraphs()) {

                if (paragraph.getNumID() == null) {
                    activeType = null;
                    continue;
                }

                ListType currentType =
                        resolveListType(paragraph);

                if (currentType == null) {
                    activeType = null;
                    continue;
                }

                if (currentType != activeType) {

                    if (currentType == ListType.ORDERED) {
                        ordered++;
                    } else {
                        unordered++;
                    }

                    activeType = currentType;
                }
            }

            return new ListCountResult(
                    ordered,
                    unordered
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to inspect generated Word document.",
                    e
            );
        }
    }

    @Override
    public int countOutputWords(byte[] docxBytes) {
        if (docxBytes == null || docxBytes.length == 0) {
            return 0;
        }

        try (
                ByteArrayInputStream inputStream =
                        new ByteArrayInputStream(docxBytes);

                XWPFDocument document =
                        new XWPFDocument(inputStream)
        ) {
            int totalWords = 0;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                totalWords += countWords(paragraph.getText());
            }

            for (XWPFTable table : document.getTables()) {
                totalWords += countWords(table.getText());
            }

            return totalWords;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read generated Word document.",
                    e
            );
        }
    }

    @Override
    public int countHeadings(PdfExtractionResult extractionResult) {
        if (extractionResult == null
                || extractionResult.getPages() == null) {
            return 0;
        }

        int total = 0;

        for (PageExtraction page : extractionResult.getPages()) {

            if (page.getStructuredBlocks() == null) {
                continue;
            }

            for (StructuredBlock block : page.getStructuredBlocks()) {
                if (block.getType() == BlockType.HEADING) {
                    total++;
                }
            }
        }

        return total;
    }

    private float representativeFontSize(StructuredBlock block) {
        if (block.getSpans() == null || block.getSpans().isEmpty()) {
            return 0f;
        }

        float max = 0f;

        for (TextSpan span : block.getSpans()) {
            max = Math.max(max, span.getFontSize());
        }

        return max;
    }

    private float normalizedFontSize(StructuredBlock block) {
        float raw = representativeFontSize(block);
        return Math.round(raw * 2f) / 2f;
    }

    @Override
    public HeadingLevelCountResult countHeadingLevels(
            PdfExtractionResult extractionResult
    ) {
        if (extractionResult == null
                || extractionResult.getPages() == null) {
            return new HeadingLevelCountResult(0, 0, 0);
        }

        List<StructuredBlock> headings = new ArrayList<>();

        for (PageExtraction page : extractionResult.getPages()) {

            if (page.getStructuredBlocks() == null) {
                continue;
            }

            for (StructuredBlock block : page.getStructuredBlocks()) {
                if (block.getType() == BlockType.HEADING) {
                    headings.add(block);
                }
            }
        }

        if (headings.isEmpty()) {
            return new HeadingLevelCountResult(0, 0, 0);
        }

        List<Float> distinctSizes = headings.stream()
                .map(this::normalizedFontSize)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        int levelOne = 0;
        int levelTwo = 0;
        int levelThree = 0;

        for (StructuredBlock heading : headings) {
            int rank = distinctSizes.indexOf(normalizedFontSize(heading));

            if (rank == 0) {
                levelOne++;
            } else if (rank == 1) {
                levelTwo++;
            } else {
                levelThree++;
            }
        }

        return new HeadingLevelCountResult(levelOne, levelTwo, levelThree);
    }

    @Override
    public int countTables(PdfExtractionResult extractionResult) {
        if (extractionResult == null
                || extractionResult.getPages() == null) {
            return 0;
        }

        int total = 0;

        for (PageExtraction page : extractionResult.getPages()) {
            total += page.getTableCount();
        }

        return total;
    }

    @Override
    public int countImages(PdfExtractionResult extractionResult) {
        if (extractionResult == null
                || extractionResult.getPages() == null) {
            return 0;
        }

        int total = 0;

        for (PageExtraction page : extractionResult.getPages()) {
            if (page.getImages() != null) {
                total += page.getImages().size();
            }
        }

        return total;
    }

    @Override
    public int countMultiColumnPages(PdfExtractionResult extractionResult) {
        if (extractionResult == null
                || extractionResult.getPages() == null) {
            return 0;
        }

        int total = 0;

        for (PageExtraction page : extractionResult.getPages()) {
            if (page.isMultiColumn()) {
                total++;
            }
        }

        return total;
    }
}