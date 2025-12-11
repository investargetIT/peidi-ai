package com.cyanrocks.ai.dao.entity;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

/**
 * @Author wjq
 * @Date 2024/9/19 15:57
 */
@Entity
@Table(name = "bi_reddit")
@Data
public class BiReddit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "shop")
    private String shop;

    @Column(name = "sentiment")
    private String sentiment;

    @Column(name = "reviews")
    private String reviews;

    @Column(name = "milvus_id")
    private String milvusId;
}
