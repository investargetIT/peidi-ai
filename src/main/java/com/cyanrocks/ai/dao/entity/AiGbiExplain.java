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
@Table(name = "ai_gbi_explain")
@Data
public class AiGbiExplain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "milvus_id")
    @Comment("milvus主键id")
    private String milvusId;

    @Column(name = "gbi_explain")
    @Comment("业务逻辑解释")
    private String gbiExplain;

    @Column(name = "explain_type")
    @Comment("业务逻辑解释类型")
    private Boolean explainType;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

}
