package com.cyanrocks.ai.vo;

import com.cyanrocks.ai.dao.entity.BiGoodsReview;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GoodsReviewMilvus {
    private String id;
    private String channel;
    private String customerName;
    private LocalDate reviewDate;
    private String goodsName;
    private String goodsId;
    private String goodsType;
    private String goodsReview;
    private String goodsImage;
    private String shopName;
    private String sentiment;

    private List<BiGoodsReview> goodsReviews;
    private String goodsWordCloud;

    private List<BiGoodsReview> compareGoodsReviews;
    private String compareGoodsWordCloud;

    private String compareSummary;
}
