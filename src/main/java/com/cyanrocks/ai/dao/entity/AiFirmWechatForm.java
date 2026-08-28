package com.cyanrocks.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 企业微信表单实体类
 * @author yangshihao
 */
@Entity
@Table(name = "ai_firm_wechat_form")
@Data
@TableName("ai_firm_wechat_form")
public class AiFirmWechatForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "JDBC")
    @Column(name = "id")
    private Long id;

    @Column(name = "otid", nullable = false)
    private String otid;

    @TableField(exist = false)
    private String code;

    @TableField(exist = false)
    private String userId;

    @Column(name = "external_userid", nullable = false)
    private String externalUserid;

    @Column(name = "is_del", nullable = false)
    private Integer isDel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


}
