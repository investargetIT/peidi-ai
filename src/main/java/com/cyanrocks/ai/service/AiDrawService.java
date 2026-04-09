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
import okio.BufferedSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


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

    /**
     * Creates or updates an AI drawing task, triggers synchronous image generation, uploads generated images to OSS, and persists task metadata.
     *
     * If a task with the same UUID does not exist this method inserts a new record; otherwise it updates the existing task fields before generation.
     * After generation it stores generated image object names in the task's `imgs`, sets `updateAt`, and updates the task `status` to indicate success or failure.
     *
     * Side effects: inserts/updates the AiDraw record in the database and uploads image bytes to OSS.
     *
     * @param draw the AiDraw task to create or update and to use for generation (must contain `uuid`, `urlParam`, `size`, and `maxRetries` as applicable)
     */
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

    /**
     * Creates or updates an AiDraw task and enqueues it for asynchronous processing.
     *
     * If a task with the same UUID does not exist, the method inserts the provided task;
     * otherwise it updates the existing task's parameters. In both cases the task status
     * is set to 0 (processing) and the createAt/updateAt timestamps are cleared prior to
     * publishing the task to the draw processing queue to avoid serialization issues.
     *
     * @param draw the AiDraw task to create or update and publish for asynchronous processing
     */
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

    /**
     * Retrieve a paginated list of AiDraw records with optional dynamic search and sort.
     *
     * @param pageNo    the page number to retrieve
     * @param pageSize  the number of items per page
     * @param sortStr   JSON array string of SortReq objects used to build sort SQL; when null no custom sort is applied
     * @param searchStr JSON array string of SearchReq objects used to build search SQL; when null no search filtering is applied
     * @return          a page of AiDraw records matching the provided search and sort criteria
     */
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

    /**
     * Persists the given AI draw record to the database.
     *
     * @param aiDrawRecord the AiDrawRecord to insert; its generated id may be populated after insertion
     */
    public void newRecord(AiDrawRecord aiDrawRecord){
        aiDrawRecordMapper.insert(aiDrawRecord);
    }

    /**
     * Retrieves a paged list of AiDrawRecord entries applying optional JSON-encoded search and sort criteria.
     *
     * @param pageNo    1-based page number to retrieve
     * @param pageSize  number of records per page
     * @param sortStr   optional JSON array of SortReq objects describing sort order (null to use default)
     * @param searchStr optional JSON array of SearchReq objects describing filter conditions (null for no filters)
     * @return          a page of AiDrawRecord matching the provided search and sort criteria
     */
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

    /**
     * Requests image generation from the drawing API, retrying on failures, and downloads the generated images as raw bytes.
     *
     * This method attempts `size` generation cycles; for each cycle it calls the draw API and, if URLs are returned,
     * downloads all images and appends their byte arrays to the result. Each cycle will retry up to `maxRetries`
     * times with a short delay between attempts. If the thread is interrupted during a sleep between retries,
     * the method restores the interrupt flag and returns an empty list immediately.
     *
     * @param jsonParams JSON string sent to the drawing API describing the generation request
     * @param itemId     identifier used for logging and correlation of the request
     * @param size       number of generation cycles to perform; each successful cycle yields the images returned by the API
     * @param maxRetries maximum number of retry attempts per generation cycle (0 means a single attempt)
     * @return a list of downloaded image byte arrays in the order they were obtained; may be empty if no images were produced or if interrupted
     */
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

    /**
     * Parse the draw API's streaming response and extract generated image URLs when the job succeeds.
     *
     * @param jsonParams JSON request body sent to the draw API
     * @param itemId     identifier used for logging context
     * @return a list of image URLs produced by the API; an empty list if the stream ends without a successful result
     * @throws IOException       if the HTTP request or response body handling fails
     * @throws BusinessException if the API reports a failure status or a stream payload cannot be parsed
     */
    public List<String> fetchImageUrls(String jsonParams, String itemId) throws IOException {
        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(jsonParams, JSON))
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
        }finally {
            client.clone();
        }
    }

    /**
     * Aggregates streamed `delta.content` fragments from a chat-completions streaming response into a single string.
     *
     * Sends the provided JSON payload to the chat completions endpoint and concatenates each non-empty
     * `choices[].delta.content` value encountered in `data: ` stream events.
     *
     * @param jsonParams the JSON request payload to send to the chat-completions endpoint
     * @return the concatenated content extracted from streamed `choices[].delta.content` fragments
     * @throws BusinessException if the HTTP request fails, the response body is empty, or an I/O or parsing error occurs
     */
    public String transferGemini(String jsonParams){
        StringBuilder parseResult = new StringBuilder();
        Request request = new Request.Builder()
                .url("https://grsaiapi.com/v1/chat/completions")
                .post(RequestBody.create(jsonParams, MediaType.get("application/json; charset=utf-8")))
                .addHeader("Authorization", "Bearer " + apiKeyTest)
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
                                System.out.println(line);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException(500, " failed: " + e.getMessage());
        } finally {
            client.clone();
        }
        return parseResult.toString();
    }

    /**
     * Downloads each image at the provided URLs and returns their raw bytes.
     *
     * @param urls the list of image URLs to download
     * @return a list of byte arrays where each element is the content of the corresponding image URL
     * @throws IOException if an I/O error occurs while downloading any image
     */
    private List<byte[]> downloadImages(List<String> urls) throws IOException {
        List<byte[]> list = new ArrayList<>();
        for (String url : urls) {
            byte[] bytes = downloadImage(url);
            list.add(bytes);
        }
        return list;
    }

    /**
     * Download an image from the given URL and return its raw bytes.
     *
     * @param imageUrl the HTTP(S) URL of the image to download
     * @return the image content as a byte array
     * @throws IOException if the HTTP response is unsuccessful, the response body is empty, or a network I/O error occurs
     */
    private byte[] downloadImage(String imageUrl) throws IOException {
        Request request = new Request.Builder().url(imageUrl).build();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载图片失败: " + response.code() + ", URL: " + imageUrl);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("图片响应体为空: " + imageUrl);
            }
            return body.bytes();
        }finally {
            client.clone();
        }
    }

    /**
     * Insert a new AiDrawMaterials record into the persistent store.
     *
     * @param aiDrawMaterials the materials entity to insert; the entity may be populated with a generated id after insertion
     */
    public void newMaterials(AiDrawMaterials aiDrawMaterials){
        aiDrawMaterialsMapper.insert(aiDrawMaterials);
    }

    /**
     * Updates an existing AiDrawMaterials record in the database using the entity's id.
     *
     * @param aiDrawMaterials the material entity containing updated fields; its `id` identifies the record to update
     */
    public void updateMaterials(AiDrawMaterials aiDrawMaterials){
        aiDrawMaterialsMapper.updateById(aiDrawMaterials);
    }

    /**
     * Delete the material record identified by the provided entity's id.
     *
     * @param aiDrawMaterials an entity whose `id` field specifies the material to delete
     */
    public void deleteMaterials(AiDrawMaterials aiDrawMaterials){
        aiDrawMaterialsMapper.deleteById(aiDrawMaterials.getId());
    }

    /**
     * Retrieve a paginated list of AiDrawMaterials with optional JSON-defined search and sort.
     *
     * @param pageNo   1-based page number to fetch
     * @param pageSize number of items per page
     * @param sortStr  optional JSON array of SortReq objects that define ordering (or null)
     * @param searchStr optional JSON array of SearchReq objects that define filtering (or null)
     * @return         a page of AiDrawMaterials matching the provided search and sort criteria
     */
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
}
