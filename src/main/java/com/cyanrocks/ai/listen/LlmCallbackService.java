package com.cyanrocks.ai.listen;

import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.dao.entity.AiQueryHistory;
import com.cyanrocks.ai.dao.mapper.AiQueryHistoryMapper;
import com.cyanrocks.ai.listen.model.CardCallbackRequest;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@Component
public class LlmCallbackService implements OpenDingTalkCallbackListener<CardCallbackRequest, JSONObject> {

    @Autowired
    private AiQueryHistoryMapper aiQueryHistoryMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_KEY = "ding:llmListen:";

    @Override
    public JSONObject execute(CardCallbackRequest request) {
        System.out.println("receive call back request, " + request);
        AiQueryHistory aiQueryHistory = new AiQueryHistory();
        aiQueryHistory.setUserId(request.getUserId());
        aiQueryHistory.setIdType("dingId");
        aiQueryHistory.setCreateAt(LocalDateTime.now());
        aiQueryHistory.setSource("问问");
        aiQueryHistoryMapper.insert(aiQueryHistory);

        String redisKey = REDIS_KEY + request.getUserId();
        stringRedisTemplate.delete(redisKey);
        //根据自身业务需求，变更卡片内容，返回response
        return new JSONObject();

    }
}
