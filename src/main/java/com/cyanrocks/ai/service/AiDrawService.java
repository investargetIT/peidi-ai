package com.cyanrocks.ai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyanrocks.ai.dao.entity.AiDraw;
import com.cyanrocks.ai.dao.entity.AiDrawMaterials;
import com.cyanrocks.ai.dao.entity.AiDrawRecord;
import com.cyanrocks.ai.dao.mapper.AiDrawMapper;
import com.cyanrocks.ai.dao.mapper.AiDrawMaterialsMapper;
import com.cyanrocks.ai.dao.mapper.AiDrawRecordMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.utils.OssUtils;
import com.cyanrocks.ai.utils.SearchSqlUtils;
import com.cyanrocks.ai.utils.rabbitmq.RabbitMQConfig;
import com.cyanrocks.ai.vo.request.SearchReq;
import com.cyanrocks.ai.vo.request.SortReq;
import okhttp3.OkHttpClient;
import okio.BufferedSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * @Author wjq
 * @Date 2026/1/12 14:22
 */
@Service
public class AiDrawService extends ServiceImpl<AiDrawMapper, AiDraw> {

    private static final Logger log = LoggerFactory.getLogger(AiDrawService.class);
    private static final String API_URL = "https://grsai.dakka.com.cn/v1/draw/nano-banana";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${grsai.api-key}")
    private String apiKey;
    @Value("${grsai.api-key-test}")
    private String apiKeyTest;
    @Value("${dashscope.api-key}")
    private String dashscopeApiKey;
    @Value("${qnaigc.api-key}")
    private String qnaigcApiKey;
    @Value("${coding-plan.api-key}")
    private String codingPlanApiKey;

    @Autowired
    @Qualifier("okHttpClient")
    private OkHttpClient okHttpClient;

    @Autowired
    @Qualifier("okHttpClientShort")
    private OkHttpClient okHttpClientShort;

    @Autowired
    private OssUtils ossUtils;
    @Autowired
    private SearchSqlUtils searchSqlUtils;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AiDrawMaterialsMapper aiDrawMaterialsMapper;
    @Autowired
    private AiDrawRecordMapper aiDrawPathMapper;
    @Autowired
    private AiDrawRecordMapper aiDrawRecordMapper;

    public void newDraw(AiDraw draw) {
        AiDraw exist = baseMapper.selectOne(Wrappers.<AiDraw>lambdaQuery().eq(AiDraw::getUuid,draw.getUuid()));
        if (null == exist){
            draw.setCreateAt(LocalDateTime.now());
            draw.setStatus(0);//处理中
            baseMapper.insert(draw);
        }else {
            //修改任务
            exist.setFields(draw.getFields());
            exist.setSize(draw.getSize());
            exist.setRemark(draw.getRemark());
            exist.setMaxRetries(draw.getMaxRetries());
            exist.setUrlParam(draw.getUrlParam());
            exist.setStatus(0);//处理中
            baseMapper.updateById(exist);
        }
        List<byte[]> resultList = sendDrawingRequestSync(draw.getUrlParam(), draw.getUuid(),draw.getSize(),draw.getMaxRetries());
        List<String> imgs = new ArrayList<>();
        if (null != exist && null != exist.getImgs()){
            imgs.addAll(JSONArray.parseArray(exist.getImgs(),String.class));
        }
        resultList.forEach(result->{
            String objectName = "ai/draw/grsai/"+draw.getUuid()+".png";
            ossUtils.uploadToOss(objectName, result);
            imgs.add(objectName);
        });
        draw.setUpdateAt(LocalDateTime.now());
        draw.setImgs(JSONObject.toJSONString(imgs));
        if (CollectionUtil.isNotEmpty(resultList)){
            draw.setStatus(1);//成功
        }else {
            draw.setStatus(2);//失败
        }
        baseMapper.updateById(draw);
    }

    public void newDrawAsy(AiDraw draw) {
        AiDraw exist = baseMapper.selectOne(Wrappers.<AiDraw>lambdaQuery().eq(AiDraw::getUuid,draw.getUuid()));
        if (null == exist){
            //新增一个任务
            draw.setCreateAt(LocalDateTime.now());
            draw.setStatus(0);//处理中
            baseMapper.insert(draw);
            //因为reddisMq不支持localdatetime序列化
            draw.setCreateAt(null);
            draw.setUpdateAt(null);
            rabbitTemplate.convertAndSend(RabbitMQConfig.DRAW_PROCESS_QUEUE, draw);
        }else {
            //修改任务
            exist.setUrlParam(draw.getUrlParam());
            exist.setFields(draw.getFields());
            exist.setSize(draw.getSize());
            exist.setRemark(draw.getRemark());
            exist.setMaxRetries(draw.getMaxRetries());
            exist.setUrlParam(draw.getUrlParam());
            exist.setStatus(0);//处理中
            baseMapper.updateById(exist);
            //因为reddisMq不支持localdatetime序列化
            exist.setCreateAt(null);
            exist.setUpdateAt(null);
            rabbitTemplate.convertAndSend(RabbitMQConfig.DRAW_PROCESS_QUEUE, exist);
        }
    }

