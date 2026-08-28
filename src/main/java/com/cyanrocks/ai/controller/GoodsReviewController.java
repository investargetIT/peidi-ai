package com.cyanrocks.ai.controller;

import com.cyanrocks.ai.dao.entity.BiGoodsReview;
import com.cyanrocks.ai.service.GoodsReviewService;
import com.cyanrocks.ai.vo.GoodsReviewMilvus;
import com.cyanrocks.ai.vo.RedditMilvus;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/12/26 10:46
 */
@RestController
@RequestMapping("/ai/goods-review")
@Api(tags = {"商品评论相关接口"})
public class GoodsReviewController {

    @Autowired
    private GoodsReviewService goodsReviewService;


    /**
     * Answer a product-related question by querying the goods_review collection.
     *
     * @param question the natural-language question to ask about the product
     * @param product identifier or name of the primary product to query
     * @param productReviewTime optional time filter for reviews of the primary product (e.g., timestamp or date range)
     * @param compareProduct optional identifier or name of a product to compare against the primary product
     * @param compareProductReviewTime optional time filter for reviews of the comparison product (e.g., timestamp or date range)
     * @return a GoodsReviewMilvus object containing the answer and related retrieval/metadata from the goods_review collection
     */
    @GetMapping("/question")
    @ApiOperation(value = "问题")
    public GoodsReviewMilvus question(@RequestParam(value = "question") String question,
                                      @RequestParam(value = "product") String product,
                                      @RequestParam(value = "productReviewTime",required = false) String productReviewTime,
                                      @RequestParam(value = "compareProduct",required = false) String compareProduct,
                                      @RequestParam(value = "compareProductReviewTime",required = false) String compareProductReviewTime) {
        return goodsReviewService.question(question, product, productReviewTime, compareProduct, compareProductReviewTime,"goods_review");
    }

    /**
     * Estimates how long it will take to prepare an answer to a question about product reviews.
     *
     * @param question the natural-language question to evaluate
     * @param product identifier or name of the primary product to query reviews for
     * @param productReviewTime optional time filter for the primary product's reviews (e.g., date or time range)
     * @param compareProduct optional identifier or name of a secondary product to compare against
     * @param compareProductReviewTime optional time filter for the comparison product's reviews (e.g., date or time range)
     * @return the estimated processing time in milliseconds
     */
    @GetMapping("/question-pre")
    @ApiOperation(value = "问题准备预估处理时间")
    public Integer questionPre(@RequestParam(value = "question") String question,
                                      @RequestParam(value = "product") String product,
                                      @RequestParam(value = "productReviewTime",required = false) String productReviewTime,
                                      @RequestParam(value = "compareProduct",required = false) String compareProduct,
                                      @RequestParam(value = "compareProductReviewTime",required = false) String compareProductReviewTime) {
        return goodsReviewService.questionPre(question, product, productReviewTime, compareProduct, compareProductReviewTime,"goods_review");
    }
}
