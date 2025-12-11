package com.cyanrocks.ai.dao.entity;
import lombok.Data;
import org.apache.poi.hpsf.Decimal;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * @Author wjq
 * @Date 2025/11/24 14:16
 */
@Entity
@Table(name = "ai_intelligence_product")
@Data
public class AiIntelligenceProduct {

    @Id
    @Column(name = "id")
    @Comment("主键ID")
    private Integer id;

    @Column(name = "title")
    @Comment("标题")
    private String title;

    @Column(name = "star")
    @Comment("星级")
    private String star;

    @Column(name = "review_cnt")
    @Comment("评论数")
    private Integer reviewCnt;

    @Column(name = "amount")
    @Comment("价格")
    private BigDecimal amount;

    @Column(name = "long_amount")
    @Comment("长期订购价")
    private BigDecimal longAmount;

    @Column(name = "channel")
    @Comment("渠道")
    private String channel;
}
