package com.cyanrocks.ai.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.service.AiChewyDetailService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author wjq
 * @Date 2026/1/19 9:29
 */
@RestController
@RequestMapping("/ai/chewy")
@Api(tags = {"chewy接口"})
public class ChewyController {

    @Autowired
    private AiChewyDetailService chewyDetailService;

    /**
     * Triggers the chewy service test operation.
     */
    @PostMapping("/test")
    @ApiOperation(value = "test")
    public void test() {
        chewyDetailService.test();
    }

    /**
     * Retrieve a list of chewy items filtered by the given criteria.
     *
     * @param keyword optional search keyword to filter items
     * @param function optional function or category to filter items
     * @param score true to include scoring information for each item, false to omit it
     * @param redFlag true to restrict results to items marked with the red flag, false to include all
     * @return a JSONObject containing the chewy list and associated metadata (for example: total count and items)
     */
    @GetMapping("/list")
    @ApiOperation(value = "查询chewy列表")
    public JSONObject getChewyList(@RequestParam(value="keyword",required = false) String keyword,
                                   @RequestParam(value="function",required = false) String function,
                                   @RequestParam(value="score") Boolean score,
                                   @RequestParam(value="redFlag") Boolean redFlag) {
        return chewyDetailService.getChewyList(keyword, function, score, redFlag);
    }
}
