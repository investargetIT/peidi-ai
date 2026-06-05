package com.cyanrocks.ai.service;

/**
 * 意图类型枚举
 * 支持扩展新的意图类型
 */
public enum IntentType {

    /** 领取xxxx */
    RECEIVE_GIFT("领取奖品"),

    /** 怎么领取 */
    ASK_RECEIVE_METHOD("询问领取方式"),

    /** 罐头相关 */
    CAN_RELATED("罐头相关"),

    /** 未知意图 */
    UNKNOWN("未知"),

    /** 其他意图（扩展预留） */
    OTHER("其他");

    private final String description;

    IntentType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}