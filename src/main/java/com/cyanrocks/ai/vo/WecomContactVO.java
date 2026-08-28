package com.cyanrocks.ai.vo;

import lombok.Data;

/**
 * 企微客户信息VO
 * 包含添加时间和外部联系人ID
 * @author yangshihao
 */
@Data
public class WecomContactVO {
    /**
     * 外部联系人ID
     * 仅当 is_customer=true 时存在
     */
    private String externalUserid;

    /**
     * 添加时间（Unix秒）
     */
    private Long addTime;

    /**
     * 是否为客户
     */
    private Boolean isCustomer;
    /**
     * 标签
     */
    private String tags;

    private String followUserid;
}
