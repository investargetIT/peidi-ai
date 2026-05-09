package com.cyanrocks.ai.dao.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentChatResp {

    /**
     * 智脑返回的结果文本
     */
    @SerializedName("agent_result")
    private String agentResult;

    /**
     * 生成的图表 URL 列表
     */
    @SerializedName("chart_urls")
    private List<String> chartUrls;

    /**
     * 是否为修正模式
     */
    @SerializedName("correction_mode")
    private Boolean correctionMode;
    /**
     * 模型状态码
     */
    private Integer stateCode=200;
}