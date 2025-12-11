package com.cyanrocks.ai.xiyan.models;


import com.cyanrocks.ai.listen.model.GbiChartData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisualizationBO {

    private String text;

    private GbiChartData data;

}
