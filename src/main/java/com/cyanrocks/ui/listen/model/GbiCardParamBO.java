package com.cyanrocks.ui.listen.model;

import com.alibaba.fastjson.JSONObject;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class GbiCardParamBO {

    private String cardInstanceId;
    private JSONObject updateCardData;
    private JSONObject updateOptions;

    private String contentKey;
    private StringBuilder contentValue;

    private String outTrackId;
    private String content;
    private Map<String, String> cardDataCardParamMap;

}
