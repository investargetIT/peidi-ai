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
public class GbiCallbackListener {

    @Value("${dingtalk.app.client-id}")
    private String clientId;

    @Value("${dingtalk.app.client-secret}")
    private String clientSecret;

    /**
     * Create and configure an OpenDingTalkClient for DingTalk stream callbacks.
     *
     * <p>The returned client is configured with app credentials and registers the provided
     * GbiCallbackService as the listener for the CARD_CALLBACK_TOPIC.</p>
     *
     * @return a configured OpenDingTalkClient that listens for CARD_CALLBACK_TOPIC callbacks
     * @throws Exception if the client cannot be built or initialized
     */
    @Bean(initMethod = "start")
    public OpenDingTalkClient configureStreamClientGbi(@Autowired GbiCallbackService gbiCallbackService) throws Exception {
        // init stream client
        return OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(clientId, clientSecret))
                //注册机器人回调
                .registerCallbackListener(DingTalkStreamTopics.CARD_CALLBACK_TOPIC, gbiCallbackService).build();
    }
}
