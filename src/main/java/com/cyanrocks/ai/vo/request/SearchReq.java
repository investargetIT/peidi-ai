package com.cyanrocks.ai.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @Author wjq
 *
 *
 *
 * @Date 2024/8/13 13:29
 */
@Data
@ApiModel(description = "搜索请求参数")
public class SearchReq {

    @ApiModelProperty(value = "搜索字段")
    private String searchName;

    @ApiModelProperty(value = "搜索类型：like/between/betweenStr")
    private String searchType;

    @ApiModelProperty(value = "搜索值")
        private String searchValue;
}
