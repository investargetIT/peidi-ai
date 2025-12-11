package com.cyanrocks.ai.listen.model;

import lombok.Getter;

/**
 * @author xxx
 * @desc 流式输出的关键事件
 * @date 2024/2/29
 */
public enum GbiChatBIMsgType {

    REWRITE("rewrite"),

    SELECT("selector"),

    SELECT_MATCH("selector_match"),

    SMR_RESULT("smr_result"),

    SQL("sql"),

    REFINE("refine"),

    EVIDENCE("evidence"),

    SQL_PART("sql_part"),

    SQL_DATA("sql_data"),

    RESULT("result"),

    CHAT("chat"),

    REJECT("reject"),

    DEBUG_REWRITE("debug_rewrite"),

    DEBUG_SELECTOR("debug_selector"),

    DEBUG_SQL("debug_sql"),

    DEBUG_RESULT("debug_result"),

    DEBUG_EVIDENCE_RETRIEVAL("debug_evidence_retrieval"),

    ERROR("error");

    @Getter
    private String code;

    GbiChatBIMsgType(String code) {
        this.code = code;
    }
}
