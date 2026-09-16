package com.amalitech.backend.controller;

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
}