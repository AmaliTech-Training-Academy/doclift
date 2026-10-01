package com.amalitech.backend.service;

import java.util.List;

public record TableCell(
        int row,
        int column,
        int rowSpan,
        int columnSpan,
        String text,
        List<TextSpan> spans,
        String footnoteKey
) {
}
