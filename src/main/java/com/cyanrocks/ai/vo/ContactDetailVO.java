package com.cyanrocks.ai.vo;

import lombok.Data;

/**
 * 客户详情VO（包含标签和添加时间）
 * @author yangshihao
 */
@Data
public class ContactDetailVO {
    /**
     * 标签字符串，用 "|" 拼接
     */
    private String tags;

    /**
     * 添加好友时间（Unix秒）
     * 如果企微接口没有返回 createtime 字段，则为 null
     */
    private Long createTime;
}
