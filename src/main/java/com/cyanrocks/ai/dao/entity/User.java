package com.cyanrocks.ai.dao.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * @Author wjq
 * @Date 2024/7/24 15:12
 */
@Entity
@Table(name = "user")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(length = 100, name = "username")
    @Comment("用户名")
    private String username;

    @Column(length = 100, name = "password")
    @Comment("密码")
    private String password;

    @Column(length = 50, name = "mobile" )
    @Comment("手机号")
    private String mobile;

    @Column(length = 50, name = "email")
    @Comment("邮箱")
    private String email;

    @JsonFormat
    @Column(name = "create_time")
    @Comment("创建时间")
    private LocalDateTime createTime;

    @Column(length = 255, name = "data_source")
    @Comment("来源")
    private Long dataSource;

    @Column(length = 255, name = "ding_id")
    @Comment("钉钉userid")
    private String dingId;

    @Column(length = 255, name = "dept_id")
    @Comment("钉钉部门id")
    private String deptId;

    @Column(name = "oa_delete")
    @Comment("oa上删除")
    private Boolean oaDelete;
}
