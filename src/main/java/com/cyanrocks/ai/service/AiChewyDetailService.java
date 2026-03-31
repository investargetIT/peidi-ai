package com.cyanrocks.ai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyanrocks.ai.dao.entity.AiChewyDetail;
import com.cyanrocks.ai.dao.entity.AiModel;
import com.cyanrocks.ai.dao.entity.AiQueryHistory;
import com.cyanrocks.ai.dao.mapper.AiChewyDetailMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.utils.AiModelUtils;
import com.cyanrocks.ai.utils.EmbeddingResourceManager;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.utils.UUIDConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import okhttp3.*;
import okio.BufferedSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author wjq
 * @Date 2025/12/23 17:08
 */
@Service
public class AiChewyDetailService extends ServiceImpl<AiChewyDetailMapper, AiChewyDetail> {

    @Autowired
    private AiModelUtils aiModelUtils;

    @Autowired
    private MilvusUtils milvusUtils;

    @Value("${milvus.uri}")
    private String milvusUri;

    @Value("${grsai.api-key}")
    private String apiKey;

    @Autowired
    private EmbeddingResourceManager embeddingResourceManager;

    public JSONObject getChewyList(String keyword, String function, Boolean score, Boolean redFlag){
        JSONObject result = new JSONObject();

        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        StringBuilder filter = new StringBuilder("id > 0");
        if (StringUtils.isNotEmpty(keyword)){
            filter.append(" and productName like '%").append(keyword.replace("'", "''")).append("%'");
        }
        if (StringUtils.isNotEmpty(function)){
            filter.append(" and primaryFunction == '").append(function.replace("'", "''")).append("'");
        }
        if (score){
            filter.append(" and healthScore > 8");
        }
        if (redFlag){
            filter.append(" and redFlags == '[]'");
        }
        QueryReq queryReq = QueryReq.builder()
                .collectionName("chewy_parse_new")
                .filter(filter.toString())
                .build();
        QueryResp queryResp = client.query(queryReq);
        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
        JSONArray jsonArray = new JSONArray(queryResults.size());
        for (QueryResp.QueryResult queryResult : queryResults) {
            jsonArray.add(JSONObject.parseObject((String) queryResult.getEntity().get("text")));
        }
        result.put("data", jsonArray);
        result.put("cnt", 9555);
        return result;
    }

    public void test(){
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        QueryReq queryReq = QueryReq.builder()
                .collectionName("chewy_parse_new")
                .filter("id > 0 ")
                .build();
        QueryResp queryResp = client.query(queryReq);
        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
        Set<String> function = new HashSet<>();
        for (QueryResp.QueryResult queryResult : queryResults) {
            Map<String, Object> entity = queryResult.getEntity();
            String primaryFunction = (String) entity.get("primaryFunction");
            function.add(primaryFunction);
        }
        System.out.println(JSONObject.toJSONString(function));
    }

    public void test1(){
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        QueryReq queryReq = QueryReq.builder()
                .collectionName("chewy_parse")
                .filter("id > 0 ")
                .build();
        QueryResp queryResp = client.query(queryReq);
        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
        for (QueryResp.QueryResult queryResult : queryResults) {
            List<JsonObject> list = new ArrayList<>();
            Map<String, Object> entity = queryResult.getEntity();
            System.out.println(entity.get("id"));
            QueryReq queryReq1 = QueryReq.builder()
                    .collectionName("chewy_parse_new")
                    .filter("id == " + entity.get("id"))
                    .build();
            QueryResp queryResp1 = client.query(queryReq1);
            List<QueryResp.QueryResult> queryResults1 = queryResp1.getQueryResults();
            if (CollectionUtil.isEmpty(queryResults1)){
                JSONObject jsonObject = JSONObject.parseObject((String) entity.get("text")).getJSONObject("analysis");
                Gson gson = new Gson();
                JsonObject updateData =gson.toJsonTree(entity).getAsJsonObject();
                updateData.addProperty("productName",jsonObject.getString("product_name"));
                if (null != jsonObject.getJSONObject("layer_1_positioning")){
                    updateData.addProperty("primaryFunction",jsonObject.getJSONObject("layer_1_positioning").getString("primary_function"));
                }
                if (null != jsonObject.getJSONObject("layer_2_quality")){
                    updateData.addProperty("healthScore",jsonObject.getJSONObject("layer_2_quality").getInteger("health_score"));
                    updateData.addProperty("redFlags",jsonObject.getJSONObject("layer_2_quality").getString("red_flags"));
                }
                updateData.addProperty("brand",getBrand(jsonObject.getString("product_name")));
                list.add(updateData);
                InsertReq insertReq = InsertReq.builder()
                        .collectionName("chewy_parse_new")
                        .data(list)
                        .build();
                InsertResp insertResp = client.insert(insertReq);
            }
        }
    }

