package com.cyanrocks.ui.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cyanrocks.ui.utils.http.HttpClientService;
import com.cyanrocks.ui.utils.http.HttpResponseContent;
import com.cyanrocks.ui.utils.http.HttpTimeoutConfig;
import com.cyanrocks.ui.utils.http.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author wjq
 * @Date 2025/5/21 15:54
 */
@Component
public class RerankClient {

    @Autowired
    private HttpClientService httpClientService;


    private static final String API_KEY = "";
    private static final String API_URL = "https://api.jina.ai/v1/rerank";

    public List<Double> rerank(String query, List<String> documents) throws IOException {

        JSONObject jsonObject = new JSONObject();
        jsonObject.set("query", query);
        jsonObject.set("model", "jina-reranker-v2-base-multilingual");
        jsonObject.set("documents", documents);
        HttpResponseContent content;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer jina_a8b840971a994b00a1ab31b9553e3f06u1BjAWBHoNjzHRdPldZbYioG6ITx");
        try {
            content = httpClientService.doPost(API_URL, headers, JSONUtil.toJsonStr(jsonObject),
                    HttpUtils.initHttpClientContext(null, new HttpTimeoutConfig(300000)));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
        System.out.println(content.getContent());
        JSONObject contentJson = JSONUtil.parseObj(content.getContent());
        JSONArray results = contentJson.getJSONArray("results");
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            scores.add(results.getJSONObject(i).getDouble("relevance_score"));
        }
        return scores;
    }

}
