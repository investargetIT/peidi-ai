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
@Table(name = "ai_gbi_table")
@Data
public class AiGbiTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "milvus_id")
    @Comment("milvus主键id")
    private String milvusId;

    @Column(name = "table_name")
    @Comment("表名")
    private String tableName;

    @Column(name = "field")
    @Comment("相关字段")
    private String field;

    @Column(name = "search_sql")
    @Comment("参考sql")
    private String searchSql;

    @Column(name = "metedate")
    @Comment("扩展字段")
    private String metedate;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;
}