    public IPage<AiDraw> getPage(int pageNo, int pageSize, String sortStr, String searchStr){
        String searchSb = null;
        if (null != searchStr) {
            List<SearchReq> searchReqs = JSONObject.parseArray(searchStr, SearchReq.class);
            searchSb = searchSqlUtils.buildSearchSql(searchReqs);
        }
        String sortSb = null;
        if (null != sortStr) {
            List<SortReq> sortReqs = JSONObject.parseArray(sortStr, SortReq.class);
            sortSb = searchSqlUtils.buildSortSql(sortReqs);
        }
        return baseMapper.getPage(new Page<>(pageNo, pageSize), searchSb, sortSb);
    }

    public void newRecord(AiDrawRecord aiDrawRecord){
        aiDrawRecordMapper.insert(aiDrawRecord);
    }

    public IPage<AiDrawRecord> getRecordPage(int pageNo, int pageSize, String sortStr, String searchStr){
        String searchSb = null;
        if (null != searchStr) {
            List<SearchReq> searchReqs = JSONObject.parseArray(searchStr, SearchReq.class);
            searchSb = searchSqlUtils.buildSearchSql(searchReqs);
        }
        String sortSb = null;
        if (null != sortStr) {
            List<SortReq> sortReqs = JSONObject.parseArray(sortStr, SortReq.class);
            sortSb = searchSqlUtils.buildSortSql(sortReqs);
        }
        return aiDrawRecordMapper.getRecordPage(new Page<>(pageNo, pageSize), searchSb, sortSb);
    }

