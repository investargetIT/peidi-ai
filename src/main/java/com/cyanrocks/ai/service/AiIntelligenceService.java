package com.cyanrocks.ai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyanrocks.ai.dao.entity.*;
import com.cyanrocks.ai.dao.mapper.AiEnumMapper;
import com.cyanrocks.ai.dao.mapper.AiIntelligenceProductMapper;
import com.cyanrocks.ai.dao.mapper.BiRedditMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.utils.AiModelUtils;
import com.cyanrocks.ai.utils.EmbeddingResourceManager;
import com.cyanrocks.ai.utils.SearchSqlUtils;
import com.cyanrocks.ai.utils.UUIDConverter;
import com.cyanrocks.ai.vo.RedditMilvus;
import com.cyanrocks.ai.vo.request.SearchReq;
import com.cyanrocks.ai.vo.request.SortReq;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author wjq
 * @Date 2025/10/31 13:45
 */
@Service
public class AiIntelligenceService extends ServiceImpl<AiIntelligenceProductMapper, AiIntelligenceProduct> {

    @Autowired
    private SearchSqlUtils searchSqlUtils;
    @Autowired
    private AiModelUtils aiModelUtils;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private BiRedditMapper biRedditMapper;
    @Autowired
    private AiEnumMapper aiEnumMapper;

    private static final String REDIS_KEY = "intelligence:wordCloud:";


    public void newIntelligenceProduct(AiIntelligenceProduct aiIntelligenceProduct) {
        if (null != baseMapper.selectOne(Wrappers.<AiIntelligenceProduct>lambdaQuery().eq(AiIntelligenceProduct::getTitle, aiIntelligenceProduct.getTitle()))){
            throw new BusinessException(500,"存在重复标题");
        }
        baseMapper.insert(aiIntelligenceProduct);
    }

    public void deleteIntelligenceProduct(AiIntelligenceProduct aiIntelligenceProduct) {
        baseMapper.deleteById(aiIntelligenceProduct.getId());
    }

    public IPage<AiIntelligenceProduct> getIntelligenceProductPage(int pageNo, int pageSize, String sortStr, String searchStr) {
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
        return baseMapper.getIntelligenceProductPage(new Page<>(pageNo, pageSize), searchSb, sortSb);
    }

    public String getIntelligenceWordCloud(Boolean refresh){
        if (refresh){
            return refreshIntelligenceWordCloud();
        }else {
            String result = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (StringUtils.isEmpty(result)){
                return refreshIntelligenceWordCloud();
            }else {
                return result;
            }
        }
    }

    private String refreshIntelligenceWordCloud(){
        List<String> wordList = new ArrayList<>();
        int i = 1;
        while (true){
            List<AiIntelligenceProduct> intelligenceProduct = baseMapper.getIntelligenceProductPage(new Page<>(i++, 1000), null, null).getRecords();
            if (CollectionUtil.isEmpty(intelligenceProduct)){
                break;
            }
            wordList.addAll(intelligenceProduct.stream().map(AiIntelligenceProduct::getTitle).collect(Collectors.toList()));
        }
        String result = aiModelUtils.getIntelligenceWordCloud(wordList);
        stringRedisTemplate.opsForValue().set(REDIS_KEY, result);
        stringRedisTemplate.expire(REDIS_KEY, Duration.ofSeconds(60*60*24));
        return result;
    }

    public String parseIntelligenceProduct(MultipartFile file){
        String result = aiModelUtils.parseIntelligenceProduct(file);
        if (StringUtils.isEmpty(result)){
            throw new BusinessException(500,"未成功识别图片");
        }
        return result;
    }

