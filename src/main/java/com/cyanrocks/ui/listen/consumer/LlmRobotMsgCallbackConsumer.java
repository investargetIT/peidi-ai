package com.cyanrocks.ui.listen.consumer;

import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ui.listen.manager.GbiCardManager;
import com.cyanrocks.ui.listen.manager.LlmCardManager;
import com.cyanrocks.ui.listen.model.GbiCardParamBO;
import com.cyanrocks.ui.xiyan.GbiManager;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import darabonba.core.exception.TeaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * @Author wjq
 * @Date 2025/5/9 15:19
 */
@Component
public class LlmRobotMsgCallbackConsumer implements OpenDingTalkCallbackListener<JSONObject, JSONObject> {

    @Autowired
    private LlmCardManager cardManager;

    @Value("${dingtalk.llm.client-id}")
    private String clientId;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final String REDIS_KEY = "ding:llmListen:";

    /*
     * @param request
     * @return
     */
    @Override
    public JSONObject execute(JSONObject request) {
        String userId = request.get("senderStaffId").toString();
        String redisKey = REDIS_KEY + userId;

        String content = request.getJSONObject("text").getString("content");
        System.out.println(stringRedisTemplate.opsForValue().get(redisKey));
        if (!Objects.equals(stringRedisTemplate.getExpire(redisKey), (long) -2)&& content.equals(stringRedisTemplate.opsForValue().get(redisKey))){
            //10分钟内重复请求
            return new JSONObject();
        }
        //防止频繁请求相同的问题
        stringRedisTemplate.opsForValue().set(redisKey, content);
        stringRedisTemplate.expire(redisKey, Duration.ofSeconds(600));
        System.out.println("receive bot message from user="+userId+", msg="+content);
        String outTrackId = UUID.randomUUID().toString();
        try {
            cardManager.sendCard(outTrackId, clientId, userId);
            Function<GbiCardParamBO, String> streamingFunction = (cardParamBO) -> {
                cardManager.streamUpdate(cardParamBO.getOutTrackId(), cardParamBO.getContent());
                return null;
            };

            Function<GbiCardParamBO, String> updateFunction = (cardParamBO) -> {
                cardManager.updateFunction(cardParamBO.getOutTrackId(),cardParamBO.getCardDataCardParamMap());
                return null;
            };

            GbiCardParamBO cardParamBO = GbiCardParamBO.builder()
                    .outTrackId(outTrackId)
                    .build();
            cardManager.streamCallWithCallback(content, cardParamBO, streamingFunction, updateFunction, userId);
        }  catch (Exception e) {
            stringRedisTemplate.delete(redisKey);
            System.out.println("RobotMsgCallbackConsumer#excute  throw Exception, msg:{} "+ e.getMessage());
        }
        return new JSONObject();
    }
}
