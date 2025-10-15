package com.cyanrocks.ui.listen;

import com.cyanrocks.ui.listen.consumer.LlmRobotMsgCallbackConsumer;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class LlmListener {

    @Autowired
    private LlmRobotMsgCallbackConsumer robotMsgCallbackConsumer;

    @Value("${dingtalk.llm.client-id}")
    private String clientId;
    @Value("${dingtalk.llm.client-secret}")
    private String clientSecret;

    @PostConstruct
    public void init() throws Exception {
        // init stream client
        OpenDingTalkClient client = OpenDingTalkStreamClientBuilder
                .custom()
                .credential(new AuthClientCredential(clientId, clientSecret))
                .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, robotMsgCallbackConsumer)
                .build();
        client.start();
    }
}
