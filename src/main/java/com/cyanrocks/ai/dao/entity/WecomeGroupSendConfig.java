package com.cyanrocks.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 企微群发策略配置实体类
 * 用于动态配置群发任务策略
 * @author yangshihao
 */
@Entity
@Table(name = "wecom_group_send_config")
@Data
@TableName("wecom_group_send_config")
public class WecomeGroupSendConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "JDBC")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 是否启用：0-禁用，1-启用
     */
    @Column(name = "enabled", nullable = false)
    @TableField("enabled")
    private Integer enabled = 1;

    /**
     * 发送者企微成员ID
     */
    @Column(name = "sender", nullable = false, length = 100)
    @TableField("sender")
    private String sender;

    /**
     * 【核心字段】客户入群N天后触发群发
     */
    @Column(name = "days_ago", nullable = false)
    @TableField("days_ago")
    private Integer daysAgo = 7;

    /**
     * 群发图片URL（用户上传后存储）
     */
    @Column(name = "image_url", length = 500)
    @TableField("image_url")
    private String imageUrl;

    /**
     * 群发文案内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    @TableField("content")
    private String content;

    /**
     * 逻辑删除：0-正常，1-已删除
     */
    @Column(name = "is_del", nullable = false)
    @TableField("is_del")
    @TableLogic
    private Integer isDel = 0;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
