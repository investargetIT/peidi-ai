package com.cyanrocks.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * @Author wjq
 * @Date 2026/1/12 14:15
 */
@Entity
@Table(name = "ai_draw_materials")
@Data
public class AiDrawMaterials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "object_name")
    @Comment("图片oss路径")
    private String objectName;

    @Column(name = "type")
    @Comment("类型")
    private String type;
}
