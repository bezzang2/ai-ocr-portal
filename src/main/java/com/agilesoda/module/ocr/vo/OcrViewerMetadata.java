package com.agilesoda.module.ocr.vo;

import java.util.List;

/** Display assets use the same processed pixel grid as token_bbox. */
public record OcrViewerMetadata(String documentId, String imageView, List<Page> pages,
                                Original original, String message) {
    public record Page(int pageNumber, int width, int height, String imageUrl) {}
    public record Original(String url, String mimeType, String fileName) {}
}
