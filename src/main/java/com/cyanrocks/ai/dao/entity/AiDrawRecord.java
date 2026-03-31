package com.cyanrocks.ai.dao.entity;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * @Author wjq
 * @Date 2026/1/12 14:15
 */
@Entity
@Table(name = "ai_draw_record")
@Data
public class AiDrawRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "path")
    @Comment("oss路径")
    private String path;

    @Column(name = "type")
    @Comment("类型")
    private String type;

    @Column(name = "create_at")
    @Comment("创建日期")
    private LocalDateTime createAt;


}
