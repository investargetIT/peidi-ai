package com.cyanrocks.ui.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyanrocks.ui.dao.entity.AiEnum;
import com.cyanrocks.ui.dao.entity.AiModel;
import com.cyanrocks.ui.dao.entity.AiQueryHistory;
import com.cyanrocks.ui.dao.mapper.AiEnumMapper;
import com.cyanrocks.ui.dao.mapper.AiModelMapper;
import com.cyanrocks.ui.dao.mapper.AiQueryHistoryMapper;
import com.cyanrocks.ui.exception.BusinessException;
import com.cyanrocks.ui.vo.BiJiuqianRecordMilvus;
import com.cyanrocks.ui.vo.request.BiJiuqianReportReq;
import com.cyanrocks.ui.vo.request.PdfRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.BaseRanker;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final int BATCH_SIZE = 1000;
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
    private static final String MILVUS_IP = "121.43.145.161";
    private static final String DASHSCOPE_API_KEY = "sk-17ec61d83bba433f8acb638aeced5ab8";
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
//    private static final String MILVUS_IP = "localhost";

    @Autowired
    private RerankClient rerankClient;
    @Autowired
    private AiQueryHistoryMapper aiQueryHistoryMapper;
    @Autowired
    private SmartHistoryTruncator smartHistoryTruncator;
    @Autowired
    private AiEnumMapper aiEnumMapper;
    @Autowired
    private AiModelMapper aiModelMapper;


    // 主处理流程
    public void processPdfReportData(List<PdfRequest> inputList, String collectionName) throws Exception {
        // 2. 生成向量
        generatePdfReportVectors(inputList);
        // 3. 连接Milvus

        ConnectConfig config = ConnectConfig.builder()
                .uri("http://121.43.145.161:19530")
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        try {
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
                insertPdfDataInBatches(client, inputList, collectionName);
            } else {
                loadPdfCollection(collectionName, client, inputList);
            }
        } finally {
            client.close();
        }
    }

    private void loadJiuqianCollection(String collectionName, MilvusServiceClient milvusClient, List<BiJiuqianRecordMilvus> records) throws Exception {
        R<RpcStatus> loadResponse = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());

        if (loadResponse.getStatus() != R.Status.Success.getCode()) {
            throw new BusinessException(500, "加载失败: " + loadResponse.getMessage());
        }

        while (true) {
            R<GetLoadStateResponse> response = milvusClient.getLoadState(
                    GetLoadStateParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );

            if (response.getStatus() == R.Status.Success.getCode()) {
                LoadState state = response.getData().getState();
                if (state == LoadState.LoadStateLoaded) {
                    insertJiuqianDataInBatches(milvusClient, records, collectionName);
                    break;
                } else {
                    System.out.println("当前加载状态: " + state);
                }
            } else {
                System.err.println("状态查询失败: " + response.getMessage());
            }
            Thread.sleep(1000); // 每秒检查一次状态
        }
    }

    private void loadPdfCollection(String collectionName, MilvusClientV2 client, List<PdfRequest> records) throws Exception {
        client.loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());

        while (true) {
            Boolean loadState = client.getLoadState(
                    GetLoadStateReq.builder()
                            .collectionName(collectionName)
                            .build()
            );

            if (loadState) {
                insertPdfDataInBatches(client, records, collectionName);
            } else {
                System.err.println("状态查询失败: " + collectionName);
            }
            Thread.sleep(1000); // 每秒检查一次状态
        }
    }


    public Map<String, String> semanticSearch(String question, String collectionName, String dingId) throws Exception {
//        List<AiQueryHistory> historyList = aiQueryHistoryMapper.selectList(Wrappers.<AiQueryHistory>lambdaQuery()
//                .eq(AiQueryHistory::getUserId,dingId)
//                .eq(AiQueryHistory::getIdType,"dingId")
//                .gt(AiQueryHistory::getCreateAt,LocalDateTime.now().minusDays(1))
//                .orderByAsc(AiQueryHistory::getCreateAt));
//        StringBuilder historyQuery = new StringBuilder();
//        if (CollectionUtil.isNotEmpty(historyList)){
//            historyList.forEach(history->{
//                historyQuery.append(history.getQuery()).append(",");
//            });
//        }
        Map<String, String> result = new HashMap<>();
        boolean hasReturn = false;
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://121.43.145.161:19530")
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        //如果问题包含”检测报告“和"到期日",则提取到期日并且进行标量查询
        if (question.contains("检测报告") && question.contains("到期")) {
            String reportDate = callWithGetDate(question);
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
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("title like \"%" + question + "%\" or reportType like \"%" + question + "%\"")
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

        if (!hasReturn) {
//            //  向量化问题,加上新问题
////            question = historyQuery.append(question).toString();
            AiEnum vectorTopK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                    .eq(AiEnum::getType, "vectorTopK"));
            AiEnum sparseTopK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                    .eq(AiEnum::getType, "sparseTopK"));
            AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                    .eq(AiEnum::getType, "topK"));
            List<AnnSearchReq> searchRequests = new ArrayList<>();
            searchRequests.add(AnnSearchReq.builder()
                    .vectorFieldName("vector")
                    .vectors(Collections.singletonList(new FloatVec(EmbeddingResourceManager.embedText(question))))
                    .params("{\"nprobe\": 10}")
                    .topK(Integer.parseInt(vectorTopK.getValue()))
                    .build());
            searchRequests.add(AnnSearchReq.builder()
                    .vectorFieldName("sparse")
                    .vectors(Collections.singletonList(new EmbeddedText(question)))
                    .params("{\"drop_ratio_search\": 0.2}")
                    .topK(Integer.parseInt(sparseTopK.getValue()))
                    .build());
            BaseRanker reranker = new RRFRanker(100);
            HybridSearchReq hybridSearchReq = HybridSearchReq.builder()
                    .collectionName(collectionName)
                    .searchRequests(searchRequests)
                    .outFields(Arrays.asList("title", "source", "text"))
                    .ranker(reranker)
                    .topK(Integer.parseInt(topK.getValue()))
                    .build();

            SearchResp searchResp = client.hybridSearch(hybridSearchReq);
            if (null != searchResp && CollectionUtil.isNotEmpty(searchResp.getSearchResults())) {
                List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
                List<SearchResp.SearchResult> scores = searchResults.get(0);
                // 处理最终结果
                Set<String> sources = new HashSet<>();
                Set<String> titles = new HashSet<>();
                Map<String, StringBuilder> title2text = new HashMap<>();
                for (SearchResp.SearchResult score : scores) {
                    Map<String, Object> entity = score.getEntity();
                    sources.add((String) entity.get("source"));
                    String title = (String) entity.get("title");
                    titles.add(title);
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
                            .filter("title == '" + title + "'")
                            .outputFields(Arrays.asList("title", "text"))
                            .build();
                    QueryResp queryResp = client.query(queryReq);
                    if (null != queryResp && CollectionUtil.isNotEmpty(queryResp.getQueryResults())) {
                        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
                        if (queryResults.size() <= Integer.parseInt(fileChunk.getValue())) {
                            //若chunk数量小于等于5，整个文档都拼接上
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
                for (Map.Entry<String, StringBuilder> entry : title2text.entrySet()) {
                    resultText.append(entry.getValue());
                }
                result.put("title", String.join(",", titles));
                result.put("source", String.join(",", sources));
                //调用模型处理返回结果,传入处理过token长度后的历史问题
//                result.put("text", callWithMessage(question,text.toString(),smartHistoryTruncator.smartTruncate(historyList)));
                result.put("text", callWithMessage(question, resultText.toString(), new ArrayList<>()));
            } else {
                System.err.println("混合查询失败");
            }
        }
        //保存记录，用于上下文对话
        AiQueryHistory aiQueryHistory = new AiQueryHistory();
        aiQueryHistory.setUserId(dingId);
        aiQueryHistory.setIdType("dingId");
        aiQueryHistory.setQuery(question);
        aiQueryHistory.setResult(result.get("text") == null ? result.get("title") : result.get("text"));
        aiQueryHistory.setCreateAt(LocalDateTime.now());
        aiQueryHistoryMapper.insert(aiQueryHistory);
        return result;
    }

    //纯向量查询
    public Map<String, String> semanticSearch2(String question, String collectionName, String dingId) throws Exception {
        String rewriteQuestion = "";
        //纯向量
        Map<String, String> result = new HashMap<>();
        boolean hasReturn = false;
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://121.43.145.161:19530")
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        //如果问题包含”检测报告“和"到期日",则提取到期日并且进行标量查询
        if (question.contains("检测报告") && question.contains("到期")) {
            String reportDate = callWithGetDate(question);
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
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("title like \"%" + question + "%\" or reportType like \"%" + question + "%\"")
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

        if (!hasReturn) {
            //  向量化问题,加上新问题
            List<AiQueryHistory> historyList = aiQueryHistoryMapper.selectPage(new Page<>(1, 1),Wrappers.<AiQueryHistory>lambdaQuery()
                    .eq(AiQueryHistory::getUserId, dingId)
                    .eq(AiQueryHistory::getIdType, "dingId")
                    .orderByDesc(AiQueryHistory::getCreateAt)).getRecords();

            //根据最近一条问题，重写问题
            if (CollectionUtil.isEmpty(historyList)){
                rewriteQuestion = question;
            }else if (null == historyList.get(0).getRewriteQuery()){
                rewriteQuestion = callWithRewriteQuestion(question,historyList.get(0).getQuery().toString());
            }else {
                rewriteQuestion = callWithRewriteQuestion(question,historyList.get(0).getRewriteQuery().toString());
            }
            System.out.println("查询问题:" + rewriteQuestion);
            AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                    .eq(AiEnum::getType, "topK"));
            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("nprobe", 10);
            SearchResp searchResp = client.search(SearchReq.builder()
                    .collectionName(collectionName)
                    .data(Collections.singletonList(new FloatVec(EmbeddingResourceManager.embedText(rewriteQuestion))))
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
                            .filter("title == '" + title + "'")
                            .outputFields(Arrays.asList("title", "text"))
                            .build();
                    QueryResp queryResp = client.query(queryReq);
                    if (null != queryResp && CollectionUtil.isNotEmpty(queryResp.getQueryResults())) {
                        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
                        if (queryResults.size() <= Integer.parseInt(fileChunk.getValue())) {
                            //若chunk数量小于等于5，整个文档都拼接上
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
                for (Map.Entry<String, StringBuilder> entry : title2text.entrySet()) {
                    resultText.append(entry.getValue());
                }
                result.put("title", String.join(",", titles));
                result.put("source", String.join(",", sources));
                //调用模型处理返回结果,传入处理过token长度后的历史问题
                result.put("text", callWithMessage(rewriteQuestion, resultText.toString(), new ArrayList<>()));
            } else {
                System.err.println("混合查询失败");
            }
        }
        //保存记录，用于上下文对话
        AiQueryHistory aiQueryHistory = new AiQueryHistory();
        aiQueryHistory.setUserId(dingId);
        aiQueryHistory.setIdType("dingId");
        aiQueryHistory.setQuery(question);
        aiQueryHistory.setRewriteQuery(rewriteQuestion);
        aiQueryHistory.setResult(result.get("text") == null ? result.get("title") : result.get("text"));
        aiQueryHistory.setCreateAt(LocalDateTime.now());
        aiQueryHistoryMapper.insert(aiQueryHistory);
        return result;
    }

    //短期记忆
    public Map<String, String> semanticSearch3(String question, String collectionName, String dingId) throws Exception {
        //纯向量
        Map<String, String> result = new HashMap<>();
        boolean hasReturn = false;
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://121.43.145.161:19530")
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        //如果问题包含”检测报告“和"到期日",则提取到期日并且进行标量查询
        if (question.contains("检测报告") && question.contains("到期")) {
            String reportDate = callWithGetDate(question);
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
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("title like \"%" + question + "%\" or reportType like \"%" + question + "%\"")
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

        if (!hasReturn) {
            //  向量化问题
            List<Float> questionFloat = EmbeddingResourceManager.embedText(question);
            //取出最近5条问题
            List<AiQueryHistory> historyList = aiQueryHistoryMapper.selectPage(new Page<>(1, 5),Wrappers.<AiQueryHistory>lambdaQuery()
                    .eq(AiQueryHistory::getUserId, dingId)
                    .eq(AiQueryHistory::getIdType, "dingId")
                    .orderByDesc(AiQueryHistory::getCreateAt)).getRecords();

            //构建对话上下文,上下文第一条是最新的
            List<AiQueryHistory> context = new ArrayList<>();
            for (int i = 0; i < historyList.size(); i++) {
                AiQueryHistory historicalItem = historyList.get(i);
                double similarity = cosineSimilarity(questionFloat, EmbeddingResourceManager.embedText(historicalItem.getQuery()));
                System.out.println("问题:"+historicalItem.getQuery()+" 相关度："+ similarity);
                if (similarity >= 0.75) {
                    // 强相关，直接加入上下文
                    context.add(historicalItem);
                } else if (similarity >= 0.6 && context.isEmpty()) {
                    // 弱相关且上下文为空，只取一条帮助衔接
                    context.add(historicalItem);
                    break; // 只取一条弱相关的
                }
                // 相似度低于0.6的丢弃
            }
            //将上下文顺序按正序排序
            List<AiQueryHistory> contextNew = new ArrayList<>();
            StringBuilder questionNew = new StringBuilder();
            if (CollectionUtil.isNotEmpty(context)){
                for (int i = context.size()-1; i >= 0; i--) {
                    contextNew.add(context.get(i));
                    questionNew.append(contextNew.get(i).getQuery()).append(",");
                }
            }
            questionNew.append(question);
            System.out.println("查询问题:" + questionNew);
            AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                    .eq(AiEnum::getType, "topK"));
            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("nprobe", 10);
            SearchResp searchResp = client.search(SearchReq.builder()
                    .collectionName(collectionName)
                    .data(Collections.singletonList(new FloatVec(EmbeddingResourceManager.embedText(questionNew.toString()))))
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
                            .filter("title == '" + title + "'")
                            .outputFields(Arrays.asList("title", "text"))
                            .build();
                    QueryResp queryResp = client.query(queryReq);
                    if (null != queryResp && CollectionUtil.isNotEmpty(queryResp.getQueryResults())) {
                        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
                        if (queryResults.size() <= Integer.parseInt(fileChunk.getValue())) {
                            //若chunk数量小于等于5，整个文档都拼接上
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
                for (Map.Entry<String, StringBuilder> entry : title2text.entrySet()) {
                    resultText.append(entry.getValue());
                }
                result.put("title", String.join(",", titles));
                result.put("source", String.join(",", sources));
                //调用模型处理返回结果,传入处理过token长度后的历史问题
                result.put("text", callWithMessage(question,resultText.toString(),smartHistoryTruncator.smartTruncate(historyList)));
            } else {
                System.err.println("混合查询失败");
            }
        }
        //保存记录，用于上下文对话
        AiQueryHistory aiQueryHistory = new AiQueryHistory();
        aiQueryHistory.setUserId(dingId);
        aiQueryHistory.setIdType("dingId");
        aiQueryHistory.setQuery(question);
        aiQueryHistory.setResult(result.get("text") == null ? result.get("title") : result.get("text"));
        aiQueryHistory.setCreateAt(LocalDateTime.now());
        aiQueryHistoryMapper.insert(aiQueryHistory);
        return result;
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

    public String callWithMessage(String question, String text, List<AiQueryHistory> historyList) throws Exception {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithMessage").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost(API_URL);
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
                user.put("content", "问题是" + question + "\n资料是" + text);
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

    public String callWithGetDate(String question) throws Exception {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithGetDate").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost(API_URL);
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

    public String callWithRewriteQuestion(String question, String historyQuestion) throws Exception {
        final int MAX_RETRIES = 3;
        AiModel aiModel = aiModelMapper.selectOne(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getType, "callWithRewriteQuestion").eq(AiModel::getActive, true));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 创建HTTP POST请求
                HttpPost httpPost = new HttpPost(API_URL);
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

    // 数据展平处理
    private List<BiJiuqianRecordMilvus> flattenJiuqianReportData(List<BiJiuqianReportReq> inputList) {
        List<BiJiuqianRecordMilvus> records = new ArrayList<>();

        for (BiJiuqianReportReq data : inputList) {
            // 添加content记录
            records.add(new BiJiuqianRecordMilvus(
                    ID_GENERATOR.getAndIncrement(),
                    data.month,
                    data.title,
                    true,
                    data.content
            ));

            // 添加description记录
            for (String desc : data.description) {
                records.add(new BiJiuqianRecordMilvus(
                        ID_GENERATOR.getAndIncrement(),
                        data.month,
                        data.title,
                        false,
                        desc
                ));
            }
        }
        return records;
    }

    // 生成向量（单线程安全方式）
    private void generateJiuqianReportVectors(List<BiJiuqianRecordMilvus> records) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (final BiJiuqianRecordMilvus record : records) {
                futures.add(executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            record.vector = EmbeddingResourceManager.embedText(record.text);
                        } catch (Exception e) {
                            throw new RuntimeException("Vector生成失败: " + record.id, e);
                        }
                    }
                }));
            }

            // 等待所有任务完成
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }
    }

    private void generatePdfReportVectors(List<PdfRequest> records) throws Exception {
        for (PdfRequest record : records) {
            try {
                record.setVector(EmbeddingResourceManager.embedText(record.text));
            } catch (Exception e) {
                throw new RuntimeException("Vector生成失败: " + record.id, e);
            }
        }
    }


    private void createCollectionIfNotExists(MilvusClientV2 client, String collectionName) {
        Boolean response = client.hasCollection(
                HasCollectionReq.builder()
                        .collectionName(collectionName)
                        .build());

        if (!response) {
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .build();
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(io.milvus.v2.common.DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(true)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("title").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).description("文档标题")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("text").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(8000).enableAnalyzer(true).description("文档原文")
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
                    .fieldName("visibility").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("访问控制(部门/角色/用户)")
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
                    .fieldName("batchNo").dataType(io.milvus.v2.common.DataType.Int32).maxLength(255).description("批次/LOT")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("metedate").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).isNullable(true).description("扩展元素")
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

            List<IndexParam> indexParams = new ArrayList<>();
            indexParams.add(indexParamForTextDense);
            indexParams.add(indexParamForTextSparse);
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            client.createCollection(createCollectionReq);
        }
    }


    // 分批次插入数据
    private void insertJiuqianDataInBatches(MilvusServiceClient client,
                                            List<BiJiuqianRecordMilvus> records,
                                            String collectionName) throws Exception {
        int total = records.size();
        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            List<BiJiuqianRecordMilvus> batch = records.subList(i, end);

            List<Long> ids = new ArrayList<>(batch.size());
            List<String> months = new ArrayList<>(batch.size());
            List<String> titles = new ArrayList<>(batch.size());
            List<Boolean> contentTypes = new ArrayList<>(batch.size());
            List<String> texts = new ArrayList<>(batch.size());
            List<List<Float>> vectors = new ArrayList<>(batch.size());

            for (BiJiuqianRecordMilvus record : batch) {
                ids.add(record.id);
                months.add(record.month);
                titles.add(record.title);
                contentTypes.add(record.contentType);
                texts.add(record.text);
                vectors.add(record.vector);
            }

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(Arrays.asList(
                            new InsertParam.Field("id", ids),
                            new InsertParam.Field("month", months),
                            new InsertParam.Field("title", titles),
                            new InsertParam.Field("content_type", contentTypes),
                            new InsertParam.Field("text", texts),
                            new InsertParam.Field("vector", vectors)
                    ))
                    .build();

            try {
                R<MutationResult> response = client.insert(insertParam);
                if (response.getStatus() != R.Status.Success.getCode()) {
                    System.err.println("插入失败批次: " + (i / BATCH_SIZE)
                            + ", 原因: " + response.getMessage());
                } else {
                    System.out.printf("成功插入 %d-%d/%d 条记录%n", i, end, total);
                }
            } catch (Exception e) {
                System.err.println("插入异常: " + e.getMessage());
            }
        }
    }

    private void insertPdfDataInBatches(MilvusClientV2 client,
                                        List<PdfRequest> records,
                                        String collectionName) throws Exception {
        int total = records.size();
        List<JsonObject> data = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            PdfRequest request = records.get(i);
            JsonObject jsonObject = new JsonObject();
            Gson gson = new Gson();
            jsonObject.addProperty("title", request.getTitle());
            jsonObject.addProperty("text", request.getText());
            jsonObject.add("vector", gson.toJsonTree(request.getVector()));
            jsonObject.addProperty("reportType", request.getReportType());
            jsonObject.addProperty("expireDate", request.getExpireDate());
            jsonObject.addProperty("reportDate", request.getReportDate());
            jsonObject.addProperty("source", request.getSource());
            jsonObject.addProperty("productName", request.getProductName());
            jsonObject.addProperty("reportId", request.getReportId());
            jsonObject.addProperty("sourceSystem", request.getSourceSystem());
            jsonObject.addProperty("lang", request.getLang());
            jsonObject.addProperty("ingestDate", request.getIngestDate());
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
    }
}