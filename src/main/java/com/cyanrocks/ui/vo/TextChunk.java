package com.cyanrocks.ui.vo;

import lombok.Data;

/**
 * @Author wjq
 * @Date 2025/5/22 14:30
 */
@Data
public class TextChunk {
    private final String content;
    private final int pageNumber;
    private final boolean isOcr;
}
