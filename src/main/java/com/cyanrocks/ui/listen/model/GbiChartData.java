package com.cyanrocks.ui.listen.model;

import lombok.Data;

import java.util.List;

/**
 * @author xxx
 * @date 2024/12/02
 */
@Data
public class GbiChartData {

    private String plotType;

    private List<String> xAxis;

    private List<String> yAxis;
}