    public List<RedditMilvus> question(String question, String collectionName) throws Exception {
        //纯向量
        List<RedditMilvus> result = new ArrayList<>();
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://121.43.145.161:19530")
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                .eq(AiEnum::getType, "redditTopK"));
        AiEnum redditScore = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                .eq(AiEnum::getType, "redditScore"));
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("nprobe", 10);
        SearchResp searchResp = client.search(io.milvus.v2.service.vector.request.SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(new FloatVec(EmbeddingResourceManager.embedText(question))))
                .annsField("vector")
                .searchParams(searchParams)
                .topK(Integer.parseInt(topK.getValue()))
                .outputFields(Arrays.asList("id", "json"))
                .build());

        if (null != searchResp && CollectionUtil.isNotEmpty(searchResp.getSearchResults())) {
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
            List<SearchResp.SearchResult> scores = searchResults.get(0);
            // 处理最终结果
            for (SearchResp.SearchResult score : scores) {
                Map<String, Object> entity = score.getEntity();
                if (score.getScore() > Double.parseDouble(redditScore.getValue())){
                    RedditMilvus redditMilvus = JSON.toJavaObject(JSON.parseObject((String) entity.get("json")), RedditMilvus.class);
                    redditMilvus.setId(String.valueOf(entity.get("id")));
                    result.add(redditMilvus);
                }else {
                    System.out.println("结束score:" + score.getScore() + " title:"+ entity.get("title"));
                    break;
                }
            }

        } else {
            System.err.println("混合查询失败");
        }
        try {
            client.close();
        } catch (Exception e) {
            // 记录关闭异常，但不抛出
            System.err.println("关闭 Milvus 客户端时发生错误: " + e.getMessage());
        }
        return result;
    }

    public List<RedditMilvus.Review> getReview(String id) {
        BiReddit biReddit = biRedditMapper.selectOne(Wrappers.<BiReddit>lambdaQuery().eq(BiReddit::getMilvusId, id));
        return JSON.parseArray(biReddit.getReviews(), RedditMilvus.Review.class);
    }

    public void test() throws Exception {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://121.43.145.161:19530")
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        Boolean response = client.hasCollection(
                HasCollectionReq.builder()
                        .collectionName("reddit")
                        .build());

        if (!response) {
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .build();
            List<IndexParam> indexParams = new ArrayList<>();

            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(io.milvus.v2.common.DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("title").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(1600).description("标题")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("text").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).enableAnalyzer(true).description("正文")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("json").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).description("完整内容")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("subreddit").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(255).description("文档类型")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("vector").dataType(io.milvus.v2.common.DataType.FloatVector).dimension(1024).description("文档稠密向量")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("sparse").dataType(io.milvus.v2.common.DataType.SparseFloatVector).description("文档稀疏向量")
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

            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName("reddit")
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            client.createCollection(createCollectionReq);
            return;
        }

        List<BiReddit> biRedditList = biRedditMapper.selectAll();
        List<JsonObject> data = new ArrayList<>();
        for (BiReddit biReddit : biRedditList) {
            RedditMilvus redditMilvus = new RedditMilvus();

            JSONArray content = JSON.parseArray(biReddit.getContent());
            JSONObject object1 = (JSONObject) content.get(0);
            JSONObject children1 = (JSONObject) object1.getJSONObject("data").getJSONArray("children").get(0);
            redditMilvus.setSubreddit(children1.getJSONObject("data").getString("subreddit"));
            redditMilvus.setTitle(children1.getJSONObject("data").getString("title"));
            redditMilvus.setSelfText(children1.getJSONObject("data").getString("selftext"));
            redditMilvus.setDowns(children1.getJSONObject("data").getInteger("downs"));
            redditMilvus.setUps(children1.getJSONObject("data").getInteger("ups"));
            redditMilvus.setScore(children1.getJSONObject("data").getDouble("score"));

            JSONObject object2 = (JSONObject) content.get(1);
            List<RedditMilvus.Review> reviews = new ArrayList<>();
            if (null != object2){
                JSONArray childrenList = object2.getJSONObject("data").getJSONArray("children");
                if (CollectionUtil.isNotEmpty(childrenList)){
                    for (int i = 0; i < childrenList.size(); i++) {
                        RedditMilvus.Review review = new RedditMilvus.Review();
                        JSONObject child = (JSONObject) childrenList.get(i);
                        review.setBody(child.getJSONObject("data").getString("body"));
                        review.setDowns(child.getJSONObject("data").getInteger("downs"));
                        review.setUps(child.getJSONObject("data").getInteger("ups"));
                        reviews.add(review);
                    }
                }
            }
            redditMilvus.setReviewCnt(reviews.size());
            //情绪
            if (null == biReddit.getSentiment()){
                String sentimentResult = aiModelUtils.getTextSentiment(redditMilvus.getSelfText());
                if (sentimentResult.contains("positive")){
                    redditMilvus.setSentiment("positive");
                    biReddit.setSentiment("positive");
                }else if (sentimentResult.contains("neutral")){
                    redditMilvus.setSentiment("neutral");
                    biReddit.setSentiment("neutral");
                }else if (sentimentResult.contains("negative")){
                    redditMilvus.setSentiment("negative");
                    biReddit.setSentiment("negative");
                }
                biRedditMapper.updateById(biReddit);
            }else {
                redditMilvus.setSentiment(biReddit.getSentiment());
            }
            if (null == biReddit.getReviews()){
                biReddit.setReviews(JSONObject.toJSONString(reviews));
                biRedditMapper.updateById(biReddit);
            }

            JsonObject jsonObject = new JsonObject();
            Gson gson = new Gson();
            Long id = UUIDConverter.generateSafeUUIDAsLong();
            jsonObject.addProperty("id",id);
            jsonObject.addProperty("title", redditMilvus.getTitle());
            jsonObject.addProperty("text", redditMilvus.getSelfText());
            jsonObject.addProperty("reviews", JSONObject.toJSONString(reviews));
            jsonObject.addProperty("json", JSONObject.toJSONString(redditMilvus));
            jsonObject.addProperty("subreddit", redditMilvus.getSubreddit());
            jsonObject.add("vector", gson.toJsonTree((EmbeddingResourceManager.embedText(JSONObject.toJSONString(redditMilvus)))));
            data.add(jsonObject);
            biReddit.setMilvusId(id.toString());
            biRedditMapper.updateById(biReddit);
        }
        InsertReq insertReq = InsertReq.builder()
                .collectionName("reddit")
                .data(data)
                .build();
        InsertResp insertResp = client.insert(insertReq);
    }
}
