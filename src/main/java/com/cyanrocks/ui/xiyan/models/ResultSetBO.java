package com.cyanrocks.ui.xiyan.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author xxx
 * @desc sql执行后的结果集
 * @date 2024/2/29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultSetBO {

    private List<String> column;
    private List<Map<String, String>> data;
    private String errorMsg;

}
