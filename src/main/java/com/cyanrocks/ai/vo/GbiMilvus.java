package com.cyanrocks.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/3/25 9:50
 */
@Data
public class GbiMilvus {
    public long id;
    public String table;
    public String field;
    public String sql;
    public String metedate;
}
