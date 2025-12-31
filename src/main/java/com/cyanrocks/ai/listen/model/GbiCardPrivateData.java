package com.cyanrocks.ai.listen.model;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class GbiCardPrivateData {
    @JSONField(name = "actionIds")
    private List<String> actionIds;
    @JSONField(name = "params")
    private JSONObject params;
}
