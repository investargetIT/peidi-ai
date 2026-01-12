package com.cyanrocks.ai.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cyanrocks.ai.dao.entity.AiModel;
import com.cyanrocks.ai.dao.entity.AiQueryHistory;
import com.cyanrocks.ai.dao.entity.BiGoodsReview;
import com.cyanrocks.ai.dao.mapper.AiModelMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.*;
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

    public String getIntelligenceWordCloud(List<String> wordList){
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

    public String getReviewRerank(List<BiGoodsReview> reviewList, String question){
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


    public String parseIntelligenceProduct(MultipartFile file){
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "parseIntelligenceProduct").eq(AiModel::getActive, true));
        BufferedImage image = imageConverter.toBufferedImage(file);
        final int MAX_RETRIES = 3;
        String base64Image = convertImageToJpegBase64(image);
        if (StringUtils.isEmpty(base64Image)){
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
                }catch (Exception e){
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
                        .setSocketTimeout(aiModel.getSocketTimeout())       // 读取超时（关键！）：5分钟 = 300,000 毫秒
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
                historyList.forEach(history->{
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
            }catch (SocketTimeoutException e) {
                throw e;
            } catch (Exception e) {
                System.out.println("调用模型时发生未知错误" + e);
            }
        }
        return null;
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
                user.put("content", "新问题是" + question + ",过去提问的问题是" + historyQuestion);
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
                user.put("content", "当前问题是：" + question + "\n表、字段、字段参考基础sql和业务逻辑解释是：" + text);
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
        try{
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
        }catch (Exception e){
            System.out.println("image convert error");
        }
        return null;
    }

    public String processFile(Path tempFile) {
        // 将文件上传到阿里云
        StringBuilder fullResponse = new StringBuilder();
        int maxRetries = 5;
        long baseDelay = 2000;
        for (int i = 0; i <= maxRetries; i++) {
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
                if (isRateLimitError(e) && i < maxRetries) {
                    long delay = baseDelay * (1L << i); // 指数退避：1s, 2s, 4s, 8s...
                    System.out.println("遇到限流，" + delay + "ms 后重试 (" + (i+1) + "/" + maxRetries + ")");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    System.err.println("错误信息：" + e.getMessage());
                    System.err.println("请参考文档：https://help.aliyun.com/zh/model-studio/developer-reference/error-code");
                }
            }
        }
        return fullResponse.toString();
    }

    private boolean isRateLimitError(Exception e) {
        // 判断是否是阿里云 429 错误
        return e.getMessage() != null && e.getMessage().contains("429")
                || e.getMessage().contains("rate_limit")
                || e.getMessage().contains("Too many requests");
    }

    public String processPageWithQwen(MultipartFile file){
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "processPageWithQwen").eq(AiModel::getActive, true));
        BufferedImage image = imageConverter.toBufferedImage(file);
        final int MAX_RETRIES = 3;
        String base64Image = convertImageToJpegBase64(image);
        if (StringUtils.isEmpty(base64Image)){
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
                }catch (Exception e){
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

    public String compareProductReviews(List<String> productReview, List<String> compareProductReview){
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

}
