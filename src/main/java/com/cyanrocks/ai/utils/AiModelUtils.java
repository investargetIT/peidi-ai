package com.cyanrocks.ai.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cyanrocks.ai.dao.entity.AiModel;
import com.cyanrocks.ai.dao.entity.AiQueryHistory;
import com.cyanrocks.ai.dao.entity.BiGoodsEvaluation;
import com.cyanrocks.ai.dao.entity.BiGoodsReview;
import com.cyanrocks.ai.dao.mapper.AiModelMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.*;
import okhttp3.*;
import okio.BufferedSource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author wjq
 * @Date 2025/11/24 15:18
 */
@Component
public class AiModelUtils {

    @Value("${dashscope.api-key}")
    private String DASHSCOPE_API_KEY;
    @Value("${grsai.api-key}")
    private String apiKey;
    @Value("${grsai.api-key-test}")
    private String apiKeyTest;

    @Autowired
    private AiModelMapper aiModelMapper;
    @Autowired
    private ImageConverter imageConverter;

    public String getTextSentiment(String text) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "getTextSentiment").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "文本是" + text);
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public String getIntelligenceWordCloud(List<String> wordList) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "getIntelligenceWordCloud").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "词组是" + String.join(",", wordList));
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public String getReviewRerank(List<BiGoodsReview> reviewList, String question) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "getReviewRerank").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "我的问题是:" + question + "\n我的评论列表是：" + JSONObject.toJSONString(reviewList));
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }


    public String parseIntelligenceProduct(MultipartFile file) {
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "parseIntelligenceProduct").eq(AiModel::getActive, true));
        BufferedImage image = imageConverter.toBufferedImage(file);
        final int MAX_RETRIES = 3;
        String base64Image = convertImageToJpegBase64(image);
        if (StringUtils.isEmpty(base64Image)) {
            return null;
        }
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                JSONObject requestBody = new JSONObject();
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
                            System.out.println(response.getStatusLine().getStatusCode());
                            System.out.println(responseBody);
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
                                return null;
                            } else {
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("execute error");
                }
            } catch (IOException e) {
                System.out.println("io error");
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
            }
        }
        return null;
    }

    public String callWithMessage(String question, String text, List<AiQueryHistory> historyList) throws SocketTimeoutException {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithMessage").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(5000)        // 连接超时：5秒
                        .setSocketTimeout(aiModel.getSocketTimeout())
                        .setConnectionRequestTimeout(5000) // 从连接池获取连接的超时
                        .build();
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setConfig(requestConfig);

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                historyList.forEach(history -> {
                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", history.getQuery());
                    messages.add(user);
                    JSONObject assistant = new JSONObject();
                    assistant.put("role", "assistant");
                    assistant.put("content", history.getResult());
                    messages.add(assistant);
                });

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "问题是" + question + "\n资料文本是" + text);
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                throw e;
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public String callWithMessageNoMarkdown(String question, String text, List<AiQueryHistory> historyList) throws SocketTimeoutException {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithMessageNoMarkdown").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(5000)        // 连接超时：5秒
                        .setSocketTimeout(aiModel.getSocketTimeout())
                        .setConnectionRequestTimeout(5000) // 从连接池获取连接的超时
                        .build();
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setConfig(requestConfig);

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                historyList.forEach(history -> {
                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", history.getQuery());
                    messages.add(user);
                    JSONObject assistant = new JSONObject();
                    assistant.put("role", "assistant");
                    assistant.put("content", history.getResult());
                    messages.add(assistant);
                });

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "问题是" + question + "\n资料文本是" + text);
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return "实在抱歉，这个问题超出我的解答范围啦，麻烦你移步项目群咨询项目辅导员，他们会及时为你答疑的～";
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                throw e;
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return "实在抱歉，这个问题超出我的解答范围啦，麻烦你移步项目群咨询项目辅导员，他们会及时为你答疑的～";
    }


    public String callWithMessageWithImg(String question, MultipartFile file, String text, List<AiQueryHistory> historyList) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithMessageWithImg").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            StringBuilder parseResult = new StringBuilder();
            try {
                JSONObject param = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();
                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                historyList.forEach(history -> {
                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", history.getQuery());
                    messages.add(user);
                    JSONObject assistant = new JSONObject();
                    assistant.put("role", "assistant");
                    assistant.put("content", history.getResult());
                    messages.add(assistant);
                });
                // 多模态
                JSONArray contentArray = new JSONArray();
                String textPrompt = "问题是：" + question + "\n资料文本是：" + text + "\n\n（以下图片是问题的补充说明，请结合图片内容回答）";
                contentArray.add(new JSONObject().fluentPut("type", "text").fluentPut("text", textPrompt));
                if (file != null && !file.isEmpty()) {
                    String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
                    String mimeType = file.getContentType();
                    if (mimeType == null) mimeType = "image/jpeg";

                    JSONObject imageUrlObj = new JSONObject();
                    imageUrlObj.put("url", "data:" + mimeType + ";base64," + base64Image);
                    contentArray.add(new JSONObject().fluentPut("type", "image_url").fluentPut("image_url", imageUrlObj));
                }
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", contentArray);

                messages.add(userMessage);
                param.put("messages", messages);
                param.put("model", aiModel.getModelName());
                param.put("stream", true);
                param.put("enable_thinking", false);

                Request request = new Request.Builder()
                        .url("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                        .post(RequestBody.create(JSONObject.toJSONString(param), MediaType.get("application/json; charset=utf-8")))
                        .addHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(600, TimeUnit.SECONDS)
                        .writeTimeout(600, TimeUnit.SECONDS)
                        .readTimeout(600, TimeUnit.SECONDS)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP error! status: " + response.code());
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("Empty response body");
                    }

                    try (ResponseBody responseBody = body) {
                        BufferedSource source = responseBody.source();
                        while (!source.exhausted()) {
                            String line = source.readUtf8Line();
                            if (line == null) continue;

                            if (line.startsWith("data: ")) {
                                String dataStr = line.substring(6).trim();
                                System.out.println("dataStr:" + dataStr);
                                if (!dataStr.isEmpty()) {
                                    try {
                                        ObjectMapper objectMapper = new ObjectMapper();
                                        JsonNode data = objectMapper.readTree(dataStr);
                                        JsonNode results = data.path("choices");
                                        if (results.isArray()) {
                                            for (JsonNode result : results) {
                                                String content = result.path("delta").path("content").asText(null);
                                                if (!"null".equals(content) && StringUtils.isNotEmpty(content)) {
                                                    parseResult.append(content);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.out.println(line);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Attempt " + (attempt + 1) + " failed: " + e.getMessage());
                    // 继续重试
                } finally {
                    client.clone();
                }
            } catch (Exception e) {
                System.err.println("Unexpected error on attempt " + (attempt + 1) + ": " + e.getMessage());
            }
            if (StringUtils.isNotEmpty(parseResult)){
                if (parseResult.toString().contains("<think>") && parseResult.toString().contains("</think>")){
                    return parseResult.toString().replaceAll("(?si)<think>.*?</think>", "");
                }
                if (!parseResult.toString().contains("<think>")){
                    return parseResult.toString();
                }
            }
            if (attempt < MAX_RETRIES - 1) {
                try {
                    Thread.sleep(10_000); // 10 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return "请稍后再试";
    }

    public String callWithMessageWithImgNoMarkdown(String question, List<MultipartFile> files, String text, List<AiQueryHistory> historyList) {
        System.out.println("接收到"+files.size()+"张图片");
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithMessageWithImgNoMarkdown").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            StringBuilder parseResult = new StringBuilder();
            try {
                JSONObject param = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();
                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                historyList.forEach(history -> {
                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", history.getQuery());
                    messages.add(user);
                    JSONObject assistant = new JSONObject();
                    assistant.put("role", "assistant");
                    assistant.put("content", history.getResult());
                    messages.add(assistant);
                });
                // 多模态消息构建
                JSONArray contentArray = new JSONArray();

                // 添加文本提示
                String textPrompt = "问题是：" + question + "\n资料文本是：" + text + "\n\n（以下图片是问题的补充说明，请结合图片内容回答）";
                contentArray.add(new JSONObject().fluentPut("type", "text").fluentPut("text", textPrompt));

                // 遍历所有图片文件并添加到内容数组
                if (files != null && !files.isEmpty()) {
                    System.out.println("添加图片补充");
                    int imgCnt = 0;
                    for (MultipartFile file : files) {
                        if (file == null || file.isEmpty()) {
                            continue; // 跳过空文件
                        }
                        try {
                            // 读取文件字节
                            byte[] imageBytes = file.getBytes();
                            if (imageBytes == null || imageBytes.length == 0) {
                                continue;
                            }
                            // 获取MIME类型（安全处理）
                            String mimeType = file.getContentType();
                            if (mimeType == null || !mimeType.startsWith("image/")) {
                                // 尝试从文件扩展名推断（基础容错）
                                String filename = file.getOriginalFilename();
                                if (filename != null && filename.toLowerCase().endsWith(".png")) {
                                    mimeType = "image/png";
                                } else if (filename != null && (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg"))) {
                                    mimeType = "image/jpeg";
                                } else {
                                    mimeType = "image/jpeg"; // 默认回退
                                }
                            }

                            // 生成标准Base64 Data URL
                            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                            String dataUrl = "data:" + mimeType + ";base64," + base64Image;
                            // 构建image_url对象
                            JSONObject imageUrlObj = new JSONObject();
                            imageUrlObj.put("url", dataUrl);
                            // 添加到内容数组
                            contentArray.add(
                                    new JSONObject()
                                            .fluentPut("type", "image_url")
                                            .fluentPut("image_url", imageUrlObj)
                            );
                            imgCnt++;
                        } catch (IOException e) {
                            // 继续处理其他图片，不中断整体流程
                        }
                        System.out.println("添加"+imgCnt+"张照片");
                    }
                }
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", contentArray);

                messages.add(userMessage);
                param.put("messages", messages);
                param.put("model", aiModel.getModelName());
                param.put("stream", true);
                param.put("enable_thinking", false);

                Request request = new Request.Builder()
                        .url("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                        .post(RequestBody.create(JSONObject.toJSONString(param), MediaType.get("application/json; charset=utf-8")))
                        .addHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(600, TimeUnit.SECONDS)
                        .writeTimeout(600, TimeUnit.SECONDS)
                        .readTimeout(600, TimeUnit.SECONDS)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP error! status: " + response.code());
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("Empty response body");
                    }

                    try (ResponseBody responseBody = body) {
                        BufferedSource source = responseBody.source();
                        while (!source.exhausted()) {
                            String line = source.readUtf8Line();
                            if (line == null) continue;

                            if (line.startsWith("data: ")) {
                                String dataStr = line.substring(6).trim();
                                System.out.println("dataStr:"+dataStr);
                                if (!dataStr.isEmpty()) {
                                    try {
                                        ObjectMapper objectMapper = new ObjectMapper();
                                        JsonNode data = objectMapper.readTree(dataStr);
                                        JsonNode results = data.path("choices");
                                        if (results.isArray()) {
                                            for (JsonNode result : results) {
                                                String content = result.path("delta").path("content").asText(null);
                                                if (!"null".equals(content) && StringUtils.isNotEmpty(content)) {
                                                    parseResult.append(content);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.out.println(line);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Attempt " + (attempt + 1) + " failed: " + e.getMessage());
                    // 继续重试
                } finally {
                    client.clone();
                }
            } catch (Exception e) {
                System.err.println("Unexpected error on attempt " + (attempt + 1) + ": " + e.getMessage());
            }
            if (StringUtils.isNotEmpty(parseResult)){
                if (parseResult.toString().contains("<think>") && parseResult.toString().contains("</think>")){
                    return parseResult.toString().replaceAll("(?si)<think>.*?</think>", "");
                }
                if (!parseResult.toString().contains("<think>")){
                    return parseResult.toString();
                }
            }
            if (attempt < MAX_RETRIES - 1) {
                try {
                    System.out.println("请求失败，重试");
                    Thread.sleep(10_000); // 10 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return "请稍后再试";
    }

    public String callWithGetDate(String question) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithGetDate").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "文本是" + question + ",提取到期日并输出yyyy-MM-dd格式的日期");
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public String callWithRewriteQuestion(String question, String historyQuestion) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithRewriteQuestion").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "新问题：" + question + "\n历史问题：" + historyQuestion
                        + "\n\n请判断：如果新问题与历史问题完全无关（是全新话题），直接原样输出新问题，不做任何修改；"
                        + "如果新问题是对历史问题的追问或延伸，则结合历史问题改写新问题使其语义完整，只输出改写后的问题。");
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public String callWithAnalysisJson(String question, String json) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithAnalysisJson").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "问题是" + question + "结果json是" + json);
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public String callWithGbiQa(String question, String text) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithGbiQa").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "当前问题是：" + question + "\n表、字段、字段参考基础sql、表基础字段sql查询100条数据和业务逻辑解释是：" + text);
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public String gbiSqlReview(String question, String text, String sql) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "gbiSqlReview").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "当前问题是：" + question + "\n表和字段逻辑解释是：" + text + "\n审查候选 SQL是：" + sql);
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public String gbiSqlRepair(String question, String text, String sql, String exception) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "gbiSqlRepair").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "当前问题是：" + question + "\n表和字段逻辑解释是：" + text + "\n待修复SQL是：" + sql + "\n待修复SQL报错：" + exception);
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    private String convertImageToJpegBase64(BufferedImage originalImage) {
        try {
            // 创建一个新的RGB图像（移除Alpha通道）
            BufferedImage jpegImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );

            // 绘制到新图像上
            jpegImage.createGraphics().drawImage(originalImage, 0, 0, Color.WHITE, null);

            // 转换为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(jpegImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            System.out.println("image convert error");
        }
        return null;
    }

    public String processFile(Path tempFile) {
        // 将文件上传到阿里云
        System.out.println("将文件上传到阿里云");
        StringBuilder fullResponse = new StringBuilder();
        int maxRetries = 5;
        long baseDelay = 2000;
        for (int i = 0; i < maxRetries; i++) {
            try {
                OpenAIClient client = OpenAIOkHttpClient.builder()
                        .apiKey(DASHSCOPE_API_KEY)
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                        .build();
                FileCreateParams fileParams = FileCreateParams.builder()
                        .file(tempFile)
                        .purpose(FilePurpose.of("file-extract"))
                        .build();
                FileObject fileObject = client.files().create(fileParams);
                AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "processFile").eq(AiModel::getActive, true));
                ChatCompletionCreateParams chatParams = ChatCompletionCreateParams.builder()
                        .addSystemMessage(aiModel.getPrompt())
                        .addSystemMessage("fileid://" + fileObject.id())
                        .addUserMessage("按要求输出")
                        .model(aiModel.getModelName())
                        .build();
                StreamResponse<ChatCompletionChunk> streamResponse = client.chat().completions().createStreaming(chatParams);
                streamResponse.stream().forEach(chunk -> {
                    String content = chunk.choices().get(0).delta().content().orElse("");
                    if (!content.isEmpty()) {
                        fullResponse.append(content);
                    }
                });
                return fullResponse.toString();
            } catch (Exception e) {
                // 检查是否是 429 限流错误
                if (isRateLimitError(e) && i < maxRetries - 1) {
                    long delay = baseDelay * (1L << i); // 指数退避：1s, 2s, 4s, 8s...
                    System.out.println("遇到限流，" + delay + "ms 后重试 (" + (i + 1) + "/" + maxRetries + ")");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    System.err.println("错误信息：" + e.getMessage());
                    System.err.println("请参考文档：https://help.aliyun.com/zh/model-studio/developer-reference/error-code");
                    if (i == maxRetries - 1) {
                        throw new RuntimeException("处理文件失败，已重试" + maxRetries + "次", e);
                    }
                }
            }
        }
        throw new RuntimeException("处理文件失败，已重试" + maxRetries + "次");
    }

    private boolean isRateLimitError(Exception e) {
        // 判断是否是阿里云 429 错误
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("429")
                || message.contains("rate_limit")
                || message.contains("Too many requests");
    }

    public String processPageWithQwen(MultipartFile file) {
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "processPageWithQwen").eq(AiModel::getActive, true));
        BufferedImage image = imageConverter.toBufferedImage(file);
        final int MAX_RETRIES = 3;
        String base64Image = convertImageToJpegBase64(image);
        if (StringUtils.isEmpty(base64Image)) {
            return null;
        }
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                JSONObject requestBody = new JSONObject();
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
                            System.out.println(response.getStatusLine().getStatusCode());
                            System.out.println(responseBody);
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
                                return null;
                            } else {
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("execute error");
                }
            } catch (IOException e) {
                System.out.println("io error");
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
            }
        }
        return null;
    }

    public String compareProductReviews(List<String> productReview, List<String> compareProductReview) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "compareProductReviews").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "我们的商品的评论是：" + String.join(";", productReview) + "\n竞品的评论是：" + String.join(";", compareProductReview));
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

    public JSONArray getChewyParse(String text,List<String> idList) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "getChewyParse").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                StringBuilder parseResult = new StringBuilder();
                JSONObject param = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();
                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "Input Data:" + text);
                messages.add(user);
                param.put("messages", messages);
                param.put("model", aiModel.getModelName());
                param.put("stream", true);

                Request request = new Request.Builder()
                        .url("https://grsaiapi.com/v1/chat/completions")
                        .post(RequestBody.create(JSONObject.toJSONString(param), MediaType.get("application/json; charset=utf-8")))
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .build();
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(600, TimeUnit.SECONDS)
                        .writeTimeout(600, TimeUnit.SECONDS)
                        .readTimeout(600, TimeUnit.SECONDS)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP error! status: " + response.code());
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("Empty response body");
                    }

                    try (ResponseBody responseBody = body) {
                        BufferedSource source = responseBody.source();
                        while (!source.exhausted()) {
                            String line = source.readUtf8Line();
                            if (line == null) continue;

                            if (line.startsWith("data: ")) {
                                String dataStr = line.substring(6).trim();
                                if (!dataStr.isEmpty()) {
                                    try {
                                        ObjectMapper objectMapper = new ObjectMapper();
                                        JsonNode data = objectMapper.readTree(dataStr);
                                        JsonNode results = data.path("choices");
                                        if (results.isArray()) {
                                            for (JsonNode result : results) {
                                                String content = result.path("delta").path("content").asText(null);
                                                if (!"null".equals(content) && StringUtils.isNotEmpty(content)) {
                                                    parseResult.append(content);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.out.println("JSON 解析失败");
                                    }
                                }
                            }
                        }
                    }
                    JSONArray resultArray = JSONArray.parseArray(parseResult.toString().replaceAll("(?si)<think>.*?</think>", ""));
                    if (CollectionUtil.isNotEmpty(resultArray) && resultArray.size() == idList.size()){
                        return resultArray;
                    }else {
                        System.err.println("Attempt " + (attempt + 1) + " failed: 生成数量不一致");
                    }
                } catch (Exception e) {
                    System.err.println("Attempt " + (attempt + 1) + " failed: " + e.getMessage());
                    // 继续重试
                } finally {
                    client.clone();
                }
            } catch (Exception e) {
                System.err.println("Unexpected error on attempt " + (attempt + 1) + ": " + e.getMessage());
            }

            if (attempt < MAX_RETRIES - 1) {
                try {
                    Thread.sleep(10_000); // 10 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    public String getEvaluation(BiGoodsEvaluation evaluation) {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "getEvaluation").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
                httpPost.setHeader("Authorization", "Bearer " + DASHSCOPE_API_KEY);
                httpPost.setHeader("Content-Type", "application/json");

                // 使用FastJSON构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", aiModel.getModelName());

                JSONObject input = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", aiModel.getPrompt());
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "评论的相关信息是：" + JSONUtil.toJsonStr(evaluation));
                messages.add(user);

                input.put("messages", messages);
                requestBody.put("input", input);

                requestBody.put("parameters", JSONObject.parse(aiModel.getParams()));

                // 设置请求体
                httpPost.setEntity(new StringEntity(
                        requestBody.toJSONString(),
                        ContentType.APPLICATION_JSON
                ));

                // 执行请求
                System.out.println("开始模型api" + LocalDateTime.now());
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    System.out.println("模型api返回" + LocalDateTime.now());
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream inputStream = entity.getContent()) {
                            String responseBody = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                JSONObject jsonResponse = JSONObject.parseObject(responseBody);
                                JSONObject output = jsonResponse.getJSONObject("output");
                                com.alibaba.fastjson.JSONArray choices = output.getJSONArray("choices");
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.getJSONObject("message");

                                // 提取文本内容
                                Object contentObj = messageObj.get("content");
                                if (contentObj instanceof com.alibaba.fastjson.JSONArray) {
                                    com.alibaba.fastjson.JSONArray contentArray = (JSONArray) contentObj;
                                    for (int i = 0; i < contentArray.size(); i++) {
                                        JSONObject item = contentArray.getJSONObject(i);
                                        if (item.containsKey("text")) {
                                            return item.getString("text");
                                        }
                                    }
                                } else if (contentObj instanceof String) {
                                    return (String) contentObj;
                                }
                                System.out.println("无法解析模型响应内容");
                                return null;
                            } else {
                                System.out.println("API错误: " + responseBody +
                                        " (状态码: " + response.getStatusLine().getStatusCode() + ")");
                                TimeUnit.SECONDS.sleep(2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
    }

}
