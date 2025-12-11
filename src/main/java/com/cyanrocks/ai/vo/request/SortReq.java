package com.cyanrocks.ai.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @Author wjq
 * @Date 2024/8/13 13:29
 */
@Data
@ApiModel(description = "排序请求参数")
public class SortReq {

    @ApiModelProperty(value = "排序字段")
    private String sortName;

    @ApiModelProperty(value = "排序类型：asc/desc")
    private String sortType;
}
