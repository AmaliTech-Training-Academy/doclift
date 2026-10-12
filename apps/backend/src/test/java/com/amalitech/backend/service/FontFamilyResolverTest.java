package com.amalitech.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FontFamilyResolverTest {

    @Test
    void mapsEmbeddedTimesAndCalibriNamesToWordFamilies() {
        assertEquals("Times New Roman", FontFamilyResolver.resolve("ABCDEF+TimesNewRomanPS-BoldMT"));
        assertEquals("Times New Roman", FontFamilyResolver.resolve("TimesNewRomanPSMT"));
        assertEquals("Calibri", FontFamilyResolver.resolve("Calibri-Bold"));
        assertEquals("Arial", FontFamilyResolver.resolve("ArialMT"));
        assertEquals("Times New Roman", FontFamilyResolver.resolve("Times-Roman"));
        assertEquals("Arial", FontFamilyResolver.resolve("Helvetica-BoldOblique"));
        assertEquals("Courier New", FontFamilyResolver.resolve("CourierNewPSMT"));
        assertEquals("Segoe UI", FontFamilyResolver.resolve("SegoeUI"));
    }

    @Test
    void returnsNullWhenThePdfNameIsMissing() {
        assertNull(FontFamilyResolver.resolve(null));
        assertNull(FontFamilyResolver.resolve("  "));
    }
}
