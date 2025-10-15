package com.cyanrocks.ui.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/3/25 9:50
 */
@Data
public class PdfRecordMilvus {
    public String id;
    public String title;
    public String text;
    public List<Float> vector;
}
