package com.amalitech.backend.service;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TextSpan {

    private int pageIndex;
    private String text;

    private float x;
    private float y;
    private float width;
    private float height;

    private String fontName;
    private float fontSize;

    private boolean bold;
    private boolean italic;
    private boolean underline;

    private boolean wordSeparatorBefore;

    private String colorHex;

    // Keep old constructor so existing tests/call sites don't all break.
    public TextSpan(
            int pageIndex,
            String text,
            float x,
            float y,
            float width,
            float height,
            String fontName,
            float fontSize,
            boolean bold,
            boolean italic,
            boolean underline,
            boolean wordSeparatorBefore
    ) {
        this(
                pageIndex,
                text,
                x,
                y,
                width,
                height,
                fontName,
                fontSize,
                bold,
                italic,
                underline,
                wordSeparatorBefore,
                null
        );
    }
}