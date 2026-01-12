package com.cyanrocks.ai.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cyanrocks.ai.dao.entity.AiEnum;
import com.cyanrocks.ai.dao.entity.AiMilvusPdfMarkdown;
import com.cyanrocks.ai.dao.entity.AiModel;
import com.cyanrocks.ai.dao.mapper.AiEnumMapper;
import com.cyanrocks.ai.dao.mapper.AiMilvusPdfMarkdownMapper;
import com.cyanrocks.ai.dao.mapper.AiModelMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.utils.rabbitmq.PdfChunkTask;
import com.cyanrocks.ai.utils.rabbitmq.RabbitMQConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.tika.Tika;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class FileToMarkdownConverter {

    private static final Logger logger = Logger.getLogger(FileToMarkdownConverter.class.getName());
    private static final String DASHSCOPE_API_KEY = "";
//    private static final String DASHSCOPE_API_KEY = "sk-17ec61d83bba433f8acb638aeced5ab8";
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String MODEL_NAME = "qwen2.5-vl-72b-instruct";

    private static final String UPLOAD_PDF_PATH = "ai/pdf/";
    private static final String UPLOAD_MARKDOWN_PATH = "ai/pdf-markdown/";

    @Autowired
    private MilvusUtils milvusUtils;
    @Autowired
    private OssUtils ossUtils;
    @Autowired
    private AiMilvusPdfMarkdownMapper aiMilvusPdfMarkdownMapper;
    @Autowired
    private ImageConverter imageConverter;
    @Autowired
    private AiModelMapper aiModelMapper;
    @Autowired
    private AiModelUtils aiModelUtils;
    @Autowired
    private AiEnumMapper aiEnumMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void processFile(MultipartFile file, String request, String milvusFile) {
        //防止重复上传
        if (CollectionUtil.isNotEmpty(aiMilvusPdfMarkdownMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                .eq(AiMilvusPdfMarkdown::getTitle, JSON.toJavaObject(JSON.parseObject(request), AiMilvusPdfMarkdown.class).getTitle())))) {
            throw new BusinessException(500, "该文件已存在");
        }

        try {
            InputStream is = file.getInputStream();
            Tika tika = new Tika();
            String realType = tika.detect(is);
            System.out.println("上传文件类型:" + realType);
            //完整文件创建临时文件
            Path tempFile = Files.createTempFile("openai_", file.getOriginalFilename());
            file.transferTo(tempFile.toFile());
            //文件存储在oss中
            String source = UPLOAD_PDF_PATH + tempFile.getFileName().toString();
            ossUtils.uploadToOss(source, Files.readAllBytes(tempFile));
            List<AiMilvusPdfMarkdown> pdfRecordMilvusList = new ArrayList<>();
            if (realType.equals("application/pdf")) {
                //pdf文件进行chunk
                List<byte[]> splitPdfs = splitPdfWithOverlap(tempFile);
                System.out.println("文件拆分为" + splitPdfs.size() + "份");
                if (splitPdfs.size() ==1){
                    //只有10页
                    String fullResponse = aiModelUtils.processFile(tempFile);
                    AiMilvusPdfMarkdown pdfRequest = JSON.toJavaObject(JSON.parseObject(request), AiMilvusPdfMarkdown.class);
                    pdfRequest.setCreateAt(LocalDateTime.now());
                    pdfRequest.setText(fullResponse);
                    pdfRequest.setBatchNo(1);
                    pdfRequest.setSource(source);
                    pdfRecordMilvusList.add(pdfRequest);
                }else {
                    AiMilvusPdfMarkdown pdfRequest = JSON.toJavaObject(JSON.parseObject(request), AiMilvusPdfMarkdown.class);
                    //防止重复上传，中断请求模型操作
                    String lockKey = "pdf_upload_lock:" + pdfRequest.getTitle();
                    // 加锁（20分钟过期，防死锁）
                    Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(20));
                    if (Boolean.FALSE.equals(locked)) {
                        // 检查是否已有数据？或直接拒绝
                        if (CollectionUtil.isNotEmpty(aiMilvusPdfMarkdownMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                                .eq(AiMilvusPdfMarkdown::getTitle, pdfRequest.getTitle())))) {
                            throw new BusinessException(500, "文件已存在");
                        } else {
                            throw new BusinessException(500, "文件正在处理中，请稍后再试");
                        }
                    }
                    if (CollectionUtil.isNotEmpty(aiMilvusPdfMarkdownMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                            .eq(AiMilvusPdfMarkdown::getTitle, pdfRequest.getTitle())))) {
                        throw new BusinessException(500, "该文件已存在");
                    }
                    // 如果没重复，发送所有分片任务到rabbitmq并发模型处理
                    String requestId = UUID.randomUUID().toString();
                    for (int i = 0; i < splitPdfs.size(); i++) {
                        System.out.println("存入rabbitMQ第" + (i+1) + "份");

                        PdfChunkTask task = new PdfChunkTask();
                        task.setRequestId(requestId);
                        task.setRequestStr(request);
                        task.setSplitPdf(splitPdfs.get(i));
                        task.setBatchNo(i + 1);
                        task.setSource(source);
                        task.setOriginalFilename(file.getOriginalFilename());
                        rabbitTemplate.convertAndSend(RabbitMQConfig.PDF_PROCESS_QUEUE, task);
                    }
                }
            }else {
                String fullResponse = aiModelUtils.processFile(tempFile);
                AiMilvusPdfMarkdown pdfRequest = JSON.toJavaObject(JSON.parseObject(request), AiMilvusPdfMarkdown.class);
                pdfRequest.setCreateAt(LocalDateTime.now());
                pdfRequest.setText(fullResponse);
                pdfRequest.setBatchNo(1);
                pdfRequest.setSource(source);
                pdfRecordMilvusList.add(pdfRequest);
            }
            Files.deleteIfExists(tempFile);
            try {
                System.out.println("开始写入数据库");
                milvusUtils.processFileData(pdfRecordMilvusList, milvusFile);
            } catch (Exception e) {
                throw new BusinessException(500, "保存失败");
            }
        } catch (IOException e) {
            System.out.println("扫描文件时出错: " + e.getMessage());
        }

    }

    public List<byte[]> splitPdfWithOverlap(Path file) {
        try (PDDocument document = PDDocument.load(file.toFile())) {
            document.setAllSecurityToBeRemoved(true);
            int totalPages = document.getNumberOfPages();
            AiEnum chunkPageEnum = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                    .eq(AiEnum::getType, "chunkPage"));
            int chunkPage = Integer.parseInt(chunkPageEnum.getValue());
            // 1. 页数 ≤ 10，不拆分
            if (totalPages <= chunkPage) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                document.save(baos);
                return Collections.singletonList(baos.toByteArray());
            }

            List<byte[]> resultBytes = new ArrayList<>();
            int currentStart = 1; // 1-based，用户视角的页码

            while (currentStart <= totalPages) {
                // 计算当前分片的结束页（最多取10页）
                int endPage = Math.min(currentStart + (chunkPage-1), totalPages);

                // 创建新文档，收集 [currentStart, endPage] 的页（注意：PDFBox 是 0-based）
                PDDocument partDoc = new PDDocument();
                try {
                    for (int i = currentStart - 1; i <= endPage - 1; i++) {
                        PDPage page = document.getPage(i);
                        partDoc.addPage(page);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    partDoc.save(baos);
                    resultBytes.add(baos.toByteArray());
                } finally {
                    partDoc.close();
                }

                // 更新下一段的起始页：重叠最后一页
                if (endPage >= totalPages) {
                    break; // 已到末尾，退出
                }
                currentStart = endPage; // 重叠：下一段从当前段最后一页开始
            }

            return resultBytes;
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw new BusinessException(500, "文件切分失败");
        }
    }

    public void processFolder(Path inputPath, Path outputPath, AiMilvusPdfMarkdown request, String collection) {
        try {
            List<Path> pdfFiles = new ArrayList<>();
            Files.walkFileTree(inputPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    pdfFiles.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });

            if (pdfFiles.isEmpty()) {
                logger.warning("未找到文件");
                return;
            }

            logger.info("找到 " + pdfFiles.size() + " 个 文件");

            for (int i = 0; i < pdfFiles.size(); i++) {
                Path filePath = pdfFiles.get(i);
                logger.info("\n=== 处理第 " + (i + 1) + "/" + pdfFiles.size() +
                        " 个文件: " + filePath.getFileName() + " ===");
                String source = UPLOAD_PDF_PATH + filePath.getFileName().toString();
                if (CollectionUtil.isNotEmpty(aiMilvusPdfMarkdownMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery().eq(AiMilvusPdfMarkdown::getTitle, request.getTitle())))) {
                    throw new BusinessException(500, "该文件已存在");
                }
                request.setSource(source);
                ossUtils.uploadToOss(source, Files.readAllBytes(filePath));
                String outputFilename = filePath.getFileName().toString().replace(".pdf", "_content.md")
                        .replace(".doc", "_content.md").replace(".docx", "_content.md");
                Path outputFile = outputPath.resolve(outputFilename);
                try {
                    processFileToMarkdown(filePath, outputFile, request, collection);
                    logger.info("转换完成，结果已保存到: " + outputFile);

                    // 将文件保存到oss中
                    ossUtils.uploadToOss(UPLOAD_MARKDOWN_PATH + outputFilename, Files.readAllBytes(outputFile));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "处理 " + filePath.getFileName() + " 时出错", e);
                }
            }

            logger.info("\n=== 所有文件处理完成 ===");
        } catch (IOException e) {
            logger.severe("扫描文件时出错: " + e.getMessage());
        }
    }

    public void processFileToMarkdown(Path filePath, Path outputPath, AiMilvusPdfMarkdown request, String collection) throws Exception {
        List<BufferedImage> images = new ArrayList<>();
        if (filePath.getFileName().toString().endsWith(".docx")) {
            images = imageConverter.docxToImages(filePath);
        }
        if (filePath.getFileName().toString().endsWith(".pdf")) {
            images = imageConverter.pdfToImages(filePath);
        }
        if (images.isEmpty()) {
            logger.severe("无法转换: " + filePath);
            return;
        }

        String markdownContent = "# " + filePath.getFileName().toString().replace(".pdf", "") + "\n\n";

        for (int i = 0; i < images.size(); i++) {

            logger.info("处理第 " + (i + 1) + "/" + images.size() + " 页");
            String pageContent = processPageWithQwen(images.get(i));
            logger.info((i + 1) + "/" + images.size() + " 页处理完成");
            String localMarkdownContent = "# " + filePath.getFileName().toString().replace(".pdf", "")
                    .replace(".doc", "").replace(".docx", "") + "\n\n"
                    + (pageContent != null ? pageContent : "[未能提取内容]") +
                    "\n\n";
            markdownContent += "## 第 " + (i + 1) + " 页\n\n" +
                    (pageContent != null ? pageContent : "[未能提取内容]") +
                    "\n\n";
            List<AiMilvusPdfMarkdown> pdfRecordMilvusList = new ArrayList<>();
            request.setText(localMarkdownContent);
            request.setBatchNo(i);
            pdfRecordMilvusList.add(request);
            milvusUtils.processFileData(pdfRecordMilvusList, collection);
            try {
                TimeUnit.SECONDS.sleep(1); // 避免速率限制
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Files.write(outputPath, markdownContent.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public String processPageWithQwen(BufferedImage image) {
        final int MAX_RETRIES = 3;
        String base64Image = imageToBase64(image);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost(API_URL);
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "processPageWithQwen").eq(AiModel::getActive, true));

                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                JSONArray messages = new JSONArray();

                JSONObject message = new JSONObject();
                message.put("role", "user");

                JSONArray content = new JSONArray();

                // 添加图像内容
                JSONObject imageContent = new JSONObject();
                imageContent.put("image", "data:image/jpeg;base64," + base64Image);
                content.add(imageContent);

                // 添加文本提示
                JSONObject textContent = new JSONObject();
                textContent.put("text", aiModel.getPrompt());
                content.add(textContent);

                message.put("content", content);
                messages.add(message);

                input.put("messages", messages);
                requestBody.put("input", input);

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof JSONArray) {
                                    JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                logger.warning("无法解析模型响应内容");
                                return null;
                            } else {
                                logger.warning("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                logger.log(Level.SEVERE, "处理页面时出错", e);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "处理页面时发生未知错误", e);
            }
        }
        return null;
    }

    private static String imageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "转换图像为Base64时出错", e);
            return "";
        }
    }
}