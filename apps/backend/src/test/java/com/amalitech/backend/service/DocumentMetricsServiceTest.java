package com.amalitech.backend.service;

import com.amalitech.backend.service.impl.DocumentMetricsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentMetricsServiceTest {

    private DocumentMetricsService documentMetricsService;

    @BeforeEach
    void setUp() {
        documentMetricsService =
                new DocumentMetricsServiceImpl();
    }

    @Test
    void shouldReturnZeroForNullText() {

        assertEquals(
                0,
                documentMetricsService.countWords(null)
        );
    }

    @Test
    void shouldReturnZeroForBlankText() {

        assertEquals(
                0,
                documentMetricsService.countWords("   ")
        );
    }

    @Test
    void shouldCountSimpleWords() {

        assertEquals(
                3,
                documentMetricsService.countWords(
                        "Hello from DocLift"
                )
        );
    }

    @Test
    void shouldIgnorePunctuation() {

        assertEquals(
                5,
                documentMetricsService.countWords(
                        "Hello, world! How are you?"
                )
        );
    }

    @Test
    void shouldHandleMultipleWhitespaceCharacters() {

        assertEquals(
                4,
                documentMetricsService.countWords(
                        "One   two\nthree\tfour"
                )
        );
    }

    @Test
    void shouldTreatHyphenatedWordAsOneWord() {

        assertEquals(
                2,
                documentMetricsService.countWords(
                        "PDF-to-Word converter"
                )
        );
    }

    @Test
    void shouldTreatApostropheWordAsOneWord() {

        assertEquals(
                2,
                documentMetricsService.countWords(
                        "don't stop"
                )
        );
    }
}