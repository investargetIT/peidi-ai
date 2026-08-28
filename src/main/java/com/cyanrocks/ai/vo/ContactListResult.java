package com.cyanrocks.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * 企微客户列表分页结果
 * 包含客户列表和下一次请求的游标
 * @author yangshihao
 */
@Data
public class ContactListResult {
    /**
     * 客户列表
     */
    private List<WecomContactVO> contacts;

    /**
     * 下一次请求的游标
     * 如果为空或null，表示没有更多数据
     */
    private String nextCursor;

    /**
     * 是否还有更多数据
     */
    private boolean hasMore;
}
