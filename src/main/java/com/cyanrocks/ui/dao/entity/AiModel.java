package com.cyanrocks.ui.dao.entity;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

/**
 * @Author wjq
 * @Date 2024/9/19 15:57
 */
@Entity
@Table(name = "ai_model")
@Data
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "type")
    @Comment("类型")
    private String type;

    @Column(name = "model_name")
    @Comment("模型名称")
    private String modelName;

    @Column(name = "params")
    @Comment("json参数")
    private String params;

    @Column(name = "prompt")
    @Comment("提示词")
    private String prompt;

    @Column(name = "active")
    @Comment("是否启用，一个类型只可以有一个为1")
    private Boolean active;
}
