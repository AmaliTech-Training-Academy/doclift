package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.JobFilenameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobFilenameResolverTest {

    private JobFilenameResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new JobFilenameResolver();
    }

    @Test
    void shouldReplacePdfExtensionWithDocx() {

        String result =
                resolver.buildOutputFilename(
                        "sample.pdf"
                );

        assertEquals(
                "sample.docx",
                result
        );
    }

    @Test
    void shouldReplaceAnyExistingExtensionWithDocx() {

        String result =
                resolver.buildOutputFilename(
                        "report.final.pdf"
                );

        assertEquals(
                "report.final.docx",
                result
        );
    }

    @Test
    void shouldAppendDocxWhenFilenameHasNoExtension() {

        String result =
                resolver.buildOutputFilename(
                        "sample"
                );

        assertEquals(
                "sample.docx",
                result
        );
    }

    @Test
    void shouldUseFallbackForNullFilename() {

        String result =
                resolver.buildOutputFilename(
                        null
                );

        assertEquals(
                "converted.docx",
                result
        );
    }

    @Test
    void shouldUseFallbackForBlankFilename() {

        String result =
                resolver.buildOutputFilename(
                        "   "
                );

        assertEquals(
                "converted.docx",
                result
        );
    }
}