    public void test2(){
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        QueryReq queryReq = QueryReq.builder()
                .collectionName("chewy_parse")
                .filter("id > 0 ")
                .build();
        QueryResp queryResp = client.query(queryReq);
        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
        int threadCount = Math.min(10, queryResults.size()); // 最多10个线程
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            List<CompletableFuture<Void>> futures = queryResults.stream()
                    .map(queryResult -> CompletableFuture.runAsync(() -> processQueryResult(queryResult, client), executor))
                    .collect(Collectors.toList());

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } catch (Exception e) {
            log.error("多线程处理失败", e);
        } finally {
            executor.shutdown();
        }
    }

    private void processQueryResult(QueryResp.QueryResult queryResult, MilvusClientV2 client) {
        try {
            Map<String, Object> entity = queryResult.getEntity();
            Object idObj = entity.get("id");
            System.out.println("处理" + idObj);
            if (idObj == null) return;

            // 1. 检查 chewy_parse_new 中是否已存在
            QueryReq queryReq1 = QueryReq.builder()
                    .collectionName("chewy_parse_new")
                    .filter("id == " + idObj)
                    .build();
            QueryResp queryResp1 = client.query(queryReq1);

            if (!CollectionUtil.isEmpty(queryResp1.getQueryResults())) {
                System.out.println("跳过" + idObj);
                return; // 已存在，跳过
            }

            // 2. 解析 text 字段
            String textStr = (String) entity.get("text");
            if (textStr == null) return;

            JSONObject jsonObject = JSONObject.parseObject(textStr).getJSONObject("analysis");
            if (jsonObject == null) return;

            // 3. 构建新数据
            Gson gson = new Gson();
            JsonObject updateData = gson.toJsonTree(entity).getAsJsonObject();

            updateData.addProperty("productName", jsonObject.getString("product_name"));

            JSONObject layer1 = jsonObject.getJSONObject("layer_1_positioning");
            if (layer1 != null) {
                updateData.addProperty("primaryFunction", layer1.getString("primary_function"));
            }

            JSONObject layer2 = jsonObject.getJSONObject("layer_2_quality");
            if (layer2 != null) {
                Integer healthScore = layer2.getInteger("health_score");
                if (healthScore != null) {
                    updateData.addProperty("healthScore", healthScore);
                }
                updateData.addProperty("redFlags", layer2.getString("red_flags"));
            }

            String productName = jsonObject.getString("product_name");
            updateData.addProperty("brand", getBrand(productName));

            // 4. 插入 chewy_parse_new
            InsertReq insertReq = InsertReq.builder()
                    .collectionName("chewy_parse_new")
                    .data(Collections.singletonList(updateData))
                    .build();
            client.insert(insertReq);

        } catch (Exception e) {
            // 记录错误但不中断其他线程
            System.err.println("处理失败, id: " + queryResult.getEntity().get("id") + ", error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getBrand(String text) {
        final int MAX_RETRIES = 3;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                StringBuilder parseResult = new StringBuilder();
                JSONObject param = new JSONObject();
                com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();
                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", "你是一个专业的电商数据清洗助手，任务是从完整的宠物食品商品标题中准确提取**品牌名称（Brand）**。\n" +
                        "\n" +
                        "【提取可能的规则】\n" +
                        "1. 品牌名可能位于标题最前面，由1~4个单词组成；\n" +
                        "2. 品牌名后通常紧跟产品系列、口味、品类等描述（如 \"Dog Treat\", \"Dry Food\", \"Chicken Recipe\"）；\n" +
                        "3. 忽略以下关键词及之后的内容：\n" +
                        "   - 品类词：Treat, Food, Snack, Cookie, Biscuit, Chew, Kibble, Meal, Diet, Formula 等；\n" +
                        "   - 描述词：Minty, Crunchy, Grain-Free, Natural, Organic, Adult, Puppy, Senior 等；\n" +
                        "   - 规格词：10lb, 25-count, Pack of 2 等；\n" +
                        "4. 如果无法确定品牌，不要乱说；\n" +
                        "5. 不要包含标点符号（如 'n, -, &），但保留品牌固有拼写（如 \"Lick'n\" 属于产品名，不属品牌）；\n" +
                        "6. 输出仅包含品牌名称，不要解释、不要引号、不要额外文本。\n" +
                        "【特别说明】\n" +
                        "- 商品来自 Chewy.com\n" +
                        "现在，请提取以下商品标题的品牌名称：");
                messages.add(system);
                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", "title:" + text);
                messages.add(user);
                param.put("messages", messages);
                param.put("model", "gemini-3-pro");
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
                    return parseResult.toString().replaceAll("(?si)<think>.*?</think>", "");
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

    public void parse(){
        List<AiChewyDetail> recordList = baseMapper.selectAll();
        List<AiChewyDetail> parseList = new ArrayList<>();
        List<String> idList = new ArrayList<>();
        for (AiChewyDetail record : recordList) {
            parseList.add(record);
            idList.add(record.getId().toString());
            String input = JSONObject.toJSONString(parseList);
            if (estimateTokens(input) > 30000){
                System.out.println("lastId:"+record.getId());
                //模型处理
                JSONArray resultArray = aiModelUtils.getChewyParse(input,idList);
                for (int i = 0; i < resultArray.size(); i++) {
                    JSONObject obj = resultArray.getJSONObject(i);
                    String embedding = JSONObject.toJSONString(obj);
                    this.processMilvus(embedding,"chewy_parse",String.join(",", idList)+",");
                }
                parseList = new ArrayList<>();
                idList = new ArrayList<>();
            }
        }
    }

    public Long processMilvus(String embedding, String collectionName,String input) {
        MilvusClientV2 client = null;
        Long id = UUIDConverter.generateSafeUUIDAsLong();
        try {
            ConnectConfig config = ConnectConfig.builder()
                    .uri(milvusUri)
                    .build();
            client = new MilvusClientV2(config);
            List<JsonObject> data = new ArrayList<>();
            JsonObject jsonObject = new JsonObject();
            Gson gson = new Gson();

            jsonObject.addProperty("id",id);
            jsonObject.addProperty("text", embedding);
            jsonObject.addProperty("input", input);
            jsonObject.add("vector", gson.toJsonTree(embeddingResourceManager.embedText(embedding)));
            data.add(jsonObject);
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(data)
                    .build();

            InsertResp insertResp = client.insert(insertReq);
        } catch (Exception e){
            System.out.println("写入数据库失败" + e.getMessage());
        }finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // 记录关闭异常，但不抛出
                    System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
                }
            }
        }
        return id;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chinese = 0;
        int nonChinese = 0;

        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chinese++;
            } else {
                nonChinese++;
            }
        }

        double tokens = chinese * 1.1 + nonChinese * 0.25;
        return (int) Math.ceil(tokens);
    }

    public void parseChewy(String url, String title, MultipartFile detailFile, MultipartFile ingredientInformationFile) {
        String detail = null;
        if (null != detailFile && !detailFile.isEmpty()) {
            try {
                detail = aiModelUtils.processPageWithQwen(detailFile);
            } catch (Exception e) {
                throw new BusinessException(500, "处理详情文件失败: " + e.getMessage());
            }
        }

        String ingredientInformation = null;
        if (null != ingredientInformationFile && !ingredientInformationFile.isEmpty()) {
            try {
                ingredientInformation = aiModelUtils.processPageWithQwen(ingredientInformationFile);
            } catch (Exception e) {
                throw new BusinessException(500, "处理成分信息文件失败: " + e.getMessage());
            }
        }
        AiChewyDetail record = new AiChewyDetail();
        record.setUrl(url);
        record.setTitle(title);
        record.setDetail(detail);
        record.setIngredientInformation(ingredientInformation);
        baseMapper.update(record,Wrappers.<AiChewyDetail>lambdaQuery().eq(AiChewyDetail::getTitle,title));
    }
}