    public List<byte[]> sendDrawingRequestSync(String jsonParams, String itemId, int size, int maxRetries) {
        List<byte[]> result = new ArrayList<>();
        for (;size > 0; size--) {
            boolean flag = true;
            Exception lastError = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    //调用绘图 API，获取图片 URL
                    List<String> imageUrls = fetchImageUrls(jsonParams, itemId);

                    //检查是否生成
                    if (!imageUrls.isEmpty()) {
                        //下载所有图片为 byte[]
                        result.addAll(downloadImages(imageUrls));
                        flag = false;
                        break;
                    } else {
                        lastError = new IllegalStateException("未生成图片");
                        log.warn("第 {} 次尝试失败 - {}", attempt + 1, lastError.getMessage());
                    }
                } catch (Exception e) {
                    lastError = e;
                    log.warn("第 {} 次请求异常 - {}", attempt + 1, e.getMessage(), e);
                }
                // 非最后一次，等待后重试
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试被中断, itemId: {}", itemId, ie);
                        return new ArrayList<>();
                    }
                }
            }
            if (flag){
                log.error("所有重试均失败，itemId: {}, 最终错误: {}", itemId, lastError != null ? lastError.getMessage() : "未知");
            }
        }
        return result;
    }

    public List<String> fetchImageUrls(String jsonParams, String itemId) throws IOException {
        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(jsonParams, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP error! status: " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }

            List<String> imageUrls = new ArrayList<>();
            try (ResponseBody responseBody = body) {
                BufferedSource source = responseBody.source();
                while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    System.out.println("line: " + line);
                    if (line == null) continue;
                    if (line.startsWith("data: ")) {
                        String dataStr = line.substring(6).trim();
                        if (!dataStr.isEmpty()) {
                            try {
                                JsonNode data = objectMapper.readTree(dataStr);
                                String status = data.path("status").asText("");

                                if ("succeeded".equals(status)) {
                                    JsonNode results = data.path("results");
                                    if (results.isArray()) {
                                        for (JsonNode result : results) {
                                            String url = result.path("url").asText(null);
                                            if (url != null && !url.isEmpty()) {
                                                imageUrls.add(url);
                                            }
                                        }
                                    }
                                    return imageUrls;
                                } else if ("failed".equals(status)) {
                                    String error = data.path("error").asText("Unknown error");
                                    log.warn("Draw failed: {}", error);
                                    throw new BusinessException(500, "Draw failed: " + error);
                                }
                            } catch (Exception e) {
                                log.warn("itemId: {}, data: {}", itemId, dataStr, e);
                                throw new BusinessException(500, dataStr);
                            }
                        }
                    }
                }
            }
            return imageUrls;
        }
    }

    public String transferGemini(String jsonParams){
        StringBuilder parseResult = new StringBuilder();
        Request request = new Request.Builder()
                .url("https://grsaiapi.com/v1/chat/completions")
                .post(RequestBody.create(jsonParams, MediaType.get("application/json; charset=utf-8")))
                .addHeader("Authorization", "Bearer " + apiKeyTest)
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
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
                                System.out.println(line);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException(500, " failed: " + e.getMessage());
        }
        return parseResult.toString();
    }

    public String transferAliyun(AiDraw aiDraw) {
        String apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(aiDraw.getUrlParam(), JSON))
                .addHeader("Authorization", "Bearer " + dashscopeApiKey)
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new BusinessException(500, "Aliyun API error: " + response.code() + " " + errBody);
            }
            String body = response.body().string();
            log.info("Aliyun response: {}", body);
            JsonNode root = objectMapper.readTree(body);

            //不解析直接返回
            if (StringUtils.isNotEmpty(aiDraw.getResultImage()) && aiDraw.getResultImage().equals("false")) {
                return root.toString();
            }
            JsonNode choices = root.path("output").path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (content.isArray()) {
                    for (JsonNode item : content) {
                        String imageUrl = item.path("image").asText(null);
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            return imageUrl;
                        }
                    }
                }
            }
            throw new BusinessException(500, "No image in response: " + body);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "transferAliyun error: " + e.getMessage());
        }
    }

    public String transferQnaigc(AiDraw aiDraw) {
        String apiUrl = "https://api.qnaigc.com/v1/images/edits";
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(aiDraw.getUrlParam(), JSON))
                .addHeader("Authorization", "Bearer " + qnaigcApiKey)
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new BusinessException(500, "Qnaigc API error: " + response.code() + " " + errBody);
            }
            String body = response.body().string();

            log.info("Qnaigc response: {}", body);
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if(data.isArray() && data.size() > 0){
                return root.path("data").toString();
            }
            throw new BusinessException(500, "No image in response: " + body);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "transferQnaigc error: " + e.getMessage());
        }
    }

    private List<byte[]> downloadImages(List<String> urls) throws IOException {
        List<byte[]> list = new ArrayList<>();
        for (String url : urls) {
            byte[] bytes = downloadImage(url);
            list.add(bytes);
        }
        return list;
    }

    private byte[] downloadImage(String imageUrl) throws IOException {
        Request request = new Request.Builder().url(imageUrl).build();
        try (Response response = okHttpClientShort.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载图片失败: " + response.code() + ", URL: " + imageUrl);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("图片响应体为空: " + imageUrl);
            }
            return body.bytes();
        }
    }

    public void newMaterials(AiDrawMaterials aiDrawMaterials){
        aiDrawMaterialsMapper.insert(aiDrawMaterials);
    }

    public void updateMaterials(AiDrawMaterials aiDrawMaterials){
        aiDrawMaterialsMapper.updateById(aiDrawMaterials);
    }

    public void deleteMaterials(AiDrawMaterials aiDrawMaterials){
        aiDrawMaterialsMapper.deleteById(aiDrawMaterials.getId());
    }

    public IPage<AiDrawMaterials> getMaterialsPage(int pageNo, int pageSize, String sortStr, String searchStr){
        String searchSb = null;
        if (null != searchStr) {
            List<SearchReq> searchReqs = JSONObject.parseArray(searchStr, SearchReq.class);
            searchSb = searchSqlUtils.buildSearchSql(searchReqs);
        }
        String sortSb = null;
        if (null != sortStr) {
            List<SortReq> sortReqs = JSONObject.parseArray(sortStr, SortReq.class);
            sortSb = searchSqlUtils.buildSortSql(sortReqs);
        }
        return baseMapper.getMaterialsPage(new Page<>(pageNo, pageSize), searchSb, sortSb);
    }

    public String transferCodingPlan(AiDraw aiDraw) {
        String apiUrl = "https://coding.dashscope.aliyuncs.com/v1/chat/completions";
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(aiDraw.getUrlParam(), JSON))
                .addHeader("Authorization", "Bearer " + codingPlanApiKey)
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new BusinessException(500, "CodingPlan API error: " + response.code() + " " + errBody);
            }
            String body = response.body().string();
            log.info("CodingPlan response: {}", body);
            JsonNode root = objectMapper.readTree(body);
            return root.toString();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "transferCodingPlan error: " + e.getMessage());
        }
    }
}
