package com.amalitech.backend.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a PDF font name into a family Microsoft Word can select.
 * Embedded names carry a subset tag and a style suffix
 * ({@code ABCDEF+TimesNewRomanPS-BoldMT}). Word stores the family and the
 * bold or italic flag separately, so the suffix is removed before lookup.
 */
public final class FontFamilyResolver {

    private static final Pattern SUBSET_PREFIX = Pattern.compile("^[A-Z]{6}\\+");
    private static final Pattern STYLE_SUFFIX = Pattern.compile(
            "(?i)(?:[-_\\s](?:bolditalic|boldoblique|boldmt|italicmt|psmt|bold|italic|oblique|regular|roman|mt|ps)"
                    + "|(?:psmt|boldmt|italicmt|mt|ps))$"
    );
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z])([A-Z])");

    private static final Map<String, String> FAMILIES = Map.ofEntries(
            Map.entry("helvetica", "Arial"),
            Map.entry("arial", "Arial"),
            Map.entry("liberationsans", "Arial"),
            Map.entry("nimbussans", "Arial"),
            Map.entry("nimbussanl", "Arial"),
            Map.entry("times", "Times New Roman"),
            Map.entry("timesnewroman", "Times New Roman"),
            Map.entry("liberationserif", "Times New Roman"),
            Map.entry("nimbusromno9l", "Times New Roman"),
            Map.entry("courier", "Courier New"),
            Map.entry("couriernew", "Courier New"),
            Map.entry("liberationmono", "Courier New"),
            Map.entry("nimbusmonl", "Courier New"),
            Map.entry("calibri", "Calibri"),
            Map.entry("cambria", "Cambria"),
            Map.entry("georgia", "Georgia"),
            Map.entry("verdana", "Verdana"),
            Map.entry("tahoma", "Tahoma"),
            Map.entry("trebuchetms", "Trebuchet MS"),
            Map.entry("comicsansms", "Comic Sans MS"),
            Map.entry("consolas", "Consolas"),
            Map.entry("segoeui", "Segoe UI"),
            Map.entry("garamond", "Garamond"),
            Map.entry("constantia", "Constantia"),
            Map.entry("corbel", "Corbel"),
            Map.entry("candara", "Candara"),
            Map.entry("symbol", "Symbol"),
            Map.entry("zapfdingbats", "Wingdings")
    );

    private FontFamilyResolver() {
    }

    /**
     * Resolves a PDF font name to a Word font family.
     *
     * @param pdfFontName name from the PDF font dictionary, or null
     * @return a Word family, or null when the name is missing
     */
    public static String resolve(String pdfFontName) {
        if (pdfFontName == null || pdfFontName.isBlank()) {
            return null;
        }

        String stripped = SUBSET_PREFIX.matcher(pdfFontName.trim()).replaceFirst("");
        stripped = stripped.replace(',', '-');

        String previous;
        int guard = 0;
        do {
            previous = stripped;
            stripped = STYLE_SUFFIX.matcher(stripped).replaceFirst("");
            guard++;
        } while (!stripped.equals(previous) && !stripped.isBlank() && guard < 4);

        if (stripped.isBlank()) {
            stripped = SUBSET_PREFIX.matcher(pdfFontName.trim()).replaceFirst("");
        }

        String mapped = FAMILIES.get(compact(stripped));
        if (mapped != null) {
            return mapped;
        }

        String spaced = CAMEL_BOUNDARY.matcher(stripped).replaceAll("$1 $2")
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        mapped = FAMILIES.get(compact(spaced));
        return mapped != null ? mapped : spaced;
    }

    private static String compact(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_]", "");
    }
}
