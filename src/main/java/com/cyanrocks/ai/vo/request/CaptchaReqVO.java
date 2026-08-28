package com.cyanrocks.ai.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 验证码请求参数
 */
@Data
@ApiModel(description = "验证码请求参数")
public class CaptchaReqVO {

    @ApiModelProperty(value = "验证码标识（如手机号、邮箱等）", required = true)
    private String key;

    @ApiModelProperty(value = "验证码值", required = true)
    private String value;
}
