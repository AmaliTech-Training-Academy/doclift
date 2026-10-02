package com.amalitech.backend.service;


import lombok.*;

@Setter @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ExtractedImage {
    private int pageIndex;
    private String imageName;

    /** Bottom-left corner, in PDF points, of the image's unrotated placement rectangle (PDF user space, Y-up). */
    private float x;
    private float y;
    private float width;
    private float height;

    private int pixelsWidth;
    private int pixelsHeight;
    private String mimeType;

    /** Counter-clockwise rotation of the image content, in degrees, as decomposed from the PDF content stream CTM. */
    private float rotationDegrees;

    /** True when the CTM includes a reflection (mirrored image) rather than a pure rotation. */
    private boolean flipHorizontal;

    /** Re-encoded, display-ready image bytes (always PNG) for embedding into the output document. */
    private byte[] data;

}
