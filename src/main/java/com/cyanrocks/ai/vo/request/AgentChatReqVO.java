package com.cyanrocks.ai.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "Agent聊天请求参数")
public class AgentChatReqVO {

    @ApiModelProperty(value = "问题内容", required = true)
    private String message;

    @ApiModelProperty(value = "渠道", required = true)
    private String channel;

    @ApiModelProperty(value = "用户标识", required = true)
    private String openid;
}