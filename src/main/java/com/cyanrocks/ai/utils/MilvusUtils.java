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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * Generate embeddings for the given PDF markdown records, ensure the specified Milvus collection exists,
     * and insert the records into that collection in batches; underlying metadata is synchronized to local mappers.
     *
     * @param inputList     the list of AiMilvusPdfMarkdown records to process and insert
     * @param collectionName the target Milvus collection name
     * @throws Exception    if vector generation, Milvus connection/creation, or batch insertion fails;
     *                      on write failures a BusinessException(500, "写入数据库失败") is thrown
     */
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
        } catch (Exception e) {
            System.out.println("写入数据库失败" + e.getMessage());
            throw new BusinessException(500, "写入数据库失败");
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

    /**
     * Inserts a single GBI table entry into the specified Milvus collection and, if the insert succeeds,
     * persists corresponding metadata to the local aiGbiTable table.
     *
     * The method generates an ID and an embedding from the record's field, writes a single document
     * (id, field, tableName, vector, searchSql) to Milvus, and on successful insertion stores a metadata
     * row (including milvusId, tableName, field, searchSql, metedate, createAt) via aiGbiTableMapper.
     *
     * @param gbiMilvus     the GBI record containing at least field, tableName, searchSql, and metedate
     * @param collectionName the name of the Milvus collection to insert into
     * @throws BusinessException if the target Milvus collection does not exist
     */
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
                jsonObject.addProperty("id", id);
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
                if (insertResp.getInsertCnt() > 0) {
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

    /**
     * Insert a GBI explanation into the specified Milvus collection and persist its metadata.
     *
     * Ensures the collection exists and is loaded, embeds the `gbiExplain` text to produce its vector,
     * inserts a single record into Milvus, and—when the insert succeeds—writes a corresponding
     * AiGbiExplain row (including the generated Milvus id and creation timestamp) to the local database.
     *
     * @param gbiMilvus      object containing the explanation text (`gbiExplain`) and its `explainType`
     * @param collectionName the target Milvus collection name
     */
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
                jsonObject.addProperty("id", id);
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
                if (insertResp.getInsertCnt() > 0) {
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

    /**
     * Performs a basic connectivity and collection-creation check against Milvus.
     *
     * Ensures the configured Milvus collection "chewy_parse_new" exists (creating it if necessary)
     * and retrieves its load state.
     */
    public void test() {
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
//        QueryReq queryReq = QueryReq.builder()
//                .collectionName("pdf_markdown")
//                .filter("id > 0 ")
//                .build();
//        // 执行标量查询
//        QueryResp queryResp = client.query(queryReq);
//        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
//        List<JsonObject> list = new ArrayList<>();
//        for (QueryResp.QueryResult queryResult : queryResults) {
//            Map<String, Object> entity = queryResult.getEntity();
//            Gson gson = new Gson();
//            JsonObject updateData =gson.toJsonTree(entity).getAsJsonObject();
//            list.add(updateData);
//        }
        createCollectionIfNotExists(client, "chewy_parse_new");

        Boolean loadState = client.getLoadState(
                GetLoadStateReq.builder()
                        .collectionName("chewy_parse_new")
                        .build()
        );
//        InsertReq insertReq = InsertReq.builder()
//                .collectionName("pdf_markdown_new")
//                .data(list)
//                .build();
//        InsertResp insertResp = client.insert(insertReq);
    }

    /**
     * Update a PDF markdown record in Milvus by applying the non-null fields from the provided entity.
     *
     * The method queries Milvus for the record with the id specified in `req.getMilvusId()` and upserts
     * an update payload where only non-null properties from `req` overwrite the existing fields.
     *
     * @param req        the source entity containing `milvusId` and fields to update; only non-null fields are written
     * @param collection the Milvus collection name that contains the target record
     * @return           `true` if the upsert operation affected one or more records, `false` otherwise
     */
    public boolean updateMilvusPdfMarkdownById(AiMilvusPdfMarkdown req, String collection) {
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

    /**
     * Update a GBI table entry in the specified Milvus collection using non-null fields from the request.
     *
     * For the entity whose id equals req.getMilvusId(), this method updates any provided fields:
     * tableName, field, searchSql, and metedate. When `field` is provided, the stored vector is
     * recomputed from that field's embedding before the upsert. The changes are applied via an upsert.
     *
     * @param req        an AiGbiTable containing the target `milvusId` and any fields to update (tableName, field, searchSql, metedate)
     * @param collection the name of the Milvus collection to update
     * @return           `true` if the upsert affected at least one record, `false` otherwise
     */
    public boolean updateGbiTableById(AiGbiTable req, String collection) {
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

    /**
     * Updates a gbi explanation record in Milvus identified by the given entity's Milvus ID.
     *
     * If `req.gbiExplain` is provided, the stored `gbiExplain` field is replaced and the `vector`
     * field is recomputed from the new explanation. If `req.explainType` is provided, the stored
     * `explainType` field is replaced.
     *
     * @param req the AiGbiExplain object containing the Milvus ID and fields to update
     * @param collection the Milvus collection name where the record resides
     * @return `true` if one or more records were upserted (updated) in Milvus, `false` otherwise
     */
    public boolean updateGbiExpainById(AiGbiExplain req, String collection) {
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

    /**
     * Delete a Milvus entity by its numeric ID from the specified collection.
     *
     * @param milvusId  the Milvus entity ID as a decimal string (will be converted to a long)
     * @param collection the name of the Milvus collection to delete from
     * @return `true` if at least one entity was deleted, `false` otherwise
     */
    public boolean deleteMilvusById(String milvusId, String collection) {
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
            return deleteResp.getDeleteCnt() > 0;
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

    /**
     * Perform a semantic search over the specified Milvus collection, optionally using a file for model context,
     * and return a compact result map containing matched titles, sources, types, and generated answer text.
     *
     * Performs quick scalar fallbacks (report-date and exact-field checks) before running a vector search using
     * a rewrite of the user question; records the query and result in history.
     *
     * @param que the original user query string
     * @param file an optional file supplied to the downstream model call (used as context for answer generation)
     * @param collectionName the Milvus collection to search
     * @param dingId the caller's identifier used for visibility filtering and history association
     * @param filterReportType when non-null, restricts searches to entries whose reportType equals this value
     * @return a map of result fields:
     *         - "title": comma-separated matched document titles (or permission-masked titles),
     *         - "source": comma-separated sources corresponding to titles,
     *         - "reportType": comma-separated reportType entries aligned with titles (format may include a title-to-type marker),
     *         - "text": generated answer or document excerpt selected as the primary response,
     *         - "rewriteQuestion": the (possibly rewritten) question actually used for vector search,
     *         - "id": the persisted query history id for this search
     */
    public Map<String, String> semanticSearch2(String que, MultipartFile file, String collectionName, String dingId, String filterReportType){
        String question = que.trim().replace("\r", "").replace("\n", "");
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
                StringBuilder filter = new StringBuilder("(");
                filter.append(fields.stream()
                        .map(field -> field + " == \"" + question.replace("\"", "\\\"") + "\"")
                        .reduce((a, b) -> a + " or " + b)
                        .orElse(""));
                filter.append(")");
                if(null != filterReportType){
                    filter.append(" and reportType == \"").append(filterReportType).append("\"");
                }
                QueryReq queryReq = QueryReq.builder()
                        .collectionName(collectionName)
                        .filter(filter.toString())
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
                        if (titles.isEmpty()) {
                            Set<String> newTitles = new HashSet<>();
                            queryResults.forEach(queryResult -> {
                                String title = "无权限查看" + queryResult.getEntity().get("title");
                                newTitles.add(title);
                            });
                            result.put("title", String.join(",", newTitles));
                        } else {
                            result.put("title", String.join(",", titles));
                            result.put("source", String.join(",", sources));
                        }

                        hasReturn = true;
                    }
                }
            }

            if (!hasReturn) {
                //  向量化问题,加上新问题
                List<AiQueryHistory> historyList = aiQueryHistoryMapper.selectPage(new Page<>(1, 1), Wrappers.<AiQueryHistory>lambdaQuery()
                        .eq(AiQueryHistory::getUserId, dingId).eq(AiQueryHistory::getSource, "问问")
                        .orderByDesc(AiQueryHistory::getCreateAt)).getRecords();

                //根据最近一条问题，重写问题
                if (CollectionUtil.isEmpty(historyList)) {
                    rewriteQuestion = question;
                } else if (null == historyList.get(0).getRewriteQuery()) {
                    if (null == historyList.get(0).getQuery()) {
                        //过滤，开启新问题
                        rewriteQuestion = question;
                    } else {
                        rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getQuery());
                    }
                } else {
                    rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getRewriteQuery());
                }
                System.out.println("查询问题:" + rewriteQuestion);
                AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                        .eq(AiEnum::getType, "topK"));
                Map<String, Object> searchParams = new HashMap<>();
                searchParams.put("nprobe", 10);
                StringBuilder filter = new StringBuilder("(visibility == \"all\" or visibility like \"%" + dingId + "%\")");
                if(null != filterReportType){
                    filter.append(" and reportType == \"").append(filterReportType).append("\"");
                }
                if(null != filterReportType){
                    filter.append(" and reportType == \"").append(filterReportType).append("\"");
                }
                SearchResp searchResp = client.search(SearchReq.builder()
                        .collectionName(collectionName)
                        .filter(filter.toString())
                        .data(Collections.singletonList(new FloatVec(embeddingResourceManager.embedText(rewriteQuestion))))
                        .annsField("vector")
                        .searchParams(searchParams)
                        .topK(Integer.parseInt(topK.getValue()))
                        .outputFields(Arrays.asList("title", "source", "text", "reportType"))
                        .build());

                if (null != searchResp && CollectionUtil.isNotEmpty(searchResp.getSearchResults())) {
                    List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
                    List<SearchResp.SearchResult> scores = searchResults.get(0);
                    // 处理最终结果
                    List<String> sources = new ArrayList<>();
                    List<String> titles = new ArrayList<>();
                    List<String> reportTypes = new ArrayList<>();
                    Map<String, StringBuilder> title2text = new HashMap<>();
                    for (SearchResp.SearchResult score : scores) {
                        Map<String, Object> entity = score.getEntity();
                        String source = (String) entity.get("source");
                        if (!sources.contains(source)) {
                            sources.add(source);
                        }
                        String title = (String) entity.get("title");
                        String reportType = (String) entity.get("reportType");
                        if (!titles.contains(title)) {
                            titles.add(title);
                            reportTypes.add(title + "&#&" + reportType);
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
                                .filter("title == '" + title.replace("'", "''") + "'")
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
                    for (int i = 0; i < titles.size(); i++) {
                        resultText.append(titles.get(i)).append("：").append("\n").append(title2text.get(titles.get(i))).append("\n");
                    }
                    //加入历史认可查询
//                    Map<String, Object> searchParams2 = new HashMap<>();
//                    searchParams2.put("nprobe", 10);
//                    SearchResp searchResp2 = client.search(SearchReq.builder()
//                            .collectionName("query_accept")
//                            .data(Collections.singletonList(new FloatVec(embeddingResourceManager.embedText(rewriteQuestion))))
//                            .filter("source == '问问'")
//                            .annsField("record")
//                            .searchParams(searchParams2)
//                            .topK(3)
//                            .outputFields(Collections.singletonList("record"))
//                            .build());
//
//                    if (null != searchResp2 && CollectionUtil.isNotEmpty(searchResp2.getSearchResults())) {
//                        List<List<SearchResp.SearchResult>> searchResults2 = searchResp2.getSearchResults();
//                        List<SearchResp.SearchResult> scores2 = searchResults2.get(0);
//                        // 处理最终结果
//                        for (SearchResp.SearchResult score : scores2) {
//                            Map<String, Object> entity = score.getEntity();
//                            String record = (String) entity.get("record");
//                            Map<String, String> query2sql = new HashMap<>();
//                            query2sql.put("历史认可查询：", record);
//                            resultText.append(JSONObject.toJSONString(query2sql));
//                        }
//                    }
                    String modelText = "";
//                    try {
//                        modelText = aiModelUtils.getChewyParseWithImg(rewriteQuestion,resultText.toString(), new ArrayList<>());
//                    } catch (SocketTimeoutException e) {
//                        result.put("text", "连接超时，请稍后再试");
//                    }
                    modelText = aiModelUtils.callWithMessageWithImg(rewriteQuestion, file, resultText.toString(), new ArrayList<>());
                    result.put("rewriteQuestion",rewriteQuestion);
                    if (!"".equals(modelText)) {
                        if (modelText.contains("#参考资料#")) {
                            result.put("text", modelText.split("#参考资料#")[0]);
                            List<Integer> ck = Arrays.stream(modelText.split("#参考资料#")[1].replaceAll("[\\[\\]]", "")
                                    .split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).collect(Collectors.toList());
                            if (CollectionUtil.isNotEmpty(ck)) {
                                // 过滤出合法的引用索引（1-based -> 转为 0-based 后必须 < 列表大小）
                                List<Integer> validIndices = ck.stream()
                                        .filter(i -> i != null && i > 0) // 确保 i 是正整数
                                        .filter(i -> (i - 1) < titles.size() &&
                                                (i - 1) < sources.size() &&
                                                (i - 1) < reportTypes.size())
                                        .collect(Collectors.toList());

                                if (!validIndices.isEmpty()) {
                                    result.put("title", validIndices.stream().map(i -> titles.get(i - 1)).collect(Collectors.joining(",")));
                                    result.put("source", validIndices.stream().map(i -> sources.get(i - 1)).collect(Collectors.joining(",")));
                                    result.put("reportType", validIndices.stream().map(i -> reportTypes.get(i - 1)).collect(Collectors.joining(",")));
                                } else {
                                    // 可选：当所有引用都无效时，回退到全部结果或置空
                                    result.put("title", String.join(",", titles));
                                    result.put("source", String.join(",", sources));
                                    result.put("reportType", String.join(",", reportTypes));
                                }
                            }
                        } else {
                            result.put("title", String.join(",", titles));
                            result.put("source", String.join(",", sources));
                            result.put("reportType", String.join(",", reportTypes));
                            result.put("text", modelText);
                        }
                    }
                    if (null != result.get("reportType")) {
                        String[] reportTypeList = result.get("reportType").split(",");
                        for (String reportType : reportTypeList) {
                            if (reportType.contains("&#&") && reportType.split("&#&")[1].contains("佩蒂文件")) {
                                result.put("text", result.get("text") + "\n> **最终解释权归属**：人事或行政部门。");
                                break;
                            }
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
            result.put("id", aiQueryHistory.getId().toString());
            try {
                client.close();
            } catch (Exception e) {
                // 记录关闭异常，但不抛出
                System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
            }
            return result;
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

    /**
     * Produce a rewritten version of the current query for use in semantic searches.
     *
     * <p>Intended to return a reformulated or clarified form of the input question to improve retrieval or downstream processing.</p>
     *
     * @return the rewritten question, or `null` if no rewrite is available
     */
    public String reWriteQuestion(){

        return null;
    }

    /**
     * Performs a semantic search over the specified Milvus collection using the given question and uploaded files, rewrites the question with recent user context, aggregates matching document content, invokes the AI model to generate a final answer, and persists the query history.
     *
     * <p>Result map keys:
     * <ul>
     *   <li>`rewriteQuestion` — the rewritten question used for search</li>
     *   <li>`title` — comma-separated matched document titles (when available)</li>
     *   <li>`source` — comma-separated sources corresponding to the matched titles (when available)</li>
     *   <li>`reportType` — comma-separated title+reportType entries formatted as `title&#&reportType` (when available)</li>
     *   <li>`text` — generated answer text (preferred) or fallback message</li>
     *   <li>`id` — persisted query history id</li>
     * </ul>
     *
     * @param que the user's original question
     * @param files a list of uploaded files to include when generating context for the model
     * @param collectionName the Milvus collection to search
     * @param dingId the user identifier used for visibility filtering and history lookup
     * @param filterReportType optional reportType value to restrict search results; pass null to disable
     * @return a map containing the response fields described above
     */
    public Map<String, String> semanticSearch3(String que, List<MultipartFile> files, String collectionName, String dingId, String filterReportType){
        String question = que.trim().replace("\r", "").replace("\n", "");
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
            if (!hasReturn) {
                //  向量化问题,加上新问题
                List<AiQueryHistory> historyList = aiQueryHistoryMapper.selectPage(new Page<>(1, 1), Wrappers.<AiQueryHistory>lambdaQuery()
                        .eq(AiQueryHistory::getUserId, dingId).eq(AiQueryHistory::getSource, "问问")
                        .orderByDesc(AiQueryHistory::getCreateAt)).getRecords();

                //根据最近一条问题，重写问题
                if (CollectionUtil.isEmpty(historyList)) {
                    rewriteQuestion = question;
                } else if (null == historyList.get(0).getRewriteQuery()) {
                    if (null == historyList.get(0).getQuery()) {
                        //过滤，开启新问题
                        rewriteQuestion = question;
                    } else {
                        rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getQuery());
                    }
                } else {
                    rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getRewriteQuery());
                }
                System.out.println("查询问题:" + rewriteQuestion);
                AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                        .eq(AiEnum::getType, "topK"));
                Map<String, Object> searchParams = new HashMap<>();
                searchParams.put("nprobe", 10);
                StringBuilder filter = new StringBuilder("(visibility == \"all\" or visibility like \"%" + dingId + "%\")");
                if(null != filterReportType){
                    filter.append(" and reportType == \"").append(filterReportType).append("\"");
                }
                if(null != filterReportType){
                    filter.append(" and reportType == \"").append(filterReportType).append("\"");
                }
                SearchResp searchResp = client.search(SearchReq.builder()
                        .collectionName(collectionName)
                        .filter(filter.toString())
                        .data(Collections.singletonList(new FloatVec(embeddingResourceManager.embedText(rewriteQuestion))))
                        .annsField("vector")
                        .searchParams(searchParams)
                        .topK(Integer.parseInt(topK.getValue()))
                        .outputFields(Arrays.asList("title", "source", "text", "reportType"))
                        .build());

                if (null != searchResp && CollectionUtil.isNotEmpty(searchResp.getSearchResults())) {
                    List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
                    List<SearchResp.SearchResult> scores = searchResults.get(0);
                    // 处理最终结果
                    List<String> sources = new ArrayList<>();
                    List<String> titles = new ArrayList<>();
                    List<String> reportTypes = new ArrayList<>();
                    Map<String, StringBuilder> title2text = new HashMap<>();
                    for (SearchResp.SearchResult score : scores) {
                        Map<String, Object> entity = score.getEntity();
                        String source = (String) entity.get("source");
                        if (!sources.contains(source)) {
                            sources.add(source);
                        }
                        String title = (String) entity.get("title");
                        String reportType = (String) entity.get("reportType");
                        if (!titles.contains(title)) {
                            titles.add(title);
                            reportTypes.add(title + "&#&" + reportType);
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
                                .filter("title == '" + title.replace("'", "''") + "'")
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
                    for (int i = 0; i < titles.size(); i++) {
                        resultText.append(titles.get(i)).append("：").append("\n").append(title2text.get(titles.get(i))).append("\n");
                    }
                    String modelText = "";
//                    try {
//                        modelText = aiModelUtils.callWithMessageNoMarkdown(rewriteQuestion,resultText.toString(), new ArrayList<>());
//                    } catch (SocketTimeoutException e) {
//                        result.put("text", "连接超时，请稍后再试");
//                    }
                    modelText = aiModelUtils.callWithMessageWithImgNoMarkdown(rewriteQuestion, files, resultText.toString(), new ArrayList<>());
                    result.put("rewriteQuestion",rewriteQuestion);
                    if (!"".equals(modelText)) {
                        result.put("title", String.join(",", titles));
                        result.put("source", String.join(",", sources));
                        result.put("reportType", String.join(",", reportTypes));
                        result.put("text", modelText);
                    }
                } else {
                    result.put("text", "实在抱歉，这个问题超出我的解答范围啦，麻烦你移步项目群咨询项目辅导员，他们会及时为你答疑的～");
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
            result.put("id", aiQueryHistory.getId().toString());
            try {
                client.close();
            } catch (Exception e) {
                // 记录关闭异常，但不抛出
                System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
            }
            return result;
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


    /**
     * Perform a purely vector-based GBI search, generate a candidate SQL using model-provided
     * context and explanations, execute or attempt to repair the SQL, and return the final
     * SQL and analysis results.
     *
     * The method rewrites the incoming question using the caller's recent query history,
     * searches the specified collections for relevant fields, base SQL fragments and business
     * explanations, augments these with any global explanations, invokes model helpers to
     * produce and review SQL, executes the SQL when it begins with SELECT, and formats a
     * human-readable analysis of the query results. It also persists a query history record.
     *
     * @param question               the original user question to search and rewrite
     * @param collectionName         Milvus collection name to search for fields and base SQL
     * @param explainCollectionName  Milvus collection name to search for business logic explanations
     * @param dingId                 caller user identifier (used to load recent history and save the new history record)
     * @return a map containing:
     *         - "rewriteQuery": the rewritten question used for searches;
     *         - "sql": the final SQL produced by the model (if any);
     *         - "result": an analysis or a textual summary of the SQL execution results (or failure messages like "暂无结果" / "数据库查询失败");
     *         - "id": the persisted AiQueryHistory id for the saved query record.
     */
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
            if (CollectionUtil.isEmpty(historyList)) {
                rewriteQuestion = question;
            } else if (null == historyList.get(0).getRewriteQuery()) {
                if (null == historyList.get(0).getQuery()) {
                    //过滤，开启新问题
                    rewriteQuestion = question;
                } else {
                    rewriteQuestion = aiModelUtils.callWithRewriteQuestion(question, historyList.get(0).getQuery());
                }
            } else {
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
                    .outputFields(Arrays.asList("field", "searchSql"))
                    .build());

            if (null != searchResp && CollectionUtil.isNotEmpty(searchResp.getSearchResults())) {
                List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
                List<SearchResp.SearchResult> scores = searchResults.get(0);
                // 处理最终结果
                for (SearchResp.SearchResult score : scores) {
                    Map<String, Object> entity = score.getEntity();
                    String field = (String) entity.get("field");
                    String sql = (String) entity.get("searchSql");
                    Map<String, String> query2sql = new HashMap<>();
                    query2sql.put("字段包含：", field);
                    query2sql.put("表基础字段sql：", sql);
                    List<JSONObject> sqlResult = aiEnumMapper.doSql(sql + " limit 100");
                    query2sql.put("表基础字段sql查询100条数据进行参考：", JSONObject.toJSONString(sqlResult));
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
                    query2sql.put("业务逻辑解释：", gbiExplain);
                    System.out.println("业务逻辑解释：" + gbiExplain);
                    query2sqlList.add(query2sql);
                }
            }
            //加入历史认可查询
//            Map<String, Object> searchParams2 = new HashMap<>();
//            searchParams2.put("nprobe", 10);
//            SearchResp searchResp2 = client.search(SearchReq.builder()
//                    .collectionName("query_accept")
//                    .data(Collections.singletonList(new FloatVec(embeddingResourceManager.embedText(rewriteQuestion))))
//                    .filter("source == '问数'")
//                    .annsField("vector")
//                    .searchParams(searchParams2)
//                    .topK(3)
//                    .outputFields(Collections.singletonList("record"))
//                    .build());
//
//            if (null != searchResp2 && CollectionUtil.isNotEmpty(searchResp2.getSearchResults())) {
//                List<List<SearchResp.SearchResult>> searchResults = searchResp2.getSearchResults();
//                List<SearchResp.SearchResult> scores = searchResults.get(0);
//                // 处理最终结果
//                for (SearchResp.SearchResult score : scores) {
//                    Map<String, Object> entity = score.getEntity();
//                    String record = (String) entity.get("record");
//                    Map<String, String> query2sql = new HashMap<>();
//                    query2sql.put("历史认可查询：", record);
//                    query2sqlList.add(query2sql);
//                }
//            }

            if (CollectionUtil.isEmpty(query2sqlList)) {
                result.put("result", "数据库查询失败");
            } else {
                //将全局业务逻辑解释加入
                List<AiGbiExplain> aiGbiExplainList = aiGbiExplainMapper.selectList(Wrappers.<AiGbiExplain>lambdaQuery()
                        .eq(AiGbiExplain::getExplainType, false));
                if (CollectionUtil.isNotEmpty(aiGbiExplainList)) {
                    aiGbiExplainList.forEach(explain -> {
                        Map<String, String> query2sql = new HashMap<>();
                        query2sql.put("业务逻辑解释：", explain.getGbiExplain());
                        query2sqlList.add(query2sql);
                    });
                }
                //调用模型处理返回结果
                // 使用正则表达式匹配 ```sql 和 ``` 之间的内容
                String sql = aiModelUtils.callWithGbiQa(rewriteQuestion, query2sqlList.toString()).trim();
                sql = aiModelUtils.gbiSqlReview(rewriteQuestion, query2sqlList.toString(), sql);
                sql = this.trimSql(sql);
                result.put("rewriteQuery", rewriteQuestion);
                try {
                    if (sql.trim().toUpperCase().startsWith("SELECT")) {
                        result.put("sql", sql);
                        List<JSONObject> sqlResult = aiEnumMapper.doSql(sql);
                        if (null != sqlResult) {
                            result.put("result", aiModelUtils.callWithAnalysisJson(rewriteQuestion, sqlResult.toString()));
                        } else {
                            result.put("result", "暂无结果");
                        }
                    } else {
                        result.put("result", "暂无结果");
                    }
                } catch (Exception e) {
                    sql = aiModelUtils.gbiSqlRepair(rewriteQuestion, query2sqlList.toString(), sql, e.getMessage());
                    sql = this.trimSql(sql);
                    try {
                        if (sql.trim().toUpperCase().startsWith("SELECT")) {
                            result.put("sql", sql);
                            List<JSONObject> sqlResult = aiEnumMapper.doSql(sql);
                            if (null != sqlResult) {
                                result.put("result", aiModelUtils.callWithAnalysisJson(rewriteQuestion, sqlResult.toString()));
                            } else {
                                result.put("result", "暂无结果");
                            }
                        } else {
                            result.put("result", "暂无结果");
                        }
                    } catch (Exception e2) {
                        result.put("sql", sql);
                        result.put("result", "暂无结果");
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
            JSONObject historyResult = new JSONObject();
            historyResult.put("sql", result.get("sql"));
            historyResult.put("result", result.get("result"));
            aiQueryHistory.setResult(JSONObject.toJSONString(historyResult));
            aiQueryHistory.setCreateAt(LocalDateTime.now());
            aiQueryHistory.setSource("问数");
            aiQueryHistoryMapper.insert(aiQueryHistory);
            result.put("id", aiQueryHistory.getId().toString());
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

    /**
     * Extracts and cleans an SQL statement from an input string.
     *
     * If the input contains a fenced code block of the form
     * ```sql
     * ...
     * ```
     * the enclosed content is extracted; otherwise the original input is used.
     * Leading and trailing fence markers and surrounding whitespace/newlines are removed.
     *
     * @param sql the raw input that may contain a fenced SQL code block
     * @return the cleaned SQL content with surrounding fences and leading/trailing newlines removed
     */
    private String trimSql(String sql) {
        Pattern pattern = Pattern.compile("```sql\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            sql = matcher.group(1).trim().replace("```sql", "").replace("```", "").trim();
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

    /**
     * Generate and attach embedding vectors for each PDF markdown record.
     *
     * @param records list of AiMilvusPdfMarkdown whose `vector` field will be populated from each record's `text`
     * @throws BusinessException if embedding fails for any record; the exception message includes the failing record's id
     */
    private void generatePdfReportVectors(List<AiMilvusPdfMarkdown> records) {
        for (AiMilvusPdfMarkdown record : records) {
            try {
                record.setVector(embeddingResourceManager.embedText(record.getText()));
            } catch (Exception e) {
                System.out.println("Vector生成失败: " + e);
                System.out.println("文本长度为: " + estimateTokens(record.getText()));
                throw new BusinessException(500, "Vector生成失败，" + record.getId());
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

    /**
     * Ensures a Milvus collection with the given name exists; if missing, creates it with the
     * predefined schema, sparse BM25 function, and dense/sparse indexes appropriate for that collection.
     *
     * Supported collection names and their schema patterns: "pdf_markdown", "query_accept",
     * "chewy_parse_new", "gbi_table", "gbi_explain". Each created collection includes an `id` primary key,
     * a dense float vector field (`vector`) with dimension VECTOR_DIM, a sparse vector field (`sparse`)
     * backed by a BM25 function (configured for a text-like field), and other metadata fields used by the application.
     *
     * @param client        the Milvus client used to check for and create collections
     * @param collectionName the target collection name to ensure exists
     */
    private void createCollectionIfNotExists(MilvusClientV2 client, String collectionName) {
        Boolean response = client.hasCollection(
                HasCollectionReq.builder()
                        .collectionName(collectionName)
                        .build());

        if (!response) {
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .build();
            List<IndexParam> indexParams = new ArrayList<>();

            if ("pdf_markdown".equals(collectionName)) {
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
            } else if ("query_accept".equals(collectionName)) {
                schema.addField(AddFieldReq.builder()
                        .fieldName("id")
                        .dataType(io.milvus.v2.common.DataType.Int64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("record").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(MAX_TEXT_LENGTH).enableAnalyzer(true).description("内容")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("source").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).description("来源")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("vector").dataType(io.milvus.v2.common.DataType.FloatVector).dimension(VECTOR_DIM).description("问题稠密向量")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("sparse").dataType(io.milvus.v2.common.DataType.SparseFloatVector).description("问题稀疏向量")
                        .build());
                schema.addFunction(CreateCollectionReq.Function.builder()
                        .functionType(io.milvus.common.clientenum.FunctionType.BM25)
                        .name("record_bm25_emb")
                        .inputFieldNames(Collections.singletonList("record"))
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
            } else if ("chewy_parse_new".equals(collectionName)) {
                schema.addField(AddFieldReq.builder()
                        .fieldName("id")
                        .dataType(io.milvus.v2.common.DataType.Int64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("text").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(MAX_TEXT_LENGTH).enableAnalyzer(true).description("内容")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("input").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(2550).description("输入")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("productName").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(2550).description("product_name")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("primaryFunction").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(2550).description("primary_function")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("healthScore").dataType(DataType.Int64).description("health_score")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("redFlags").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(2550).description("red_flags")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("brand").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(2550).description("brand")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("vector").dataType(io.milvus.v2.common.DataType.FloatVector).dimension(VECTOR_DIM).description("问题稠密向量")
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("sparse").dataType(io.milvus.v2.common.DataType.SparseFloatVector).description("问题稀疏向量")
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
            } else if ("gbi_explain".equals(collectionName)) {
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

    /**
     * Prepares and inserts the given PDF-markdown records into the specified Milvus collection and persists their metadata to the local mapper.
     *
     * For each record this method generates a unique Milvus id (assigned to the record's `milvusId`), validates the record text length, constructs the JSON payload (including the vector and optional date fields), and performs a batch insert into Milvus. If the insert reports inserted rows, each record is persisted via `aiMilvusPdfMarkdownMapper.insert(...)`.
     *
     * @param client Milvus client used to perform the insert.
     * @param records list of PDF-markdown records to insert; each record's `milvusId` will be set to the generated id before insertion.
     * @param collectionName target Milvus collection name.
     * @throws BusinessException if any record's text length exceeds MAX_TEXT_LENGTH.
     */
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
            jsonObject.addProperty("id", id);
            jsonObject.addProperty("title", request.getTitle());
            if (request.getText().length() > MAX_TEXT_LENGTH) {
                throw new BusinessException(500, "文档内容过于大,联系管理人员");
            }
            jsonObject.addProperty("text", request.getText());
            jsonObject.add("vector", gson.toJsonTree(request.getVector()));
            jsonObject.addProperty("reportType", request.getReportType());
            if (null != request.getExpireDate()) {
                jsonObject.addProperty("expireDate", request.getExpireDate().toString());

            }
            if (null != request.getReportDate()) {
                jsonObject.addProperty("reportDate", request.getReportDate().toString());
            }
            jsonObject.addProperty("source", request.getSource());
            jsonObject.addProperty("productName", request.getProductName());
            jsonObject.addProperty("reportId", request.getReportId());
            jsonObject.addProperty("sourceSystem", request.getSourceSystem());
            jsonObject.addProperty("lang", request.getLang());
            if (null != request.getIngestDate()) {
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
        if (insertResp.getInsertCnt() > 0) {
            records.forEach(record -> {
                aiMilvusPdfMarkdownMapper.insert(record);
            });
        }
    }

    /**
     * Stores an LLM query-and-response record in the specified Milvus collection and returns the generated id.
     *
     * <p>The inserted record contains: `id`, a JSON-serialized `record` with `query` and `result`, `source`, and an embedded `vector`.
     * If insertion fails, the method logs the error and still returns the generated id.
     *
     * @param queryHistory the history entry whose `rewriteQuery` and `result` will be recorded
     * @param collectionName the target Milvus collection name
     * @return the generated Milvus id for the record
     */
    public Long processLlmBackMilvus(AiQueryHistory queryHistory, String collectionName) {
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
            JSONObject record = new JSONObject();
            record.put("query",queryHistory.getRewriteQuery());
            record.put("result",queryHistory.getResult());
            jsonObject.addProperty("record", JSONObject.toJSONString(record));
            jsonObject.addProperty("source", queryHistory.getSource());
            jsonObject.add("vector", gson.toJsonTree(embeddingResourceManager.embedText(JSONObject.toJSONString(record))));
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
}