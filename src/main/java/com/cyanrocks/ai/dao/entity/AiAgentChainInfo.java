package com.cyanrocks.ai.dao.entity;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_agent_chain_info")
@Data
public class AiAgentChainInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "JDBC")
    @Comment("主键ID")
    private Long id;

    @Column(name = "x_request_id", nullable = false)
    @Comment("请求唯一ID")
    private String xRequestId;

    @Column(name = "req_body")
    @Comment("请求体")
    private String reqBody;

    @Column(name = "state")
    @Comment("状态（如：200 / 500 / 422）")
    private String state;

    @Column(name = "resp_body")
    @Comment("响应体")
    private String respBody;

    @Column(name = "user_id")
    @Comment("用户id")
    private String userId;

    @Column(name = "error_msg")
    @Comment("错误信息")
    private String errorMsg;

    @Column(name = "create_time")
    @Comment("创建时间")
    private LocalDateTime createTime;


}