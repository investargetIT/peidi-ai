package com.cyanrocks.ai.dao.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;

/**
 * @Author wjq
 * @Date 2024/9/19 15:57
 */
@Entity
@Table(name = "bi_goods_review")
@Data
public class BiGoodsReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY,  // strategy 设置使用数据库主键自增策略；
            generator = "JDBC")
    private Long id;

    @Column(name = "channel")
    private String channel;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "review_date")
    @JsonFormat
    private LocalDate reviewDate;

    @Column(name = "goods_name")
    private String goodsName;

    @Column(name = "goods_id")
    private String goodsId;

    @Column(name = "goods_type")
    private String goodsType;

    @Column(name = "goods_review")
    private String goodsReview;

    @Column(name = "goods_image")
    private String goodsImage;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "sentiment")
    private String sentiment;

    @Column(name = "milvus_id")
    private String milvusId;
}
