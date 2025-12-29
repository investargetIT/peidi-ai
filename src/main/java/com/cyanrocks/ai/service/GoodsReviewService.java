package com.cyanrocks.ai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyanrocks.ai.dao.entity.AiEnum;
import com.cyanrocks.ai.dao.entity.AiIntelligenceProduct;
import com.cyanrocks.ai.dao.entity.BiGoodsReview;
import com.cyanrocks.ai.dao.entity.BiReddit;
import com.cyanrocks.ai.dao.mapper.AiEnumMapper;
import com.cyanrocks.ai.dao.mapper.BiGoodsReviewMapper;
import com.cyanrocks.ai.dao.mapper.BiRedditMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.utils.AiModelUtils;
import com.cyanrocks.ai.utils.EmbeddingResourceManager;
import com.cyanrocks.ai.utils.SearchSqlUtils;
import com.cyanrocks.ai.utils.UUIDConverter;
import com.cyanrocks.ai.vo.GoodsReviewMilvus;
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
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class GoodsReviewService extends ServiceImpl<BiGoodsReviewMapper, BiGoodsReview> {

    @Autowired
    private AiModelUtils aiModelUtils;
    @Autowired
    private AiEnumMapper aiEnumMapper;
    @Autowired
    private EmbeddingResourceManager embeddingResourceManager;

    @Value("${milvus.uri}")
    private String milvusUri;

    public GoodsReviewMilvus question(String question, String product, String compareProduct, String collectionName) {
        //纯向量
        GoodsReviewMilvus result = new GoodsReviewMilvus();
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = null;
        try {
            client = new MilvusClientV2(config);
            result.setGoodsReviews(this.questionProduct(client,product.split("&&")[0],product.split("&&")[1],collectionName,question));
            if (null != compareProduct){
                result.setCompareGoodsReviews(this.questionProduct(client,compareProduct.split("&&")[0],compareProduct.split("&&")[1],collectionName,question));
            }
        }catch (Exception e){
            System.out.println("查询向量数据库失败" + e.getMessage());
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

        //处理词云
        if (CollectionUtil.isNotEmpty(result.getGoodsReviews())){
            result.setGoodsWordCloud(aiModelUtils.getIntelligenceWordCloud(
                    result.getGoodsReviews().stream().map(BiGoodsReview::getGoodsReview).collect(Collectors.toList())));
        }
        if (CollectionUtil.isNotEmpty(result.getCompareGoodsReviews())){
            result.setCompareGoodsWordCloud(aiModelUtils.getIntelligenceWordCloud(
                    result.getCompareGoodsReviews().stream().map(BiGoodsReview::getGoodsReview).collect(Collectors.toList())));
        }

        //获取竞品分析
        if (CollectionUtil.isNotEmpty(result.getGoodsReviews()) && CollectionUtil.isNotEmpty(result.getCompareGoodsReviews())){
            result.setCompareSummary(aiModelUtils.compareProductReviews(
                    result.getGoodsReviews().stream().map(BiGoodsReview::getGoodsReview).collect(Collectors.toList())
                    , result.getCompareGoodsReviews().stream().map(BiGoodsReview::getGoodsReview).collect(Collectors.toList())));
        }
        return result;
    }

    private List<BiGoodsReview> questionProduct(MilvusClientV2 client, String shopName, String goodsType, String collectionName, String question){
        List<BiGoodsReview> result = new ArrayList<>();
        AiEnum topK = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                .eq(AiEnum::getType, "goodsReviewTopK"));
        AiEnum redditScore = aiEnumMapper.selectOne(Wrappers.<AiEnum>lambdaQuery()
                .eq(AiEnum::getType, "goodsReviewScore"));
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("nprobe", 10);
        SearchResp searchResp = client.search(io.milvus.v2.service.vector.request.SearchReq.builder()
                .collectionName(collectionName)
                .filter("goodsType == \""+goodsType+"\" and shopName == \"" + shopName + "\"")
                .data(Collections.singletonList(new FloatVec(embeddingResourceManager.embedText(question))))
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
                if (score.getScore() > Double.parseDouble(redditScore.getValue())) {
                    BiGoodsReview biGoodsReview = JSON.toJavaObject(JSON.parseObject((String) entity.get("json")), BiGoodsReview.class);
                    result.add(biGoodsReview);
                } else {
                    System.out.println("结束score:" + score.getScore());
                    break;
                }
            }
        } else {
            System.err.println("查询向量数据库失败");
        }
        return result;
    }

    public void test() throws Exception {
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusUri)
                .build();
        MilvusClientV2 client = new MilvusClientV2(config);
        Boolean response = client.hasCollection(
                HasCollectionReq.builder()
                        .collectionName("goods_review")
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
                    .fieldName("channel").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(50).description("渠道")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("customerName").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(100).description("客户名称")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("reviewDate").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(50).description("评论日期")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("goodsName").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(100).description("商品名称")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("goodsId").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(50).description("商品id")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("goodsType").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(50).description("商品类型")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("goodsReview").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).description("商品评论")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("goodsImage").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).isNullable(true).description("商品图片")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("shopName").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(100).description("店铺名称")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("sentiment").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(50).description("情绪")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("json").dataType(io.milvus.v2.common.DataType.VarChar).maxLength(16000).enableAnalyzer(true).description("完整内容")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("vector").dataType(io.milvus.v2.common.DataType.FloatVector).dimension(1024).description("文档稠密向量")
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("sparse").dataType(io.milvus.v2.common.DataType.SparseFloatVector).description("文档稀疏向量")
                    .build());
            schema.addFunction(CreateCollectionReq.Function.builder()
                    .functionType(io.milvus.common.clientenum.FunctionType.BM25)
                    .name("json_bm25_emb")
                    .inputFieldNames(Collections.singletonList("json"))
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
                    .collectionName("goods_review")
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            client.createCollection(createCollectionReq);
            return;
        }

        List<BiGoodsReview> recordList = baseMapper.selectAl1l();
        List<JsonObject> data = new ArrayList<>();
        for (BiGoodsReview record : recordList) {
            if (StringUtils.isEmpty(record.getGoodsReview())){
                continue;
            }
            //情绪
            String sentimentResult = aiModelUtils.getTextSentiment(record.getGoodsReview());
            if (sentimentResult.contains("positive")) {
                record.setSentiment("positive");
            } else if (sentimentResult.contains("neutral")) {
                record.setSentiment("neutral");
            } else if (sentimentResult.contains("negative")) {
                record.setSentiment("negative");
            }
            if (null == record.getSentiment()){
                continue;
            }

            JsonObject jsonObject = new JsonObject();
            Gson gson = new Gson();
            Long id = UUIDConverter.generateSafeUUIDAsLong();
            jsonObject.addProperty("id", id);
            jsonObject.addProperty("channel", record.getChannel());
            jsonObject.addProperty("customerName", record.getCustomerName());
            jsonObject.addProperty("reviewDate", record.getReviewDate().toString());
            jsonObject.addProperty("goodsName", record.getGoodsName());
            jsonObject.addProperty("goodsId", record.getGoodsId());
            jsonObject.addProperty("goodsType", record.getGoodsType());
            jsonObject.addProperty("goodsReview", record.getGoodsReview());
            jsonObject.addProperty("goodsImage", record.getGoodsImage());
            jsonObject.addProperty("shopName", record.getShopName());
            jsonObject.addProperty("sentiment", record.getSentiment());
            jsonObject.addProperty("json", JSONObject.toJSONString(record));
            jsonObject.add("vector", gson.toJsonTree((embeddingResourceManager.embedText(record.getGoodsReview()))));
            data.add(jsonObject);
            record.setMilvusId(id.toString());
            baseMapper.insert(record);
        }
        InsertReq insertReq = InsertReq.builder()
                .collectionName("goods_review")
                .data(data)
                .build();
        InsertResp insertResp = client.insert(insertReq);
    }
}
