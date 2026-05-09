package com.cyanrocks.ai.dao.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgenResponseServerError {
    /**
     * 错误详情
     */
    private String detail;
    /**
     * 请求ID
     */
    private String requestId;
}
