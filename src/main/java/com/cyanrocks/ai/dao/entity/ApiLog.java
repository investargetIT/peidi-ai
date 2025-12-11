package com.cyanrocks.ai.dao.entity;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * @Author wjq
 * @Date 2025/9/22 15:22
 */
@Entity
@Table(name = "api_log")
@Data
public class ApiLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    @Comment("user表id")
    private String userId;

    @Column(name = "user_name")
    @Comment("用户名")
    private String userName;

    @Column(name = "request_uri")
    @Comment("请求url")
    private String requestUri;

    @Column(name = "operation")
    @Comment("操作")
    private String operation;

    @Column(name = "time")
    @Comment("请求时间")
    private LocalDateTime time;

    @Column(name = "params")
    @Comment("请求参数")
    private String params;

    @Column(name = "result")
    @Comment("返回值")
    private String result;

}
