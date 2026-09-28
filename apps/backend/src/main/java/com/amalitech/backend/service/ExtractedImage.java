package com.amalitech.backend.service;


import lombok.*;

@Setter @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ExtractedImage {
    private int pageIndex;
    private String imageName;
    private float x;
    private float y;
    private float width;
    private float height;
    private int pixelsWidth;
    private int pixelsHeight;
    private String mimeType;

}
