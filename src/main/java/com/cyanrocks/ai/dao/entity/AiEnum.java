package com.cyanrocks.ai.dao.entity;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

/**
 * @Author wjq
 * @Date 2024/9/19 15:57
 */
@Entity
@Table(name = "ai_enum")
@Data
public class AiEnum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(length = 100, name = "value")
    @Comment("枚举值")
    private String value;

    @Column(length = 100, name = "type")
    @Comment("枚举类型")
    private String type;
}
