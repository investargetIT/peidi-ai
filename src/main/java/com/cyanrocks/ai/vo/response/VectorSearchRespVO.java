package com.cyanrocks.ai.vo.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 纯向量检索响应VO
 * @Author wjq
 * @Date 2025/6/4
 */
@Data
@ApiModel(description = "向量检索响应")
public class VectorSearchRespVO {

    @ApiModelProperty(value = "重写后的问题")
    private String rewriteQuestion;

    @ApiModelProperty(value = "检索结果列表")
    private List<SearchResult> results;

    @ApiModelProperty(value = "检索记录ID")
    private String id;

    @Data
    @ApiModel(description = "单个检索结果")
    public static class SearchResult {
        
        @ApiModelProperty(value = "文档标题")
        private String title;

        @ApiModelProperty(value = "文档来源")
        private String source;

        @ApiModelProperty(value = "文档类型")
        private String reportType;

        @ApiModelProperty(value = "文档内容")
        private String text;

        @ApiModelProperty(value = "相似度分数")
        private Float score;
    }
}
