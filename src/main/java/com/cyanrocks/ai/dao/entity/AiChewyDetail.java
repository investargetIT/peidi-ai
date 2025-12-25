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
@Table(name = "ai_chewy_detail")
@Data
public class AiChewyDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "url")
    private String url;

    @Column(name = "title")
    private String title;

    @Column(name = "detail")
    private String detail;

    @Column(name = "ingredient_information")
    private String ingredientInformation;



}
