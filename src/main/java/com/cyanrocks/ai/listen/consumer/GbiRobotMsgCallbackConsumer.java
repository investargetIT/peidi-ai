package com.cyanrocks.ai.listen.consumer;

import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.listen.manager.GbiCardManager;
import com.cyanrocks.ai.xiyan.GbiManager;
import com.cyanrocks.ai.listen.model.GbiCardParamBO;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * @Author wjq
 * @Date 2025/5/9 15:19
 */
@Component
public class GbiRobotMsgCallbackConsumer implements OpenDingTalkCallbackListener<JSONObject, JSONObject> {

    @Autowired
    private GbiCardManager cardManager;

    @Autowired
    private GbiManager gbiManager;
    @Value("${dingtalk.app.client-id}")
    private String clientId;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final String REDIS_KEY = "ding:streamCard2:";
    private static final String REDIS_KEY1 = "ding:gbiListen:";

    /*
     * @param request
     * @return
     */
    @Override
    public JSONObject execute(JSONObject request) {
        String userId = request.get("senderStaffId").toString();
        String redisKey = REDIS_KEY + userId;
        String redisKey1 = REDIS_KEY1+ userId;
        System.out.println(stringRedisTemplate.getExpire(redisKey));

        String content = request.getJSONObject("text").getString("content");
        String openConvId = request.get("conversationId").toString();
        String robotCode = request.get("robotCode").toString();
        System.out.println(stringRedisTemplate.opsForValue().get(redisKey));
        if (!Objects.equals(stringRedisTemplate.getExpire(redisKey1), (long) -2) && content.equals(stringRedisTemplate.opsForValue().get(redisKey1))){
            //10分钟内重复请求
            return new JSONObject();
        }
        //防止频繁请求相同的问题
        stringRedisTemplate.opsForValue().set(redisKey1, content);
        stringRedisTemplate.expire(redisKey1, Duration.ofSeconds(600));

        System.out.println("receive bot message from user="+userId+", msg="+content);
        String outTrackId = UUID.randomUUID().toString();
        try {
            cardManager.sendCard(outTrackId, clientId, userId);
            Function<GbiCardParamBO, String> streamingFunction = (cardParamBO) -> {
                cardManager.streamUpdate(cardParamBO.getOutTrackId(), cardParamBO.getContent());
                try {
                    Thread.sleep(1000*3);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            };

            Function<GbiCardParamBO, String> updateFunction = (cardParamBO) -> {
                cardManager.updateFunction(cardParamBO.getOutTrackId(),cardParamBO.getCardDataCardParamMap());
                try {
                    Thread.sleep(1000*3);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            };

            GbiCardParamBO cardParamBO = GbiCardParamBO.builder()
                    .outTrackId(outTrackId)
                    .build();
            gbiManager.streamCallWithCallbackNew(content, cardParamBO, streamingFunction, updateFunction, stringRedisTemplate, redisKey,userId);
        } catch (Exception e) {
            stringRedisTemplate.delete(redisKey1);
            System.out.println("RobotMsgCallbackConsumer#excute  throw Exception, msg:{} "+ e.getMessage());
        }
        return new JSONObject();
    }
}
