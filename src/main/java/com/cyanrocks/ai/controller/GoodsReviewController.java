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


    @GetMapping("/question")
    @ApiOperation(value = "问题")
    public GoodsReviewMilvus question(@RequestParam(value = "question") String question,
                                            @RequestParam(value = "product") String product,
                                            @RequestParam(value = "compareProduct",required = false) String compareProduct) {
        return goodsReviewService.question(question, product, compareProduct, "goods_review");
    }

    @GetMapping("/test")
    @ApiOperation(value = "test")
    public void test() throws Exception {
        goodsReviewService.test();
    }
}
