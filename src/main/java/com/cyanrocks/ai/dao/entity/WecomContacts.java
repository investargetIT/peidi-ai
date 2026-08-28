package com.cyanrocks.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 企微触达三方映射实体类
 * @author yangshihao
 */
@Entity
@Table(name = "wecom_contacts")
@Data
@TableName("wecom_contacts")
public class WecomContacts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "JDBC")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 企微外部联系人ID
     */
    @Column(name = "external_userid", nullable = false, length = 128)
    @TableField("external_userid")
    private String externalUserid;



    /**
     * 佩蒂核心32位hash业务ID(潜客可空)
     */
    @Column(name = "customer_id", length = 64)
    @TableField("customer_id")
    private String customerId;

    /**
     * 由哪个企微成员加的好友
     */
    @Column(name = "follow_userid", length = 128)
    @TableField("follow_userid")
    private String followUserid;

    /**
     * unionid
     */
    @Column(name = "unionid", length = 128)
    @TableField("unionid")
    private String unionid;

    /**
     * 加好友时间(Unix秒)
     */
    @Column(name = "add_time")
    @TableField("add_time")
    private Long addTime;

    /**
     * active或lost(失联)
     */
    @Column(name = "state", nullable = false, length = 16)
    @TableField("state")
    private String state = "active";

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 用户分组标签,使用｜分割
     */
    @Column(name = "tags", length = 500)
    @TableField("tags")
    private String tags;

    /**
     * 短码id 20位
     */
    @Column(name = "company_id", length = 20)
    @TableField("company_id")
    private String companyId;
}
