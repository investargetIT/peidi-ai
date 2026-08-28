package com.cyanrocks.ai.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 企业微信用户信息请求VO
 * @author yangshihao
 */
@Data
@ApiModel(description = "企业微信用户信息请求参数")
public class QywxUserInfoReqVO {

    @ApiModelProperty(value = "企业微信返回的code", required = true)
    private String code;
}
