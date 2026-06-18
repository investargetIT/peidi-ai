package com.cyanrocks.ai.listen;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.dao.entity.AiMilvusPdfMarkdown;
import com.cyanrocks.ai.dao.entity.AiQueryHistory;
import com.cyanrocks.ai.dao.mapper.AiQueryHistoryMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.listen.model.CardCallbackRequest;
import com.cyanrocks.ai.utils.AiModelUtils;
import com.cyanrocks.ai.utils.EmbeddingResourceManager;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.utils.UUIDConverter;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
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
public class LlmCallbackService implements OpenDingTalkCallbackListener<CardCallbackRequest, JSONObject> {

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

    private static final String REDIS_KEY = "ding:llmListen:";

    /**
     * Handle a DingTalk card callback by applying the indicated action to AI query history and related resources.
     *
     * <p>Depending on the callback's action id this method will:
     * <ul>
     *   <li>for the "start new conversation" action: insert a new AiQueryHistory record and remove the user's Redis key;</li>
     *   <li>for the "accept" action: mark the history entry as accepted and create/update its Milvus reference;</li>
     *   <li>for the "reject" action: mark the history entry as not accepted, delete its Milvus reference if present, and clear the stored Milvus id.</li>
     * </ul>
     * </p>
     *
     * @return an empty JSONObject as the callback response payload
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
            aiQueryHistory.setSource("问问");
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
                aiQueryHistory.setMilvusId(milvusUtils.processLlmBackMilvus(aiQueryHistory,"query_accept").toString());
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
}
