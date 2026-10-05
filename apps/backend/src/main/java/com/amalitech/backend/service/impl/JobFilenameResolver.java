package com.amalitech.backend.service.impl;

import org.springframework.stereotype.Component;

@Component
public class JobFilenameResolver {

    public String buildOutputFilename(
            String sourceFilename
    ) {
        if (sourceFilename == null
                || sourceFilename.isBlank()) {
            return "converted.docx";
        }

        int lastDot =
                sourceFilename.lastIndexOf('.');

        String baseName =
                lastDot > 0
                        ? sourceFilename.substring(
                        0,
                        lastDot
                )
                        : sourceFilename;

        return baseName + ".docx";
    }
}