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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypes;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    @Value("${dashscope.api-key}")
    private String dashscopeApiKey;

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

    /**
     * Ingests an uploaded file, extracts or generates text/markdown (synchronously or by enqueuing PDF chunks), persists an initial DB record, uploads artifacts to OSS, and writes extracted data into Milvus.
     *
     * Processes the provided MultipartFile according to its detected MIME type: non-PDF files are converted and processed immediately; PDFs are split into overlapping chunks and either processed immediately if single-chunk or enqueued to RabbitMQ for asynchronous chunk processing. The method also uploads the original file to OSS and inserts or updates records used for later Milvus ingestion.
     *
     * @param file       the uploaded file to process
     * @param request    JSON string representing an AiMilvusPdfMarkdown request object (used to build DB/Milvus records)
     * @param milvusFile identifier or collection name used when persisting results into Milvus
     * @throws BusinessException when a file with the same title already exists, when chunked-PDF processing detects an existing Milvus record, or when saving processed results to Milvus fails
     */
    public void processFile(MultipartFile file, String request, String milvusFile) {
        //防止重复上传
        if (CollectionUtil.isNotEmpty(aiMilvusPdfMarkdownMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                .eq(AiMilvusPdfMarkdown::getTitle, JSON.toJavaObject(JSON.parseObject(request), AiMilvusPdfMarkdown.class).getTitle())))) {
            throw new BusinessException(500, "该文件已存在");
        }
        //先存一份数据
        System.out.println("先存一份数据");
        AiMilvusPdfMarkdown initPdf = JSON.toJavaObject(JSON.parseObject(request), AiMilvusPdfMarkdown.class);
        initPdf.setCreateAt(LocalDateTime.now());
        initPdf.setText("");
        initPdf.setBatchNo(0);
        initPdf.setSource("");
        aiMilvusPdfMarkdownMapper.insert(initPdf);

        try {
            // 先创建临时文件并保存上传文件内容
            Path tempFile = Files.createTempFile("openai_", file.getOriginalFilename());
            file.transferTo(tempFile.toFile());

            // 根据文件扩展名判断文件类型（比 Tika 检测更可靠）
            String filename = file.getOriginalFilename();
            String extension = filename != null && filename.contains(".")
                    ? filename.substring(filename.lastIndexOf(".")).toLowerCase()
                    : "";
            String realType;
            if (extension.equals(".pdf")) {
                realType = "application/pdf";
            } else if (extension.equals(".docx")) {
                realType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (extension.equals(".doc")) {
                realType = "application/msword";
            } else {
                // 其他类型尝试用 Tika 检测
                Tika tika = new Tika();
                realType = tika.detect(tempFile);
            }
            System.out.println("上传文件类型:" + realType + ", 扩展名:" + extension);

            //文件存储在oss中
            String source = UPLOAD_PDF_PATH + tempFile.getFileName().toString();
            ossUtils.uploadToOss(source, Files.readAllBytes(tempFile));
            List<AiMilvusPdfMarkdown> pdfRecordMilvusList = new ArrayList<>();
            if (realType.equals("application/pdf")) {
                System.out.println("文件为pdf");
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
                    //判断是否有初始新建文件
                    if (CollectionUtil.isNotEmpty(aiMilvusPdfMarkdownMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                            .eq(AiMilvusPdfMarkdown::getTitle, pdfRequest.getTitle()).isNotNull(AiMilvusPdfMarkdown::getMilvusId)))) {
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
                    Files.deleteIfExists(tempFile);
                    return; // 异步处理，直接返回
                }
            } else if (realType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || realType.equals("application/msword")) {
                // 处理 DOC/DOCX 文件，参考 PDF 拆分逻辑
                System.out.println("文件为doc/docx");
                List<byte[]> splitDocs = splitDocxWithOverlap(tempFile);
                System.out.println("文件拆分为" + splitDocs.size() + "份");
                if (splitDocs.size() == 1) {
                    // 页数较少，不拆分，直接处理
                    String fullResponse = aiModelUtils.processFile(tempFile);
                    AiMilvusPdfMarkdown pdfRequest = JSON.toJavaObject(JSON.parseObject(request), AiMilvusPdfMarkdown.class);
                    pdfRequest.setCreateAt(LocalDateTime.now());
                    pdfRequest.setText(fullResponse);
                    pdfRequest.setBatchNo(1);
                    pdfRequest.setSource(source);
                    pdfRecordMilvusList.add(pdfRequest);
                } else {
                    // 需要拆分，发送到 RabbitMQ 异步处理
                    AiMilvusPdfMarkdown pdfRequest = JSON.toJavaObject(JSON.parseObject(request), AiMilvusPdfMarkdown.class);
                    //防止重复上传，中断请求模型操作
                    String lockKey = "doc_upload_lock:" + pdfRequest.getTitle();
                    // 加锁（20分钟过期，防死锁）
                    Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(20));
                    if (Boolean.FALSE.equals(locked)) {
                        if (CollectionUtil.isNotEmpty(aiMilvusPdfMarkdownMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                                .eq(AiMilvusPdfMarkdown::getTitle, pdfRequest.getTitle())))) {
                            throw new BusinessException(500, "文件已存在");
                        } else {
                            throw new BusinessException(500, "文件正在处理中，请稍后再试");
                        }
                    }
                    //判断是否有初始新建文件
                    if (CollectionUtil.isNotEmpty(aiMilvusPdfMarkdownMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                            .eq(AiMilvusPdfMarkdown::getTitle, pdfRequest.getTitle()).isNotNull(AiMilvusPdfMarkdown::getMilvusId)))) {
                        throw new BusinessException(500, "该文件已存在");
                    }
                    // 发送所有分片任务到rabbitmq并发模型处理
                    String requestId = UUID.randomUUID().toString();
                    for (int i = 0; i < splitDocs.size(); i++) {
                        System.out.println("存入rabbitMQ第" + (i + 1) + "份");

                        PdfChunkTask task = new PdfChunkTask();
                        task.setRequestId(requestId);
                        task.setRequestStr(request);
                        task.setSplitPdf(splitDocs.get(i)); // 使用同一个 PdfChunkTask，字段名虽为 splitPdf 但实际可以是任意文件字节
                        task.setBatchNo(i + 1);
                        task.setSource(source);
                        task.setOriginalFilename(file.getOriginalFilename());
                        rabbitTemplate.convertAndSend(RabbitMQConfig.PDF_PROCESS_QUEUE, task);
                    }
                    Files.deleteIfExists(tempFile);
                    return; // 异步处理，直接返回
                }
            } else {
                System.out.println("文件为" + realType);
                String fullResponse = aiModelUtils.processFile(tempFile);
                System.out.println("处理阿里云返回数据");
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

    /**
     * 拆分 DOCX 文件（按内容长度拆分）
     * 每 2500 个字符作为一个片段，有重叠
     * @param docxPath DOCX 文件路径
     * @return 拆分后的 DOCX 文件列表（每个元素是一个 DOCX 文件的字节数组）
     */
    public List<byte[]> splitDocxWithOverlap(Path docxPath) {

        AiEnum chunkSize = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                .eq(AiEnum::getType, "chunkSize"));
        AiEnum chunkSizeOverlap = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                .eq(AiEnum::getType, "chunkSizeOverlap"));
        // 每个片段的字符数
        final int CHUNK_SIZE = Integer.parseInt(chunkSize.getValue());
        // 重叠字符数
        final int OVERLAP_SIZE = Integer.parseInt(chunkSizeOverlap.getValue());
        try {
            // 1. 打开 DOCX 文件，读取所有文本内容
            try (XWPFDocument document = new XWPFDocument(Files.newInputStream(docxPath))) {
                List<XWPFParagraph> paragraphs = document.getParagraphs();

                // 2. 提取全部文本
                StringBuilder fullText = new StringBuilder();
                for (XWPFParagraph para : paragraphs) {
                    String text = para.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        fullText.append(text).append("\n");
                    }
                }

                String content = fullText.toString();
                int totalLength = content.length();

                logger.info("DOCX 文件总字符数: " + totalLength);

                // 3. 如果总字符数 <= CHUNK_SIZE，不拆分，返回整个文件
                if (totalLength <= CHUNK_SIZE) {
                    return Collections.singletonList(Files.readAllBytes(docxPath));
                }

                // 4. 按 CHUNK_SIZE 拆分，有重叠
                List<byte[]> resultBytes = new ArrayList<>();
                int currentStart = 0;

                while (currentStart < totalLength) {
                    // 计算当前分片的结束位置
                    int endPos = Math.min(currentStart + CHUNK_SIZE, totalLength);

                    // 提取当前分片的文本
                    String chunkText = content.substring(currentStart, endPos);

                    // 创建新的 DOCX 文档
                    try (XWPFDocument partDoc = new XWPFDocument()) {
                        XWPFParagraph newPara = partDoc.createParagraph();
                        newPara.createRun().setText(chunkText);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        partDoc.write(baos);
                        resultBytes.add(baos.toByteArray());
                    }

                    // 如果已到末尾，退出
                    if (endPos >= totalLength) {
                        break;
                    }

                    // 重叠：下一个分片从当前分片的最后 OVERLAP_SIZE 个字符开始
                    currentStart = endPos - OVERLAP_SIZE;
                }

                logger.info("DOCX 文件拆分为 " + resultBytes.size() + " 份");
                return resultBytes;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "拆分 DOCX 文件失败: " + docxPath, e);
            throw new BusinessException(500, "DOCX 文件拆分失败: " + e.getMessage());
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
                httpPost.setHeader("Authorization", "Bearer " + dashscopeApiKey);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "processPageWithQwen").eq(AiModel::getActive, 1));

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