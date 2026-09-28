package com.amalitech.backend.service;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TableRegion {
    private int pageIndex;
    private float x;
    private float y;
    private float width;
    private float height;
    private int rowCount;
    private int columnCount;

}
