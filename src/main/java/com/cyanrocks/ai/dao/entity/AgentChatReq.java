package com.cyanrocks.ai.dao.entity;

import lombok.Builder;
import lombok.Data;
@Builder
@Data
public class AgentChatReq {
    /**
     * 用户唯一标识。钉钉传 `dingId`，小程序传 `wx_openid`
     */
    private String user_id;

    /**
     * 仅支持 `"dingtalk"` 或 `"miniprogram"`，其他值返回 422
     */
    private String channel;

    /**
     * 用户的自然语言指令
     */
    private String message;

    private String timeoutMs;
}
