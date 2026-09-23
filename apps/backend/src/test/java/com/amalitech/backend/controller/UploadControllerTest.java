package com.amalitech.backend.controller;

import com.amalitech.backend.exception.EncryptedPdfException;
import com.amalitech.backend.exception.FileTooLargeException;
import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UploadController.class)
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadService uploadService;

    // =========================================================
    // VALID PDF UPLOAD
    // =========================================================

    @Test
    void shouldReturnCreatedAndJobIdForValidPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "dummy-pdf-content".getBytes()
        );

        Job job = new Job();
        job.setId(42L);

        when(uploadService.handleUpload(file))
                .thenReturn(job);

        mockMvc.perform(
                        multipart("/upload")
                                .file(file)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.jobId").value(42));
    }

    // =========================================================
// INVALID PDF RESPONSE
// =========================================================

    @Test
    void shouldReturnBadRequestForInvalidPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                "fake-content".getBytes()
        );

        when(uploadService.handleUpload(file))
                .thenThrow(new InvalidPdfException(
                        "Only PDF files are supported."
                ));

        mockMvc.perform(
                        multipart("/upload")
                                .file(file)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("INVALID_PDF"))
                .andExpect(jsonPath("$.message")
                        .value("Only PDF files are supported."));
    }

    // =========================================================
// ENCRYPTED PDF RESPONSE
// =========================================================

    @Test
    void shouldReturnBadRequestForEncryptedPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "protected.pdf",
                "application/pdf",
                "encrypted-content".getBytes()
        );

        when(uploadService.handleUpload(file))
                .thenThrow(new EncryptedPdfException(
                        "Encrypted or password-protected PDFs are not supported."
                ));

        mockMvc.perform(
                        multipart("/upload")
                                .file(file)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("ENCRYPTED_PDF"))
                .andExpect(jsonPath("$.message")
                        .value("Encrypted or password-protected PDFs are not supported."));
    }

    // =========================================================
// OVERSIZED FILE RESPONSE
// =========================================================

    @Test
    void shouldReturnPayloadTooLargeForOversizedPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                "large-content".getBytes()
        );

        when(uploadService.handleUpload(file))
                .thenThrow(new FileTooLargeException(
                        "The uploaded PDF exceeds the maximum allowed size."
                ));

        mockMvc.perform(
                        multipart("/upload")
                                .file(file)
                )
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.message")
                        .value("The uploaded PDF exceeds the maximum allowed size."));
    }

    // =========================================================
    // MISSING FILE RESPONSE
    // =========================================================

    @Test
    void shouldReturnBadRequestWhenFilePartIsMissing() throws Exception {
        mockMvc.perform(
                        multipart("/upload")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_FILE"))
                .andExpect(jsonPath("$.message")
                        .value("A PDF file is required."));
    }
}