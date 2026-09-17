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

}
