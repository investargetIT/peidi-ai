package com.cyanrocks.ai.xiyan;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.gateway.pop.Configuration;
import com.aliyun.sdk.gateway.pop.auth.SignatureVersion;
import com.aliyun.sdk.service.dataanalysisgbi20240823.AsyncClient;
import com.aliyun.sdk.service.dataanalysisgbi20240823.models.RunDataAnalysisRequest;
import com.aliyun.sdk.service.dataanalysisgbi20240823.models.RunDataAnalysisResponseBody;
import com.cyanrocks.ai.listen.manager.GbiCardManager;
import com.cyanrocks.ai.listen.model.GbiChatBIMsgType;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.xiyan.models.EventData;
import com.cyanrocks.ai.xiyan.models.VisualizationBO;
import com.cyanrocks.ai.listen.model.GbiCardParamBO;
import com.google.gson.Gson;
import darabonba.core.ResponseIterable;
import darabonba.core.ResponseIterator;
import darabonba.core.client.ClientOverrideConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @Author wjq
 * @Date 2025/5/9 15:23
 */
@Service
public class GbiManager {

    @Autowired
    private GbiCardManager cardManager;
    @Autowired
    private MilvusUtils milvusUtils;

    private final AsyncClient client;


    public GbiManager() {
        StaticCredentialProvider provider = StaticCredentialProvider.create(Credential.builder().accessKeyId("").accessKeySecret("").build());
        AsyncClient client = AsyncClient.builder().region("cn-beijing").credentialsProvider(provider).serviceConfiguration(Configuration.create().setSignatureVersion(SignatureVersion.V3)).overrideConfiguration(ClientOverrideConfiguration.create().setProtocol("HTTPS").setEndpointOverride("dataanalysisgbi.cn-beijing.aliyuncs.com")).build();
        this.client = client;
    }

    public void streamCallWithCallback(String query, GbiCardParamBO cardParamBO, Function<GbiCardParamBO, String> streamingFunction, Function<GbiCardParamBO, String> updateFunction, StringRedisTemplate stringRedisTemplate, String redisKey) throws InterruptedException {
        JSONObject agentCtrlParam = new JSONObject();
        agentCtrlParam.put("enableChatMode", true);
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        RunDataAnalysisRequest request;
        String sessionId = "";
        request = RunDataAnalysisRequest.builder().workspaceId("llm-qjbjk5qlcm9ta1di").query(query)
                .generateSqlOnly(false).agentCtrlParams(agentCtrlParam).build();
        if (redisValue == null) {
            request = RunDataAnalysisRequest.builder().workspaceId("llm-qjbjk5qlcm9ta1di").query(query)
                    .generateSqlOnly(false).agentCtrlParams(agentCtrlParam).build();
        }else {
            sessionId = redisValue.split("##")[0];
            request = RunDataAnalysisRequest.builder().workspaceId("llm-qjbjk5qlcm9ta1di").query(query)
                    .generateSqlOnly(false).agentCtrlParams(agentCtrlParam).sessionId(sessionId).build();
        }
        ResponseIterable<RunDataAnalysisResponseBody> x = client.runDataAnalysisWithResponseIterable(request);
        ResponseIterator<RunDataAnalysisResponseBody> iterator = x.iterator();
        boolean hasResult = false;
        String noResultText = "";
        while (iterator.hasNext()) {
            RunDataAnalysisResponseBody event = iterator.next();
            String jsonStr = JSON.toJSONString(event.getData());
            System.out.println(jsonStr);
            EventData eventData = JSON.parseObject(jsonStr, EventData.class);
            if (event.getData().getEvent().equals(GbiChatBIMsgType.REWRITE.getCode())){
                cardParamBO.setContent("### 执行过程\n");
                streamingFunction.apply(cardParamBO);
                cardParamBO.setContent("- 问题改写：" + eventData.getRewrite() + "\n");
                streamingFunction.apply(cardParamBO);
            }
            if (event.getData().getEvent().equals(GbiChatBIMsgType.SELECT.getCode())){
                cardParamBO.setContent("- 数据表选取：" + String.join(",", eventData.getSelector()) + "\n");
                streamingFunction.apply(cardParamBO);
            }
            if (event.getData().getEvent().equals(GbiChatBIMsgType.EVIDENCE.getCode())){
                cardParamBO.setContent("- 本次推理参考业务信息：" + eventData.getEvidence() + "\n");
                streamingFunction.apply(cardParamBO);
            }
            if (event.getData().getEvent().equals(GbiChatBIMsgType.RESULT.getCode())) {
                hasResult = true;
                System.out.println("==== gbi event: " + new Gson().toJson(event.getData()));
                cardParamBO.setContent("### 执行结果\n");
                streamingFunction.apply(cardParamBO);
                cardParamBO.setContent(eventData.getVisualization() == null ? "" : eventData.getVisualization().getText());
                streamingFunction.apply(cardParamBO);
                if (eventData.getSqlData() != null) {
                    table(cardParamBO, updateFunction, eventData, query);
                }
                if (eventData.getVisualization() != null && eventData.getVisualization().getData() != null) {
                    chart(cardParamBO, updateFunction, eventData, query);
                }
                sessionId = eventData.getSessionId();
                cardManager.finishAiCard(cardParamBO.getOutTrackId(),"");
            }
            if (event.getData().getEvent().equals(GbiChatBIMsgType.CHAT.getCode())){
                noResultText = event.getData().getChat().getText();
                sessionId = event.getData().getSessionId();
            }

        }
        if (!hasResult) {
            cardManager.finishAiCard(cardParamBO.getOutTrackId(),noResultText);
        }
        stringRedisTemplate.opsForValue().set(redisKey, sessionId+"##"+query);
    }

