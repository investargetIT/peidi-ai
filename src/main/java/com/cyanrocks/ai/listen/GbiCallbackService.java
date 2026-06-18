package com.cyanrocks.ai.listen;

import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.dao.entity.AiQueryHistory;
import com.cyanrocks.ai.dao.mapper.AiQueryHistoryMapper;
import com.cyanrocks.ai.listen.model.CardCallbackRequest;
import com.cyanrocks.ai.utils.EmbeddingResourceManager;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.utils.UUIDConverter;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Component
public class GbiCallbackService implements OpenDingTalkCallbackListener<CardCallbackRequest, JSONObject> {

    @Autowired
    private AiQueryHistoryMapper aiQueryHistoryMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Value("${milvus.uri}")
    private String milvusUri;
    @Autowired
    private EmbeddingResourceManager embeddingResourceManager;
    @Autowired
    private MilvusUtils milvusUtils;

    private static final String REDIS_KEY = "ding:gbiListen:";

    /**
     * Handle a DingTalk card callback to create or update an AI query history record and manage related Redis and Milvus state.
     *
     * <p>Depending on the callback's `cardPrivateData.actionIds`, this method either starts a new conversation (creates a new
     * AiQueryHistory and clears a Redis key) or updates an existing AiQueryHistory (marking acceptance/rejection, inserting
     * or deleting a Milvus record, and persisting the change).</p>
     *
     * @param request the DingTalk card callback request; expected to contain `cardPrivateData.actionIds` and, for updates,
     *                `cardPrivateData.params.id`
     * @return a new empty {@code JSONObject}
     */
    @Override
    public JSONObject execute(CardCallbackRequest request) {
        String actionIds = JSONObject.parseObject(request.getContent()).getJSONObject("cardPrivateData").getString("actionIds");
        System.out.println("receive call back request, " + actionIds);
        if ("[\"1\"]".equals(actionIds)){
            //开启新对话
            AiQueryHistory aiQueryHistory = new AiQueryHistory();
            aiQueryHistory.setUserId(request.getUserId());
            aiQueryHistory.setIdType("dingId");
            aiQueryHistory.setCreateAt(LocalDateTime.now());
            aiQueryHistory.setSource("问数");
            aiQueryHistoryMapper.insert(aiQueryHistory);

            String redisKey = REDIS_KEY + request.getUserId();
            stringRedisTemplate.delete(redisKey);
        }else{
            String id = JSONObject.parseObject(request.getContent()).getJSONObject("cardPrivateData").getJSONObject("params")
                    .getString("id");
            AiQueryHistory aiQueryHistory = aiQueryHistoryMapper.selectById(Long.valueOf(id));
            if (null == aiQueryHistory){
                return new JSONObject();
            }
            if ("[\"2\"]".equals(actionIds)){
                //认可
                aiQueryHistory.setAccept(true);
                aiQueryHistory.setMilvusId(processMilvus(aiQueryHistory,"query_accept").toString());
                aiQueryHistoryMapper.updateById(aiQueryHistory);
            }else if ("[\"3\"]".equals(actionIds)){
                //不认可
                if (StringUtils.isNotEmpty(aiQueryHistory.getMilvusId())){
                    //删掉历史参考
                    milvusUtils.deleteMilvusById(aiQueryHistory.getMilvusId(),"query_accept");
                }
                aiQueryHistory.setAccept(false);
                aiQueryHistory.setMilvusId("");
                aiQueryHistoryMapper.updateById(aiQueryHistory);

            }
        }
        return new JSONObject();

    }

    /**
     * Inserts a single record derived from the given query history into the specified Milvus collection and returns the record's generated numeric id.
     *
     * The inserted record contains the query rewrite, result, source, and an embedding vector produced by the embedding resource manager. If an error occurs while writing or closing the Milvus client, the method logs the error and still returns the generated id.
     *
     * @param queryHistory  the AI query history whose data will be embedded and inserted
     * @param collectionName the name of the Milvus collection to insert the record into
     * @return the generated numeric id for the inserted Milvus record (returned even if the insert operation fails)
     */
    public Long processMilvus(AiQueryHistory queryHistory, String collectionName) {
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
