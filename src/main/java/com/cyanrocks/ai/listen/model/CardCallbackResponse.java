package com.cyanrocks.ai.listen.model;

import java.util.Map;

/**
 * @Author wjq
 * @Date 2025/10/28 16:50
 */
public class CardCallbackResponse {
    //卡片公有数据
    private CardDataDTO cardData;

    //触发回调用户的私有数据
    private CardDataDTO userPrivateData;

    public static class CardDataDTO{

        //卡片参数
        private Map<String, String> cardParamMap;
    }
}
