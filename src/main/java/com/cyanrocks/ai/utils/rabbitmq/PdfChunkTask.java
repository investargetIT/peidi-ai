package com.cyanrocks.ai.utils.rabbitmq;

import lombok.Data;

import java.io.Serializable;
import java.nio.file.Path;

/**
 * @Author wjq
 * @Date 2026/1/7 13:28
 */
@Data
public class PdfChunkTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private String requestStr;
    private byte[] splitPdf;
    private int batchNo;
    private String source;
    private String originalFilename;
}
