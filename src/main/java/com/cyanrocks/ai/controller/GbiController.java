package com.cyanrocks.ai.controller;

import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.vo.GbiMilvus;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @Author wjq
 * @Date 2025/11/18 12:00
 */
@RestController
@RequestMapping("/ai/gbi")
@Api(tags = {"Gbi相关接口"})
public class GbiController {

    @Autowired
    private MilvusUtils milvusUtils;

    private static final String milvusCollection = "gbi_qa";

    @GetMapping("/question")
    @ApiOperation(value = "查询问题")
    public Map<String, String> gbiQuestion(@RequestParam String question) throws Exception {
        return milvusUtils.gbiSearch(question, "gbi_qa", "17210074020113233");
    }

    @PostMapping("/question/new")
    @ApiOperation(value = "新增问题")
    public void newQuestion(@RequestBody GbiMilvus gbiMilvus) throws Exception {
        milvusUtils.processGbiData(gbiMilvus, milvusCollection);
    }
}
