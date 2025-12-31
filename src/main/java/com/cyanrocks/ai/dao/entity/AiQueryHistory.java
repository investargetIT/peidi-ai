package com.cyanrocks.ai.dao.entity;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * @Author wjq
 * @Date 2024/9/19 15:57
 */
@Entity
@Table(name = "ai_query_history")
@Data
public class AiQueryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "user_id")
    @Comment("用户id")
    private String userId;

    @Column(name = "id_type")
    @Comment("id类型")
    private String idType;

    @Column(name = "query")
    @Comment("问题")
    private String query;

    @Column(name = "rewrite_query")
    @Comment("重写问题")
    private String rewriteQuery;

    @Column(name = "result")
    @Comment("回复")
    private String result;

    @Column(name = "create_at")
    @Comment("创建时间")
    private LocalDateTime createAt;

    @Column(name = "source")
    @Comment("来源：问问/问数")
    private String source;
}
