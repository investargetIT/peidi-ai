package com.cyanrocks.ai.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyanrocks.ai.dao.entity.*;
import com.cyanrocks.ai.dao.mapper.*;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.vo.GbiMilvus;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Author wjq
 * @Date 2025/3/19 16:01
 */
@Component
public class MilvusUtils {
    private static final int VECTOR_DIM = 1024; // all-MiniLM-L6-v2的维度
    private static final int MAX_TEXT_LENGTH = 65000;

    @Autowired
    private AiQueryHistoryMapper aiQueryHistoryMapper;
    @Autowired
    private AiEnumMapper aiEnumMapper;
    @Autowired
    private AiMilvusPdfMarkdownMapper aiMilvusPdfMarkdownMapper;
    @Autowired
    private AiModelUtils aiModelUtils;
    @Autowired
    private AiGbiTableMapper aiGbiTableMapper;
    @Autowired
    private AiGbiExplainMapper aiGbiExplainMapper;
    @Autowired
    private EmbeddingResourceManager embeddingResourceManager;

    @Value("${milvus.uri}")
    private String milvusUri;

    // 主处理流程
    public void processFileData(List<AiMilvusPdfMarkdown> inputList, String collectionName) throws Exception {
        // 2. 生成向量
        generatePdfReportVectors(inputList);
        MilvusClientV2 client = null;
        try {
            // 3. 连接Milvus

            ConnectConfig config = ConnectConfig.builder()
                    .uri(milvusUri)
                    .build();
            client = new MilvusClientV2(config);
            // 6. 创建集合
            createCollectionIfNotExists(client, collectionName);

            // 2. 检查集合是否存在
            Boolean hasCollection = client.hasCollection(
                    HasCollectionReq.builder()
                            .collectionName(collectionName)
                            .build());
            if (!hasCollection) {
                throw new BusinessException(500, "集合不存在: " + collectionName);
            }
            // 3. 加载集合
            Boolean loadState = client.getLoadState(
                    GetLoadStateReq.builder()
                            .collectionName(collectionName)
                            .build()
            );
            insertPdfDataInBatches(client, inputList, collectionName);
        } catch (Exception e){
            System.out.println("写入数据库失败" + e.getMessage());
            throw new BusinessException(500,"写入数据库失败");
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
    }

    public void processGbiTable(GbiMilvus gbiMilvus, String collectionName) {
        // 2. 生成向量
        MilvusClientV2 client = null;
        try {
            // 3. 连接Milvus

            ConnectConfig config = ConnectConfig.builder()
                    .uri(milvusUri)
                    .build();
            client = new MilvusClientV2(config);
            // 6. 创建集合
            createCollectionIfNotExists(client, collectionName);

            // 2. 检查集合是否存在
            Boolean hasCollection = client.hasCollection(
                    HasCollectionReq.builder()
                            .collectionName(collectionName)
                            .build());
            if (!hasCollection) {
                throw new BusinessException(500, "集合不存在: " + collectionName);
            }

            // 3. 加载集合
            Boolean loadState = client.getLoadState(
                    GetLoadStateReq.builder()
                            .collectionName(collectionName)
                            .build()
            );

            if (loadState) {
                List<JsonObject> data = new ArrayList<>();
                JsonObject jsonObject = new JsonObject();
                Long id = UUIDConverter.generateSafeUUIDAsLong();
                jsonObject.addProperty("id",id);
                jsonObject.addProperty("field", gbiMilvus.getField());
                jsonObject.addProperty("tableName", gbiMilvus.getTableName());
                Gson gson = new Gson();
                jsonObject.add("vector", gson.toJsonTree(embeddingResourceManager.embedText(gbiMilvus.getField())));
                jsonObject.addProperty("searchSql", gbiMilvus.getSearchSql());
                data.add(jsonObject);
                InsertReq insertReq = InsertReq.builder()
                        .collectionName(collectionName)
                        .data(data)
                        .build();
                InsertResp insertResp = client.insert(insertReq);
                if (insertResp.getInsertCnt() > 0){
                    AiGbiTable gbiTable = new AiGbiTable();
                    gbiTable.setMilvusId(id.toString());
                    gbiTable.setTableName(gbiMilvus.getTableName());
                    gbiTable.setField(gbiMilvus.getField());
                    gbiTable.setSearchSql(gbiMilvus.getSearchSql());
                    gbiTable.setMetedate(gbiMilvus.getMetedate());
                    gbiTable.setCreateAt(LocalDateTime.now());
                    aiGbiTableMapper.insert(gbiTable);
                }

            }
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // 记录关闭异常，但不抛出
                    System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
                }
            }
        }
    }

    public void processGbiExplain(GbiMilvus gbiMilvus, String collectionName) {
        // 2. 生成向量
        MilvusClientV2 client = null;
        try {
            // 3. 连接Milvus

            ConnectConfig config = ConnectConfig.builder()
                    .uri(milvusUri)
                    .build();
            client = new MilvusClientV2(config);
            // 6. 创建集合
            createCollectionIfNotExists(client, collectionName);

            // 2. 检查集合是否存在
            Boolean hasCollection = client.hasCollection(
                    HasCollectionReq.builder()
                            .collectionName(collectionName)
                            .build());
            if (!hasCollection) {
                throw new BusinessException(500, "集合不存在: " + collectionName);
            }

            // 3. 加载集合
            Boolean loadState = client.getLoadState(
                    GetLoadStateReq.builder()
                            .collectionName(collectionName)
                            .build()
            );

            if (loadState) {
                List<JsonObject> data = new ArrayList<>();
                JsonObject jsonObject = new JsonObject();
                Long id = UUIDConverter.generateSafeUUIDAsLong();
                jsonObject.addProperty("id",id);
                jsonObject.addProperty("gbiExplain", gbiMilvus.getGbiExplain());
                jsonObject.addProperty("explainType", gbiMilvus.getExplainType());
                Gson gson = new Gson();
                jsonObject.add("vector", gson.toJsonTree(embeddingResourceManager.embedText(gbiMilvus.getGbiExplain())));
                data.add(jsonObject);
                InsertReq insertReq = InsertReq.builder()
                        .collectionName(collectionName)
                        .data(data)
                        .build();
                InsertResp insertResp = client.insert(insertReq);
                if (insertResp.getInsertCnt() > 0){
                    AiGbiExplain gbiExplain = new AiGbiExplain();
                    gbiExplain.setMilvusId(id.toString());
                    gbiExplain.setGbiExplain(gbiMilvus.getGbiExplain());
                    gbiExplain.setExplainType(gbiMilvus.getExplainType());
                    gbiExplain.setCreateAt(LocalDateTime.now());
                    aiGbiExplainMapper.insert(gbiExplain);
                }
            }
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // 记录关闭异常，但不抛出
                    System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
                }
            }
        }
    }

    public void test(){
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        QueryReq queryReq = QueryReq.builder()
                .collectionName("pdf_markdown")
                .filter("id > 0 ")
                .build();
        // 执行标量查询
        QueryResp queryResp = client.query(queryReq);
        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
        List<JsonObject> list = new ArrayList<>();
        for (QueryResp.QueryResult queryResult : queryResults) {
            Map<String, Object> entity = queryResult.getEntity();
            Gson gson = new Gson();
            JsonObject updateData =gson.toJsonTree(entity).getAsJsonObject();
            list.add(updateData);
        }
        createCollectionIfNotExists(client, "pdf_markdown_new");
//
//        // 3. 加载集合
//        Boolean loadState = client.getLoadState(
//                GetLoadStateReq.builder()
//                        .collectionName("pdf_markdown_new")
//                        .build()
//        );
        InsertReq insertReq = InsertReq.builder()
                .collectionName("pdf_markdown_new")
                .data(list)
                .build();
        InsertResp insertResp = client.insert(insertReq);
    }

    public boolean updateMilvusPdfMarkdownById(AiMilvusPdfMarkdown req, String collection){
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = null;
        try {
            client = new MilvusClientV2(config);
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collection)
                    .filter("id == " + Long.valueOf(req.getMilvusId()))
                    .build();
            // 执行标量查询
            QueryResp queryResp = client.query(queryReq);
            List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
            List<JsonObject> list = new ArrayList<>();
            for (QueryResp.QueryResult queryResult : queryResults) {
                Map<String, Object> entity = queryResult.getEntity();
                Gson gson = new Gson();
                JsonObject updateData = gson.toJsonTree(entity).getAsJsonObject();
                if (null != req.getTitle()) {
                    updateData.addProperty("title", req.getTitle());
                }
                if (null != req.getReportType()) {
                    updateData.addProperty("reportType", req.getReportType());
                }
                if (null != req.getExpireDate()) {
                    updateData.addProperty("expireDate", String.valueOf(req.getExpireDate()));
                }
                if (null != req.getReportDate()) {
                    updateData.addProperty("reportDate", String.valueOf(req.getReportDate()));
                }
                if (null != req.getSource()) {
                    updateData.addProperty("source", req.getSource());
                }
                if (null != req.getProductName()) {
                    updateData.addProperty("productName", req.getProductName());
                }
                if (null != req.getReportId()) {
                    updateData.addProperty("reportId", req.getReportId());
                }
                if (null != req.getSourceSystem()) {
                    updateData.addProperty("sourceSystem", req.getSourceSystem());
                }
                if (null != req.getLang()) {
                    updateData.addProperty("lang", req.getLang());
                }
                if (null != req.getIngestDate()) {
                    updateData.addProperty("ingestDate", String.valueOf(req.getIngestDate()));
                }
                if (null != req.getVersion()) {
                    updateData.addProperty("version", req.getVersion());
                }
                if (null != req.getChecksum()) {
                    updateData.addProperty("checksum", req.getChecksum());
                }
                if (null != req.getVisibility()) {
                    updateData.addProperty("visibility", req.getVisibility());
                }
                if (null != req.getTags()) {
                    updateData.addProperty("tags", req.getTags());
                }
                if (null != req.getStandardRefs()) {
                    updateData.addProperty("standardRefs", req.getStandardRefs());
                }
                if (null != req.getDocStatus()) {
                    updateData.addProperty("docStatus", req.getDocStatus());
                }
                if (null != req.getBrand()) {
                    updateData.addProperty("brand", req.getBrand());
                }
                if (null != req.getSku()) {
                    updateData.addProperty("sku", req.getSku());
                }
                if (null != req.getSpec()) {
                    updateData.addProperty("spec", req.getSpec());
                }
                if (null != req.getBatchNo()) {
                    updateData.addProperty("batchNo", req.getBatchNo());
                }
                if (null != req.getMetedate()) {
                    updateData.addProperty("metedate", req.getMetedate());
                }
                list.add(updateData);
            }
            UpsertReq updateReq = UpsertReq.builder()
                    .collectionName(collection)
                    .data(list)
                    .build();
            UpsertResp upsertResp = client.upsert(updateReq);
            return upsertResp.getUpsertCnt() > 0;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
                }
            }
        }
    }

    public boolean updateGbiTableById(AiGbiTable req, String collection){
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = null;
        try {
            client = new MilvusClientV2(config);
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collection)
                    .filter("id == "+Long.valueOf(req.getMilvusId()))
                    .build();
            // 执行标量查询
            QueryResp queryResp = client.query(queryReq);
            List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
            List<JsonObject> list = new ArrayList<>();
            for (QueryResp.QueryResult queryResult : queryResults) {
                Map<String, Object> entity = queryResult.getEntity();
                Gson gson = new Gson();
                JsonObject updateData =gson.toJsonTree(entity).getAsJsonObject();
                if (null != req.getTableName()) {
                    updateData.addProperty("tableName", req.getTableName());
                }
                if (null != req.getField()) {
                    updateData.addProperty("field", req.getField());
                    updateData.add("vector", gson.toJsonTree(embeddingResourceManager.embedText(req.getField())));
                }

                if (null != req.getSearchSql()) {
                    updateData.addProperty("searchSql", req.getSearchSql());
                }
                if (null != req.getMetedate()) {
                    updateData.addProperty("metedate", req.getMetedate());
                }
                list.add(updateData);
            }
            UpsertReq updateReq = UpsertReq.builder()
                    .collectionName(collection)
                    .data(list)
                    .build();
            UpsertResp upsertResp = client.upsert(updateReq);

            return upsertResp.getUpsertCnt() >0;
        }finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
                }
            }
        }
    }

    public boolean updateGbiExpainById(AiGbiExplain req, String collection){
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = null;
        try {
            client = new MilvusClientV2(config);
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collection)
                    .filter("id == "+Long.valueOf(req.getMilvusId()))
                    .build();
            // 执行标量查询
            QueryResp queryResp = client.query(queryReq);
            List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
            List<JsonObject> list = new ArrayList<>();
            for (QueryResp.QueryResult queryResult : queryResults) {
                Map<String, Object> entity = queryResult.getEntity();
                Gson gson = new Gson();
                JsonObject updateData =gson.toJsonTree(entity).getAsJsonObject();
                if (null != req.getGbiExplain()) {
                    updateData.addProperty("gbiExplain", req.getGbiExplain());
                    updateData.add("vector", gson.toJsonTree(embeddingResourceManager.embedText(req.getGbiExplain())));
                }
                if (null != req.getExplainType()) {
                    updateData.addProperty("explainType", req.getExplainType());
                }

                list.add(updateData);
            }
            UpsertReq updateReq = UpsertReq.builder()
                    .collectionName(collection)
                    .data(list)
                    .build();
            UpsertResp upsertResp = client.upsert(updateReq);
            return upsertResp.getUpsertCnt() >0;
        }finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
                }
            }
        }
    }

    public boolean deleteMilvusById(String milvusId, String collection){
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = null;
        try {
            client = new MilvusClientV2(config);
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(collection)
                    .ids(Collections.singletonList(Long.valueOf(milvusId)))
                    .build();
            DeleteResp deleteResp = client.delete(deleteReq);
            return deleteResp.getDeleteCnt() >0;
        }finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
                }
            }
        }
    }

    //纯向量查询
    public Map<String, String> semanticSearch2(String que, String collectionName, String dingId) throws Exception {
        String question = que.trim().replace("\r","").replace("\n","");
        String rewriteQuestion = "";
        //纯向量
        Map<String, String> result = new HashMap<>();
        boolean hasReturn = false;
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = null;
        try {
            client = new MilvusClientV2(config);
            //如果问题包含”检测报告“和"到期日",则提取到期日并且进行标量查询
            if (question.contains("检测报告") && question.contains("到期")) {
                String reportDate = aiModelUtils.callWithGetDate(question);
                String regex = "\\b\\d{4}-\\d{2}-\\d{2}\\b";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(reportDate);
                // 查找所有匹配项
                while (matcher.find()) {
                    QueryReq queryReq = QueryReq.builder()
                            .collectionName(collectionName)
                            .filter("reportDate < '" + matcher.group() + "' and reportType == '检测报告'")
                            .outputFields(Arrays.asList("title", "source"))
                            .build();
                    // 执行标量查询
                    QueryResp queryResp = client.query(queryReq);
                    //模糊查询匹配标题成功，则直接返回
                    if (null != queryResp && CollectionUtil.isNotEmpty(queryResp.getQueryResults())) {
                        Set<String> sources = new HashSet<>();
                        Set<String> titles = new HashSet<>();
                        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
                        if (CollectionUtil.isNotEmpty(queryResults)) {
                            for (QueryResp.QueryResult queryResult : queryResults) {
                                Map<String, Object> entity = queryResult.getEntity();
                                sources.add((String) entity.get("source"));
                                titles.add((String) entity.get("title"));
                            }
                            result.put("title", String.join(",", titles));
                            result.put("source", String.join(",", sources));
                            hasReturn = true;
                        }
                    }
                }
            }

            if (!hasReturn) {
                List<String> fields = aiEnumMapper.selectList(Wrappers.<AiEnum>lambdaQuery()
                        .eq(AiEnum::getType, "scalarField")).stream().map(AiEnum::getValue).collect(Collectors.toList());
                String query = fields.stream()
                        .map(field -> field + " == \"" + question.replace("\"", "\\\"").replace("%", "\\%") + "\"")
                        .reduce((a, b) -> a + " or " + b)
                        .orElse("");
                QueryReq queryReq = QueryReq.builder()
                        .collectionName(collectionName)
                        .filter(query)
                        .outputFields(Arrays.asList("title", "source", "visibility"))
                        .build();
                // 执行标量查询
                QueryResp queryResp = client.query(queryReq);
                //模糊查询匹配标题成功，则直接返回
                if (null != queryResp && CollectionUtil.isNotEmpty(queryResp.getQueryResults())) {
                    Set<String> sources = new HashSet<>();
                    Set<String> titles = new HashSet<>();
                    List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
                    if (CollectionUtil.isNotEmpty(queryResults)) {
                        for (QueryResp.QueryResult queryResult : queryResults) {
                            if (null != queryResult.getEntity().get("visibility")
                                    && ("all".equals(queryResult.getEntity().get("visibility")) || queryResult.getEntity().get("visibility").toString().contains(dingId))) {
                                Map<String, Object> entity = queryResult.getEntity();
                                sources.add((String) entity.get("source"));
                                titles.add((String) entity.get("title"));
                            }
                        }
                        if (titles.isEmpty()){
                            Set<String> newTitles = new HashSet<>();
                            queryResults.forEach(queryResult->{
                                String title = "无权限查看" + queryResult.getEntity().get("title");
                                newTitles.add(title);
                            });
                            result.put("title", String.join(",", newTitles));
                        }else {
                            result.put("title", String.join(",", titles));
                            result.put("source", String.join(",", sources));
                        }

                        hasReturn = true;
                    }
                }
            }

            if (!hasReturn) {
                //  向量化问题,加上新问题
                List<AiQueryHistory> historyList = aiQueryHistoryMapper.selectPage(new Page<>(1, 1),Wrappers.<AiQueryHistory>lambdaQuery()
                        .eq(AiQueryHistory::getUserId, dingId).eq(AiQueryHistory::getSource,"问问")
                        .orderByDesc(AiQueryHistory::getCreateAt)).getRecords();

                //根据最近一条问题，重写问题
                if (CollectionUtil.isEmpty(historyList)){
                    rewriteQuestion = question;
                }else if (null == historyList.get(0).getRewriteQuery()){
                    if (null == historyList.get(0).getQuery()){
                        //过滤，开启新问题
                        rewriteQuestion = question;
                    }else {
                        rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getQuery());
                    }
                }else {
                    rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getRewriteQuery());
                }
                System.out.println("查询问题:" + rewriteQuestion);
                AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                        .eq(AiEnum::getType, "topK"));
                Map<String, Object> searchParams = new HashMap<>();
                searchParams.put("nprobe", 10);
                SearchResp searchResp = client.search(SearchReq.builder()
                        .collectionName(collectionName)
                        .filter("visibility == \"all\" or visibility like \"%" + dingId + "%\"")
                        .data(Collections.singletonList(new FloatVec(embeddingResourceManager.embedText(rewriteQuestion))))
                        .annsField("vector")
                        .searchParams(searchParams)
                        .topK(Integer.parseInt(topK.getValue()))
                        .outputFields(Arrays.asList("title", "source", "text"))
                        .build());

                if (null != searchResp && CollectionUtil.isNotEmpty(searchResp.getSearchResults())) {
                    List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
                    List<SearchResp.SearchResult> scores = searchResults.get(0);
                    // 处理最终结果
                    List<String> sources = new ArrayList<>();
                    List<String> titles = new ArrayList<>();
                    Map<String, StringBuilder> title2text = new HashMap<>();
                    for (SearchResp.SearchResult score : scores) {
                        Map<String, Object> entity = score.getEntity();
                        String source = (String) entity.get("source");
                        if (!sources.contains(source)) {
                            sources.add(source);
                        }
                        String title = (String) entity.get("title");
                        if (!titles.contains(title)) {
                            titles.add(title);
                        }
                        StringBuilder text = new StringBuilder((String) entity.get("text"));
                        if (null == title2text.get(title)) {
                            title2text.put(title, text);
                        } else {
                            title2text.put(title, title2text.get(title).append(text));
                        }
                    }
                    //根据title获取对应文件的chunk数量
                    AiEnum fileChunk = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                            .eq(AiEnum::getType, "fileChunk"));
                    for (String title : titles) {
                        QueryReq queryReq = QueryReq.builder()
                                .collectionName(collectionName)
                                .filter("title == '" + title.replace("\"", "\\\"").replace("%", "\\%") + "'")
                                .outputFields(Arrays.asList("title", "text"))
                                .build();
                        QueryResp queryResp = client.query(queryReq);
                        if (null != queryResp && CollectionUtil.isNotEmpty(queryResp.getQueryResults())) {
                            List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
                            if (queryResults.size() <= Integer.parseInt(fileChunk.getValue())) {
                                //若chunk数量小于等于fileChunk数量，整个文档都拼接上
                                System.out.println("拼接所有文档:" + title);
                                title2text.remove(title);
                                StringBuilder text = new StringBuilder();
                                for (QueryResp.QueryResult queryResult : queryResults) {
                                    text.append(queryResult.getEntity().get("text"));
                                }
                                title2text.put(title, text);
                            }
                        }
                    }
                    //将所有的title2text拼接到一起
                    StringBuilder resultText = new StringBuilder();
                    for (int i = 0; i<titles.size(); i++){
                        resultText.append(titles.get(i)).append("：").append("\n").append(title2text.get(titles.get(i))).append("\n");
                    }
                    String modelText = "";
                    try {
                        modelText = aiModelUtils.callWithMessage(rewriteQuestion, resultText.toString(), new ArrayList<>());
                    } catch (SocketTimeoutException e) {
                        result.put("text", "连接超时，请稍后再试");
                    }
                    if (!"".equals(modelText)){
                        if (modelText.contains("#参考资料#")){
                            result.put("text", modelText.split("#参考资料#")[0]);
                            List<Integer> ck = Arrays.stream(modelText.split("#参考资料#")[1].replaceAll("[\\[\\]]", "")
                                    .split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).collect(Collectors.toList());
                            if (CollectionUtil.isNotEmpty(ck)){
                                result.put("title", ck.stream().map(i -> titles.get(i - 1)).collect(Collectors.joining(",")));
                                result.put("source", ck.stream().map(i -> sources.get(i - 1)).collect(Collectors.joining(",")));
                            }
                        }else {
                            result.put("title", String.join(",", titles));
                            result.put("source", String.join(",", sources));
                            result.put("text", modelText);
                        }
                    }
                } else {
                    result.put("text", "查询失败，请联系系统管理员");
                    System.err.println("milvus查询失败");
                }
            }
            //保存记录，用于上下文对话
            AiQueryHistory aiQueryHistory = new AiQueryHistory();
            aiQueryHistory.setUserId(dingId);
            aiQueryHistory.setQuery(question);
            aiQueryHistory.setRewriteQuery(rewriteQuestion);
            aiQueryHistory.setResult(result.get("text") == null ? result.get("title") : result.get("text"));
            aiQueryHistory.setCreateAt(LocalDateTime.now());
            aiQueryHistory.setSource("问问");
            aiQueryHistoryMapper.insert(aiQueryHistory);
            try {
                client.close();
            } catch (Exception e) {
                // 记录关闭异常，但不抛出
                System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
            }
            return result;
        }finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
                }
            }
        }
    }

    //纯向量
    public Map<String, String> gbiSearch(String question, String collectionName, String explainCollectionName, String dingId) {
        String rewriteQuestion = "";
        Map<String, String> result = new HashMap<>();
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = null;
        try {
            client = new MilvusClientV2(config);
            //  向量化问题,加上新问题
            List<AiQueryHistory> historyList = aiQueryHistoryMapper.selectPage(new Page<>(1, 1), Wrappers.<AiQueryHistory>lambdaQuery()
                    .eq(AiQueryHistory::getUserId, dingId).eq(AiQueryHistory::getSource, "问数")
                    .orderByDesc(AiQueryHistory::getCreateAt)).getRecords();

        //根据最近一条问题，重写问题
        if (CollectionUtil.isEmpty(historyList)){
            rewriteQuestion = question;
        }else if (null == historyList.get(0).getRewriteQuery()){
            if (null == historyList.get(0).getQuery()){
                //过滤，开启新问题
                rewriteQuestion = question;
            }else {
                rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getQuery());
            }
        }else {
            rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getRewriteQuery());
        }
        System.out.println("问题重写:" + rewriteQuestion);
        List<Map<String, String>> query2sqlList = new ArrayList<>();
        //查询基础sql
        AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                    .eq(AiEnum::getType, "gbiTopK"));
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("nprobe", 10);
        SearchResp searchResp = client.search(SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(new FloatVec(embeddingResourceManager.embedText(rewriteQuestion))))
                .annsField("vector")
                .searchParams(searchParams)
                .topK(Integer.parseInt(topK.getValue()))
                .outputFields(Arrays.asList("field", "searchSql", "metedate"))
                .build());

        if (null != searchResp && CollectionUtil.isNotEmpty(searchResp.getSearchResults())) {
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
            List<SearchResp.SearchResult> scores = searchResults.get(0);
            // 处理最终结果
            for (SearchResp.SearchResult score : scores) {
                Map<String, Object> entity = score.getEntity();
                String field = (String) entity.get("field");
                String sql = (String) entity.get("searchSql");
                String metedate = (String) entity.get("metedate");
                JSONObject meteObject = JSONObject.parseObject(metedate);
                Map<String, String> query2sql = new HashMap<>();
                query2sql.put("字段包含：",field);
                query2sql.put("表基础字段sql：",sql);
                if (StringUtils.isNotEmpty(metedate)){
                    query2sql.put("参考数据",meteObject.getString("referenceData"));
                }
                query2sqlList.add(query2sql);
            }
        }
        //查询业务逻辑解释
        AiEnum explainTopK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                .eq(AiEnum::getType, "gbiExplainTopK"));
        Map<String, Object> explainSearchParams = new HashMap<>();
        explainSearchParams.put("nprobe", 10);
        SearchResp explainSearchResp = client.search(SearchReq.builder()
                .collectionName(explainCollectionName)
                .data(Collections.singletonList(new FloatVec(embeddingResourceManager.embedText(rewriteQuestion))))
                .filter("explainType == true")
                .annsField("vector")
                .searchParams(explainSearchParams)
                .topK(Integer.parseInt(explainTopK.getValue()))
                .outputFields(Collections.singletonList("gbiExplain"))
                .build());
        if (null != explainSearchResp && CollectionUtil.isNotEmpty(explainSearchResp.getSearchResults())) {
            List<List<SearchResp.SearchResult>> searchResults = explainSearchResp.getSearchResults();
            List<SearchResp.SearchResult> scores = searchResults.get(0);
            // 处理最终结果
            for (SearchResp.SearchResult score : scores) {
                Map<String, Object> entity = score.getEntity();
                String gbiExplain = (String) entity.get("gbiExplain");
                Map<String, String> query2sql = new HashMap<>();
                query2sql.put("业务逻辑解释：",gbiExplain);
                System.out.println("业务逻辑解释：" + gbiExplain);
                query2sqlList.add(query2sql);
            }
        }
        if (CollectionUtil.isEmpty(query2sqlList)){
            result.put("result","数据库查询失败");
        }else {
            //将全局业务逻辑解释加入
            List<AiGbiExplain> aiGbiExplainList = aiGbiExplainMapper.selectList(Wrappers.<AiGbiExplain>lambdaQuery()
                    .eq(AiGbiExplain::getExplainType, false));
            if (CollectionUtil.isNotEmpty(aiGbiExplainList)){
                aiGbiExplainList.forEach(explain->{
                    Map<String, String> query2sql = new HashMap<>();
                    query2sql.put("业务逻辑解释：",explain.getGbiExplain());
                    query2sqlList.add(query2sql);
                });
            }
            //调用模型处理返回结果
            // 使用正则表达式匹配 ```sql 和 ``` 之间的内容
            String sql = aiModelUtils.callWithGbiQa(rewriteQuestion, query2sqlList.toString()).trim();
            sql = aiModelUtils.gbiSqlReview(rewriteQuestion, query2sqlList.toString(),sql);
            sql = this.trimSql(sql);
            result.put("rewriteQuery",rewriteQuestion);
            try {
                if (sql.trim().toUpperCase().startsWith("SELECT")) {
                    result.put("sql",sql);
                    List<JSONObject> sqlResult = aiEnumMapper.doSql(sql);
                    if (null != sqlResult){
                        result.put("result",aiModelUtils.callWithAnalysisJson(rewriteQuestion, sqlResult.toString()));
                    }else {
                        result.put("result","暂无结果");
                    }
                }else {
                    result.put("result","暂无结果");
                }
            }catch (Exception e){
                sql = aiModelUtils.gbiSqlRepair(rewriteQuestion, query2sqlList.toString(),sql,e.getMessage());
                sql = this.trimSql(sql);
                try {
                    if (sql.trim().toUpperCase().startsWith("SELECT")) {
                        result.put("sql",sql);
                        List<JSONObject> sqlResult = aiEnumMapper.doSql(sql);
                        if (null != sqlResult){
                            result.put("result",aiModelUtils.callWithAnalysisJson(rewriteQuestion, sqlResult.toString()));
                        }else {
                            result.put("result","暂无结果");
                        }
                    }else {
                        result.put("result","暂无结果");
                    }
                }catch (Exception e2){
                    result.put("sql",sql);
                    result.put("result","暂无结果");
                }
            }
            try {
                client.close();
            } catch (Exception e) {
                // 记录关闭异常，但不抛出
                System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
            }
        }

            //保存记录，用于上下文对话
            AiQueryHistory aiQueryHistory = new AiQueryHistory();
            aiQueryHistory.setUserId(dingId);
            aiQueryHistory.setIdType("dingId");
            aiQueryHistory.setQuery(question);
            aiQueryHistory.setRewriteQuery(rewriteQuestion);
            aiQueryHistory.setResult(result.get("result"));
            aiQueryHistory.setCreateAt(LocalDateTime.now());
            aiQueryHistory.setSource("问数");
            aiQueryHistoryMapper.insert(aiQueryHistory);
            return result;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    System.out.println("关闭 Milvus 客户端时发生错误");
                }
            }
        }

    }

    private String trimSql(String sql){
        Pattern pattern = Pattern.compile("```sql\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            sql =  matcher.group(1).trim().replace("```sql", "").replace("```", "").trim();
        }
        // 清理多余的换行符
        return sql.replaceAll("^\\n+|\\n+$", "");
    }


    /**
     * 计算两个向量列表的余弦相似度
     */
    private double cosineSimilarity(List<Float> vectorA, List<Float> vectorB) {
        if (vectorA == null || vectorB == null) {
            throw new IllegalArgumentException("输入向量不能为null");
        }

        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("向量维度必须相同: " +
                    vectorA.size() + " != " + vectorB.size());
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            float a = vectorA.get(i);
            float b = vectorB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public void rerank() {
        //            // 1. 准备Rerank所需的文档列表
//            List<String> documents = scores.stream()
//                    .map(score -> (String) score.get("text"))
//                    .collect(Collectors.toList());

//            // 2. 调用Rerank模型
//            List<Double> rerankScores;
//            try {
//                rerankScores = rerankClient.rerank(question, documents);
//            } catch (Exception e) {
//                System.err.println("Rerank调用失败，降级使用原始结果: " + e.getMessage());
//                rerankScores = scores.stream()
//                        .map(score -> (double) score.getScore())
//                        .collect(Collectors.toList());
//            }
//
//            // 3. 合并原始结果与Rerank分数，并按Rerank分数排序
//            List<Map.Entry<SearchResultsWrapper.IDScore, Double>> combined = new ArrayList<>();
//            for (int i = 0; i < scores.size(); i++) {
//                combined.add(new AbstractMap.SimpleEntry<>(scores.get(i), rerankScores.get(i)));
//            }
//
//            // 按Rerank分数降序排序
//            combined.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
//
//            // 4. 取Top1结果
//            int topK = 5;
//            List<SearchResultsWrapper.IDScore> topResults = combined.stream()
//                    .limit(topK)
//                    .map(Map.Entry::getKey)
//                    .collect(Collectors.toList());


//            if ("bi_jiuqian_reports".equals(collectionName)){
//                for (SearchResultsWrapper.IDScore score : scores) {
//                    result.put("title", (String) score.get("title"));
//                    result.put("contentType", (Boolean) score.get("content_type") ? "content" : "description");
//                    result.put("text", (String) score.get("text"));
//                }
//            }else
    }

    private void generatePdfReportVectors(List<AiMilvusPdfMarkdown> records) {
        for (AiMilvusPdfMarkdown record : records) {
            try {
                record.setVector(embeddingResourceManager.embedText(record.getText()));
            } catch (Exception e) {
                System.out.println("Vector生成失败: "+e);
                System.out.println("文本长度为: "+estimateTokens(record.getText()));
                throw new BusinessException(500,"Vector生成失败，" + record.getId());
            }
        }
    }

    private int estimateTokens(String text) {
        // 使用更精确的估算方法
        if (text == null) return 0;

        double tokenCount = 0;
        for (char c : text.toCharArray()) {
            if (isChineseCharacter(c)) {
                tokenCount += 0.67; // 中文约1.5字符=1token
            } else {
                tokenCount += 0.25; // 英文约4字符=1token
            }
        }

        return (int) Math.ceil(tokenCount);
    }

    private boolean isChineseCharacter(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    private void createCollectionIfNotExists(MilvusClientV2 client, String collectionName) {
        Boolean response = client.hasCollection(
                HasCollectionReq.builder()
                        .collectionName(collectionName)
                        .build());

        if (!response) {
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .build();
            List<IndexParam> indexParams = new ArrayList<>();

            if ("pdf_markdown".equals(collectionName)){
                schema.addField(AddFieldReq.builder()
                        .fieldName("id")
                        .dataType(io.milvus.v2.common.DataType.Int64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("title").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).description("文档标题")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("text").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(MAX_TEXT_LENGTH).enableAnalyzer(true).description("文档原文")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("reportType").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).description("文档类型")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("reportDate").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("报告日期")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("expireDate").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("到期日")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("vector").dataType(io.milvus.v2.common.DataType.FloatVector).dimension(VECTOR_DIM).description("文档稠密向量")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("sparse").dataType(io.milvus.v2.common.DataType.SparseFloatVector).description("文档稀疏向量")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("source").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).description("文档路径")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("productName").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("产品名/系列名")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("reportId").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("报告编号/唯一文件号")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("sourceSystem").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("来源系统")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("lang").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("zh-CN/en")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("ingestDate").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("入库时间")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("version").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("文档版本号")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("checksum").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("文件哈希(完整性校验)")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("visibility").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).isNullable(true).description("访问控制(部门/角色/用户)")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("tags").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("自定义标签")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("standardRefs").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("标准/法规编号")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("docStatus").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("valid/obsolete/draft")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("brand").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("品牌")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("sku").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("SKU/料号")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("spec").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("规格/净含量")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("batchNo").dataType(io.milvus.v2.common.DataType.Int32).description("批次/LOT")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("metedate").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).isNullable(true).description("扩展元素")
                        .build());
                schema.addFunction(CreateCollectionReq.Function.builder()
                        .functionType(io.milvus.common.clientenum.FunctionType.BM25)
                        .name("text_bm25_emb")
                        .inputFieldNames(Collections.singletonList("text"))
                        .outputFieldNames(Collections.singletonList("sparse"))
                        .build());
                IndexParam indexParamForTextDense = IndexParam.builder()
                        .fieldName("vector")
                        .indexName("vector_index")
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.IP)
                        .build();

                Map<String, Object> sparseParams = new HashMap<>();
                sparseParams.put("inverted_index_algo", "DAAT_MAXSCORE");
                IndexParam indexParamForTextSparse = IndexParam.builder()
                        .fieldName("sparse")
                        .indexName("sparse_index")
                        .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                        .metricType(IndexParam.MetricType.BM25)
                        .extraParams(sparseParams)
                        .build();

                indexParams.add(indexParamForTextDense);
                indexParams.add(indexParamForTextSparse);
            } else if ("gbi_table".equals(collectionName)) {
                schema.addField(AddFieldReq.builder()
                        .fieldName("id")
                        .dataType(io.milvus.v2.common.DataType.Int64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("tableName").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).description("表名")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("field").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(MAX_TEXT_LENGTH).enableAnalyzer(true).description("字段逻辑")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("searchSql").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).description("查询sql")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("vector").dataType(io.milvus.v2.common.DataType.FloatVector).dimension(VECTOR_DIM).description("问题稠密向量")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("sparse").dataType(io.milvus.v2.common.DataType.SparseFloatVector).description("问题稀疏向量")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("metedate").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).isNullable(true).description("扩展元素")
                        .build());

                schema.addFunction(CreateCollectionReq.Function.builder()
                        .functionType(io.milvus.common.clientenum.FunctionType.BM25)
                        .name("field_bm25_emb")
                        .inputFieldNames(Collections.singletonList("field"))
                        .outputFieldNames(Collections.singletonList("sparse"))
                        .build());
                IndexParam indexParamForTextDense = IndexParam.builder()
                        .fieldName("vector")
                        .indexName("vector_index")
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.IP)
                        .build();

                Map<String, Object> sparseParams = new HashMap<>();
                sparseParams.put("inverted_index_algo", "DAAT_MAXSCORE");
                IndexParam indexParamForTextSparse = IndexParam.builder()
                        .fieldName("sparse")
                        .indexName("sparse_index")
                        .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                        .metricType(IndexParam.MetricType.BM25)
                        .extraParams(sparseParams)
                        .build();

                indexParams.add(indexParamForTextDense);
                indexParams.add(indexParamForTextSparse);
            }else if ("gbi_explain".equals(collectionName)) {
                schema.addField(AddFieldReq.builder()
                        .fieldName("id")
                        .dataType(io.milvus.v2.common.DataType.Int64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("gbiExplain").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(MAX_TEXT_LENGTH).enableAnalyzer(true).description("业务逻辑解释")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("explainType").dataType(DataType.Bool).description("业务逻辑解释类型")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("vector").dataType(io.milvus.v2.common.DataType.FloatVector).dimension(VECTOR_DIM).description("问题稠密向量")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("sparse").dataType(io.milvus.v2.common.DataType.SparseFloatVector).description("问题稀疏向量")
                        .build());
                schema.addFunction(CreateCollectionReq.Function.builder()
                        .functionType(io.milvus.common.clientenum.FunctionType.BM25)
                        .name("explain_bm25_emb")
                        .inputFieldNames(Collections.singletonList("gbiExplain"))
                        .outputFieldNames(Collections.singletonList("sparse"))
                        .build());
                IndexParam indexParamForTextDense = IndexParam.builder()
                        .fieldName("vector")
                        .indexName("vector_index")
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.IP)
                        .build();

                Map<String, Object> sparseParams = new HashMap<>();
                sparseParams.put("inverted_index_algo", "DAAT_MAXSCORE");
                IndexParam indexParamForTextSparse = IndexParam.builder()
                        .fieldName("sparse")
                        .indexName("sparse_index")
                        .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                        .metricType(IndexParam.MetricType.BM25)
                        .extraParams(sparseParams)
                        .build();

                indexParams.add(indexParamForTextDense);
                indexParams.add(indexParamForTextSparse);
            }


            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            client.createCollection(createCollectionReq);
        }
    }

    private void insertPdfDataInBatches(MilvusClientV2 client,
                                        List<AiMilvusPdfMarkdown> records,
                                        String collectionName) {
        int total = records.size();
        List<JsonObject> data = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            AiMilvusPdfMarkdown request = records.get(i);
            JsonObject jsonObject = new JsonObject();
            Gson gson = new Gson();
            Long id = UUIDConverter.generateSafeUUIDAsLong();
            request.setMilvusId(String.valueOf(id));
            jsonObject.addProperty("id",id);
            jsonObject.addProperty("title", request.getTitle());
            if (request.getText().length() > MAX_TEXT_LENGTH){
                throw new BusinessException(500,"文档内容过于大,联系管理人员");
            }
            jsonObject.addProperty("text", request.getText());
            jsonObject.add("vector", gson.toJsonTree(request.getVector()));
            jsonObject.addProperty("reportType", request.getReportType());
            if (null !=request.getExpireDate() ){
                jsonObject.addProperty("expireDate", request.getExpireDate().toString());

            }
            if (null != request.getReportDate()){
                jsonObject.addProperty("reportDate", request.getReportDate().toString());
            }
            jsonObject.addProperty("source", request.getSource());
            jsonObject.addProperty("productName", request.getProductName());
            jsonObject.addProperty("reportId", request.getReportId());
            jsonObject.addProperty("sourceSystem", request.getSourceSystem());
            jsonObject.addProperty("lang", request.getLang());
            if (null != request.getIngestDate()){
                jsonObject.addProperty("ingestDate", request.getIngestDate().toString());
            }
            jsonObject.addProperty("version", request.getVersion());
            jsonObject.addProperty("checksum", request.getChecksum());
            jsonObject.addProperty("visibility", request.getVisibility());
            jsonObject.addProperty("tags", request.getTags());
            jsonObject.addProperty("standardRefs", request.getStandardRefs());
            jsonObject.addProperty("docStatus", request.getDocStatus());
            jsonObject.addProperty("brand", request.getBrand());
            jsonObject.addProperty("sku", request.getSku());
            jsonObject.addProperty("spec", request.getSpec());
            jsonObject.addProperty("batchNo", request.getBatchNo());
            jsonObject.addProperty("metedate", request.getMetedate());
            data.add(jsonObject);
        }

        InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName)
                .data(data)
                .build();

        InsertResp insertResp = client.insert(insertReq);
        if (insertResp.getInsertCnt() > 0){
            records.forEach(record -> {
                aiMilvusPdfMarkdownMapper.insert(record);
            });
        }
    }
}