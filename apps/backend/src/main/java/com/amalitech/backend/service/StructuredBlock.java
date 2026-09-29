package com.amalitech.backend.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class StructuredBlock {

    private int pageIndex;

    private BlockType type;

    private String text;

    private float x;
    private float y;
    private float width;
    private float height;

    private List<TextSpan> spans;
}