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
@Table(name = "ai_draw")
@Data
public class AiDraw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "uuid")
    @Comment("任务唯一标识")
    private String uuid;

    @Column(name = "max_retries")
    @Comment("接口重试次数")
    private Integer maxRetries;

    @Column(name = "size")
    @Comment("生成图片数量")
    private Integer size;

    @Column(name = "fields")
    @Comment("页面展示字段")
    private String fields;

    @Column(name = "imgs")
    @Comment("图片数组链接")
    private String imgs;

    @Column(name = "remark")
    @Comment("备注")
    private String remark;

    @Column(name = "create_at")
    @Comment("创建日期")
    private LocalDateTime createAt;

    @Column(name = "update_at")
    @Comment("修改日期")
    private LocalDateTime updateAt;

    @Column(name = "status")
    @Comment("任务状态")
    private Integer status;

    @Transient
    @TableField(exist = false)
    private String urlParam;

    private String resultImage;
}
