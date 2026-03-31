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

    @PostMapping("/test")
    @ApiOperation(value = "test")
    public void test() {
        chewyDetailService.test();
    }

    @GetMapping("/list")
    @ApiOperation(value = "查询chewy列表")
    public JSONObject getChewyList(@RequestParam(value="keyword",required = false) String keyword,
                                   @RequestParam(value="function",required = false) String function,
                                   @RequestParam(value="score") Boolean score,
                                   @RequestParam(value="redFlag") Boolean redFlag) {
        return chewyDetailService.getChewyList(keyword, function, score, redFlag);
    }
}
