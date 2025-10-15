package com.cyanrocks.ui.controller;

import com.alibaba.fastjson.JSON;
import com.cyanrocks.ui.exception.BusinessException;
import com.cyanrocks.ui.utils.MilvusUtils;
import com.cyanrocks.ui.utils.PdfToMarkdownConverter;
import com.cyanrocks.ui.vo.request.BiJiuqianReportReq;
import com.cyanrocks.ui.vo.request.PdfRequest;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
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
    private PdfToMarkdownConverter pdfToMarkdownConverter;


//    @GetMapping("/jiuqian-reports/question")
//    @ApiOperation(value = "问题久谦报告记录")
//    public Map<String, String> reportsQuestion(@RequestParam String question) throws Exception {
//        return milvusUtils.semanticSearch(question,"bi_jiuqian_reports");
//    }
//
//    @GetMapping("/pdf/question-old")
//    @ApiOperation(value = "问题pdf--旧")
//    public Map<String, String> pdfQuestionOld(@RequestParam String question) throws Exception {
//        return milvusUtils.semanticSearch(question,"pdf_markdown");
//    }

    @GetMapping("/pdf/question")
    @ApiOperation(value = "问题pdf")
    public Map<String, String> pdfQuestion(@RequestParam String question) throws Exception {
        return milvusUtils.semanticSearch2(question,"pdf_markdown_new","17210074020113233");
    }


    @PostMapping("/pdf")
    @ApiOperation(value = "pdf上传")
        public void uploadPdf(@RequestParam("request") String request,
                              @RequestParam("file") MultipartFile file) throws IOException {
        try {
            PdfRequest pdfRequest = JSON.toJavaObject(JSON.parseObject(request), PdfRequest.class);
            Path tempInputDir = Files.createTempDirectory("pdf_input");
            Path tempInput = tempInputDir.resolve(file.getOriginalFilename());
            file.transferTo(tempInput.toFile());

            Path tempOutput = Paths.get("pdf_output");
            Files.createDirectories(tempOutput);

//            String title = multipartFile.getOriginalFilename();
            pdfToMarkdownConverter.processPdfFolder(tempInput, tempOutput, pdfRequest);
            Files.walk(tempInputDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            System.err.println("无法删除: " + path);
                        }
                    });
            Files.walk(tempOutput)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            System.err.println("无法删除: " + path);
                        }
                    });
        }catch (Exception e){
            throw new BusinessException(500, "文件处理错误");
        }
    }
}
