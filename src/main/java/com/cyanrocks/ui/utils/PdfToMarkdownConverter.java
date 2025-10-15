package com.cyanrocks.ui.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ui.vo.PdfRecordMilvus;
import com.cyanrocks.ui.vo.request.PdfRequest;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class PdfToMarkdownConverter {

    private static final Logger logger = Logger.getLogger(PdfToMarkdownConverter.class.getName());
//    private static final String DASHSCOPE_API_KEY = "sk-0583e55fc7834aa9a536779bd70b1b15";//旧的
    private static final String DASHSCOPE_API_KEY = "sk-17ec61d83bba433f8acb638aeced5ab8";
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String MODEL_NAME = "qwen2.5-vl-72b-instruct";

    private static final String UPLOAD_PDF_PATH = "ai/pdf/";
    private static final String UPLOAD_MARKDOWN_PATH = "ai/pdf-markdown/";

    private static final String milvusFile = "pdf_markdown_new";

    @Autowired
    private MilvusUtils milvusUtils;
    @Autowired
    private OssUtils ossUtils;

    public void processPdfFolder(Path inputPath, Path outputPath, PdfRequest request) {
        try {
            List<Path> pdfFiles = new ArrayList<>();
            Files.walkFileTree(inputPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().toLowerCase().endsWith(".pdf")) {
                        pdfFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (pdfFiles.isEmpty()) {
                logger.warning("未找到 PDF 文件");
                return;
            }

            logger.info("找到 " + pdfFiles.size() + " 个 PDF 文件");

            for (int i = 0; i < pdfFiles.size(); i++) {
                Path pdfFile = pdfFiles.get(i);
                logger.info("\n=== 处理第 " + (i + 1) + "/" + pdfFiles.size() +
                        " 个文件: " + pdfFile.getFileName() + " ===");
                String source = UPLOAD_PDF_PATH + pdfFile.getFileName().toString();
                request.setSource(source);
                ossUtils.uploadToOss(source, Files.readAllBytes(pdfFile));
                String outputFilename = pdfFile.getFileName().toString()
                        .replace(".pdf", "_content.md");
                Path outputFile = outputPath.resolve(outputFilename);
                try {
                    processPdfToMarkdown(pdfFile, outputFile, request);
                    logger.info("转换完成，结果已保存到: " + outputFile);

                    // 将文件保存到oss中
                    ossUtils.uploadToOss(UPLOAD_MARKDOWN_PATH + outputFilename, Files.readAllBytes(outputFile));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "处理 " + pdfFile.getFileName() + " 时出错", e);
                }
            }

            logger.info("\n=== 所有文件处理完成 ===");
        } catch (IOException e) {
            logger.severe("扫描PDF文件时出错: " + e.getMessage());
        }
    }

    public void processPdfToMarkdown(Path pdfPath, Path outputPath,PdfRequest request) throws Exception {
        List<BufferedImage> images = pdfToImages(pdfPath);
        if (images.isEmpty()) {
            logger.severe("无法转换PDF: " + pdfPath);
            return;
        }

        String markdownContent = "# " + pdfPath.getFileName().toString().replace(".pdf", "") + "\n\n";
        String prompt = "提取页面上的所有文本内容，以 Markdown 格式返回。";

        for (int i = 0; i < images.size(); i++) {

            logger.info("处理第 " + (i + 1) + "/" + images.size() + " 页");
            String pageContent = processPageWithQwen(images.get(i), prompt);
            logger.info((i + 1) + "/" + images.size() + " 页处理完成");
            String localMarkdownContent = "# " + pdfPath.getFileName().toString().replace(".pdf", "") + "\n\n"
                    + (pageContent != null ? pageContent : "[未能提取内容]") +
                    "\n\n";
            markdownContent += "## 第 " + (i + 1) + " 页\n\n" +
                    (pageContent != null ? pageContent : "[未能提取内容]") +
                    "\n\n";
            List<PdfRequest> pdfRecordMilvusList = new ArrayList<>();
            request.setText(localMarkdownContent);
            request.setBatchNo(i);
            pdfRecordMilvusList.add(request);
            milvusUtils.processPdfReportData(pdfRecordMilvusList,milvusFile);

            try {
                TimeUnit.SECONDS.sleep(1); // 避免速率限制
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Files.write(outputPath, markdownContent.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static List<BufferedImage> pdfToImages(Path pdfPath) {
        List<BufferedImage> images = new ArrayList<>();
        final int DPI = 200;

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, DPI);
                images.add(image);
            }
            logger.info("成功将PDF转换为 " + images.size() + " 张图像");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "转换PDF为图像时出错: " + pdfPath, e);
        }
        return images;
    }

    public static String processPageWithQwen(BufferedImage image, String prompt) {
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
                requestBody.put("model", MODEL_NAME);

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
                textContent.put("text", prompt);
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