package com.cyanrocks.ui.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/3/25 9:50
 */
@Data
public class BiJiuqianRecordMilvus {
    public final long id;
    public final String month;
    public final String title;
    public final boolean contentType;
    public final String text;
    public List<Float> vector;
}
