package com.cyanrocks.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiIntelligenceProduct;
import com.cyanrocks.ai.log.Log;
import com.cyanrocks.ai.service.AiIntelligenceService;
import com.cyanrocks.ai.vo.RedditMilvus;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * @Author wjq
 * @Date 2025/11/3 14:50
 */
@RestController
@RequestMapping("/ai/intelligence")
@Api(tags = {"情报相关接口"})
public class IntelligenceController {

    @Autowired
    private AiIntelligenceService intelligenceService;

    @PostMapping("/product/new")
    @ApiOperation(value = "增加新产品")
    @Log(value = "情报相关接口-增加新产品")
    public void newIntelligenceProduct(@RequestBody AiIntelligenceProduct aiIntelligenceProduct){
        intelligenceService.newIntelligenceProduct(aiIntelligenceProduct);
    }

    @PostMapping("/product/delete")
    @ApiOperation(value = "删除产品")
    @Log(value = "情报相关接口-删除产品")
    public void deleteIntelligenceProduct(@RequestBody AiIntelligenceProduct aiIntelligenceProduct){
        intelligenceService.deleteIntelligenceProduct(aiIntelligenceProduct);
    }

    @GetMapping("/product/page")
    @ApiOperation(value = "分页获取产品信息")
    @Log(value = "情报相关接口-分页获取产品信息")
    public IPage<AiIntelligenceProduct> getIntelligenceProductPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                                                   @RequestParam(value="sortStr",required=false) String sortStr,
                                                                   @RequestParam(value="searchStr",required=false) String searchStr) {
        return intelligenceService.getIntelligenceProductPage(pageNo, pageSize, sortStr,searchStr);
    }

    @GetMapping("/product/word-cload")
    @ApiOperation(value = "获取产品词云")
    @Log(value = "情报相关接口-获取产品词云")
    public String getIntelligenceWordCloud(@RequestParam(value="refresh",required=false) Boolean refresh) {
        return intelligenceService.getIntelligenceWordCloud(refresh);
    }

    @PostMapping("/parse")
    @ApiOperation(value = "文件上传")
    public String parseIntelligenceProduct(@RequestParam("file") MultipartFile file) {
        return intelligenceService.parseIntelligenceProduct(file);
    }

    @GetMapping("/question")
    @ApiOperation(value = "问题")
    public List<RedditMilvus> question(@RequestParam String question) throws Exception {
        return intelligenceService.question(question, "reddit");
    }

    @GetMapping("/reviews")
    @ApiOperation(value = "获取评论列表")
    public List<RedditMilvus.Review> getReview(@RequestParam String id) {
        return intelligenceService.getReview(id);
    }

    @GetMapping("/test")
    @ApiOperation(value = "test")
    public void test() throws Exception {
        intelligenceService.test();
    }

}
