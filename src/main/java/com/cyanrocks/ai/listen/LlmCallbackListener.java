package com.cyanrocks.ai.listen;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmCallbackListener {

    @Value("${dingtalk.llm.client-id}")
    private String clientId;

    @Value("${dingtalk.llm.client-secret}")
    private String clientSecret;

    @Bean(initMethod = "start")
    public OpenDingTalkClient configureStreamClient(@Autowired LlmCallbackService llmCallbackService) throws Exception {
        // init stream client
        return OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(clientId, clientSecret))
                //注册机器人回调
                .registerCallbackListener(DingTalkStreamTopics.CARD_CALLBACK_TOPIC, llmCallbackService).build();
    }
}
