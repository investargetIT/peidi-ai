package com.cyanrocks.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author wjq
 * @Date 2026/1/12 17:05
 */
@Entity
@Table(name = "bi_goods_evaluation")
@Data
public class BiGoodsEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "date")
    @Comment("抓取日期")
    private LocalDate date;

    @Column(name = "channel")
    @Comment("渠道")
    private String channel;

    @Column(name = "evaluation_type")
    @Comment("评价分类")
    private String evaluationType;

    @Column(name = "evaluation_content")
    @Comment("评价内容")
    private String evaluationContent;

    @Column(name = "evaluation_time")
    @Comment("评价时间")
    private LocalDateTime evaluationTime;

    @Column(name = "order_no")
    @Comment("订单号")
    private String orderNo;

    @Column(name = "product_id")
    @Comment("商品id")
    private String productId;

    @Column(name = "product_name")
    @Comment("商品名称")
    private String productName;

    @Column(name = "user_nickname")
    @Comment("用户昵称")
    private String userNickname;

    @Column(name = "image_urls")
    @Comment("图片链接")
    private String imageUrls;

    @Column(name = "more_evaluation_content")
    @Comment("追加评论内容")
    private String moreEvaluationContent;

    @Column(name = "shop_name")
    @Comment("店铺名称")
    private String shopName;

    @Column(name = "product_url")
    @Comment("商品链接")
    private String productUrl;

    @Column(name = "sentiment")
    @Comment("评论正向负面")
    private String sentiment;

    @Column(name = "ai_evaluation_content")
    @Comment("ai回复内容")
    private String aiEvaluationContent;

    @Transient
    @TableField(exist = false)
    private List<BiGoodsEvaluation> goodsEvaluationList;
}