    public void streamCallWithCallbackNew(String query, GbiCardParamBO cardParamBO, Function<GbiCardParamBO, String> streamingFunction, Function<GbiCardParamBO, String> updateFunction, StringRedisTemplate stringRedisTemplate, String redisKey,String userId) throws Exception {
        Map<String, String> result = milvusUtils.gbiSearch(query, "gbi_table","gbi_explain", userId);
        cardParamBO.setContent("### 执行过程\n");
        streamingFunction.apply(cardParamBO);
        cardParamBO.setContent("- 问题重写：" + result.get("rewriteQuery") + "\n");
        streamingFunction.apply(cardParamBO);
        cardParamBO.setContent("- 查询SQL生成， 生成SQL查询语句为：" + result.get("sql") + "\n");
        streamingFunction.apply(cardParamBO);

        cardParamBO.setContent("### 执行结果\n");
        streamingFunction.apply(cardParamBO);
        cardParamBO.setContent(result.get("result") + "\n");
        streamingFunction.apply(cardParamBO);
        cardManager.finishAiCard(cardParamBO.getOutTrackId(),"");
        stringRedisTemplate.opsForValue().set(redisKey, query);
    }

    private void table(GbiCardParamBO cardParamBO, Function<GbiCardParamBO, String> updateCardFunction, EventData eventData, String query) {
        JSONArray metaArr = new JSONArray();

        //表头
        List<String> columns =  eventData.getSqlData().getColumn();
        Double weight = 100.0 / columns.size();
        columns.forEach(column ->{
            JSONObject meta = new JSONObject();
            meta.put("aliasName",column);
            meta.put("dataType","STRING");
            meta.put("alias",column);
            meta.put("weight",weight);
            metaArr.add(meta);
        });

        Map<String, String> cardDataCardParamMap = new HashMap<>();
        JSONObject table = new JSONObject();
        table.put("meta", metaArr);
        table.put("data", eventData.getSqlData().getData());
        cardDataCardParamMap.put("table",table.toJSONString());
        cardDataCardParamMap.put("tableCnt", String.valueOf(columns.size()));
        cardParamBO.setCardDataCardParamMap(cardDataCardParamMap);
        updateCardFunction.apply(cardParamBO);
    }


    private void chart(GbiCardParamBO cardParamBO, Function<GbiCardParamBO, String> updateCardFunction, EventData eventData, String query) {
        JSONArray dataArr = new JSONArray();
        //饼图
//        String x = eventData.getVisualization().getData().getXAxis().get(0);
//        String y = eventData.getVisualization().getData().getYAxis().get(0);
//        eventData.getSqlData().getData().stream().forEach(a -> {
//            dataArr.fluentAdd(createChartData(a.get(x), a.get(y), null));
//        });
//
//        Map<String, String> cardDataCardParamMap = new HashMap<>();
//        cardDataCardParamMap.put("query",query);
//        JSONObject chart = new JSONObject();
//        chart.put("type", "pieChart");
//        JSONObject config = new JSONObject();
//        List<Long> padding = new TreeList<>();
//        padding.add(20L);
//        padding.add(30L);
//        padding.add(20L);
//        padding.add(30L);
//        config.put("padding", padding);
//        chart.put("config", config);
//        chart.put("data", dataArr);
        //柱状图
        String x = eventData.getVisualization().getData().getXAxis().get(0);
        eventData.getVisualization().getData().getYAxis().forEach( y -> {
            eventData.getSqlData().getData().stream().forEach(a -> {
                dataArr.fluentAdd(createChartData(a.get(x), a.get(y), y));
            });
        });
        Map<String, String> cardDataCardParamMap = new HashMap<>();
        cardDataCardParamMap.put("query",query);
        JSONObject chart = new JSONObject();
        chart.put("type", "histogram");
        JSONObject config = new JSONObject();
        chart.put("config", config);
        chart.put("data", dataArr);
        cardDataCardParamMap.put("chart",chart.toJSONString());
        cardParamBO.setCardDataCardParamMap(cardDataCardParamMap);
        updateCardFunction.apply(cardParamBO);
    }

    /**
     * 析言图表类型->钉钉卡片图表类型
     */
    private String getChartType(VisualizationBO visualization) {
        if (visualization.getData().getPlotType().equals("bar")) {
            return "histogram";
        }
        if (visualization.getData().getPlotType().equals("pie")) {
            return "pieChart";
        }
        if (visualization.getData().getPlotType().equals("line")) {
            return "lineChart";
        }
        return "lineChart";
    }

    private JSONObject createChartData(String x, String y, String type) {
        JSONObject chartData;
        double yValue = 0.0; // 默认值
        if (y != null && !y.isEmpty()) { // 检查非空字符串
            try {
                yValue = Double.parseDouble(y); // 尝试解析为 double
            } catch (NumberFormatException ignored) {
                // 解析失败时保持默认值 0
            }
        }
        chartData = new JSONObject().fluentPut("x", x).fluentPut("y", yValue);
        if (type != null) {
            chartData.put("type", type);
        }
        return chartData;
    }

}
