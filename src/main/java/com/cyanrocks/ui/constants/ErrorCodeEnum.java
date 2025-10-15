package com.cyanrocks.ui.constants;

import com.cyanrocks.common.em.AppErrorCode;

public enum ErrorCodeEnum {

    /**
     * 重复参数
     */
    REPEAT_PARAM("108" + "100000"),

    /**
     * 用户session过期，登陆状态失效
     */
    SESSION_EXPIRED(AppErrorCode.USER.getCode() + "100011"),

    /**
     * 无效的sessionId
     */
    SESSION_INVALID(AppErrorCode.USER.getCode() + "100012")
    ;

    private final String code;

    ErrorCodeEnum(String code) {
        this.code = code;
    }

    public Integer getCode() {
        return Integer.valueOf(code);
    }
}
