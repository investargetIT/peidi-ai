package com.cyanrocks.ai.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiMilvusPdfMarkdown;
import com.cyanrocks.ai.log.Log;
import com.cyanrocks.ai.service.AiMilvusPdfMarkdownService;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.utils.FileToMarkdownConverter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * @Author wjq
 * @Date 2025/3/19 11:58
 */
@RestController
@RequestMapping("/ai/milvus")
@Api(tags = {"Milvus相关接口"})
public class MilvusController {

    @Autowired
    private MilvusUtils milvusUtils;

    @Autowired
    private FileToMarkdownConverter fileToMarkdownConverter;

    @Autowired
    private AiMilvusPdfMarkdownService aiMilvusPdfMarkdownService;

    private static final String milvusCollection = "pdf_markdown";

    @GetMapping("/pdf/question")
    @ApiOperation(value = "问题pdf")
    public Map<String, String> pdfQuestion(@RequestParam String question) throws Exception {
        return milvusUtils.semanticSearch2(question, "pdf_markdown", "03365042031527679493");
    }

    @GetMapping("/test")
    @ApiOperation(value = "test")
    public void test() {
        milvusUtils.test();
    }

    @GetMapping("/page")
    @ApiOperation(value = "获取分页")
    @Log(value = "知识库接口-获取分页")
    public IPage<AiMilvusPdfMarkdown> getMilvusPdfMarkdownPage(@RequestParam int pageNo, @RequestParam int pageSize,
                                                               @RequestParam(value = "sortStr", required = false) String sortStr,
                                                               @RequestParam(value = "searchStr", required = false) String searchStr) {
        return aiMilvusPdfMarkdownService.getMilvusPdfMarkdownPage(pageNo, pageSize, sortStr, searchStr);
    }

    @PostMapping("/new")
    @ApiOperation(value = "文件上传")
    @Log(value = "知识库接口-文件上传")
    public void newMilvusPdfMarkdown(@RequestParam("request") String request,
                          @RequestParam("file") MultipartFile file) {
        fileToMarkdownConverter.processFile(file, request, milvusCollection);
    }

    @PostMapping("/update")
    @ApiOperation(value = "修改知识库")
    @Log(value = "知识库接口-修改知识库")
    public void updateMilvusPdfMarkdown(@RequestBody AiMilvusPdfMarkdown aiMilvusFileMarkdown) {
        aiMilvusFileMarkdown.setUpdateAt(LocalDateTime.now());
        aiMilvusPdfMarkdownService.updateMilvusPdfMarkdown(aiMilvusFileMarkdown, milvusCollection);
    }

    @PostMapping("/delete")
    @ApiOperation(value = "删除知识库")
    @Log(value = "知识库接口-删除知识库")
    public void deleteMilvusPdfMarkdown(@RequestBody AiMilvusPdfMarkdown aiMilvusFileMarkdown) throws IOException {
        aiMilvusPdfMarkdownService.deleteMilvusPdfMarkdown(aiMilvusFileMarkdown, milvusCollection);
    }

    @GetMapping("/dashboard")
    @ApiOperation(value = "数据总览")
    public JSONObject getDashBoard() {
        return aiMilvusPdfMarkdownService.getDashboard();
    }
}
