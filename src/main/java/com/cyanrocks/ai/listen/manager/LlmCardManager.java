package com.cyanrocks.ai.listen.manager;

import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.dingtalkcard_1_0.Client;
import com.aliyun.dingtalkcard_1_0.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cyanrocks.ai.dao.entity.User;
import com.cyanrocks.ai.dao.mapper.UserMapper;
import com.cyanrocks.ai.listen.LlmAccessTokenService;
import com.cyanrocks.ai.listen.model.GbiCardParamBO;
import com.cyanrocks.ai.utils.MilvusUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * @Author wjq
 * @Date 2025/5/9 15:19
 */
@Service
public class LlmCardManager {

    public static String TEMPLATE_ID = "2dc8e232-8f8a-4de9-9e3f-98185ddd6e77.schema";

    private final Client client = this.createClient();

    @Autowired
    private LlmAccessTokenService accessTokenService;
    @Autowired
    private MilvusUtils milvusUtils;
    @Autowired
    private UserMapper userMapper;

    public void sendCard(String outTrackId, String robotCode, String openConvId) {
        try {
            CreateAndDeliverHeaders headers
                    = new CreateAndDeliverHeaders();
            headers.xAcsDingtalkAccessToken = accessTokenService.getAccessToken();

            Map<String, String> cardDataMap = new HashMap<>();
            cardDataMap.put("config", JSON.toJSONString(new JSONObject().fluentPut("autoLayout", true)));

            CreateAndDeliverRequest.CreateAndDeliverRequestCardData cardData =
                    new CreateAndDeliverRequest.CreateAndDeliverRequestCardData();
            cardData.setCardParamMap(cardDataMap);

            CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenSpaceModel imRobotOpenSpaceModel =
                    new CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenSpaceModel()
                            .setSupportForward(true);

            CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenDeliverModel imGroupOpenDeliverModel =
                    new CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenDeliverModel()
                            .setRobotCode(robotCode);

            CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenDeliverModel imRobotOpenDeliverModel =
                    new CreateAndDeliverRequest.CreateAndDeliverRequestImRobotOpenDeliverModel()
                            .setSpaceType("IM_ROBOT");

            Map<String, String> lastMsgI18n = new HashMap<>();
            lastMsgI18n.put("ZH_CN", "佩蒂问问正在回复中……");
            lastMsgI18n.put("EN_US", "Assistant is replying...");


            CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenSpaceModel imGroupOpenSpaceModel =
                    new CreateAndDeliverRequest.CreateAndDeliverRequestImGroupOpenSpaceModel()
                            .setLastMessageI18n(lastMsgI18n)
                            .setSupportForward(true);

            CreateAndDeliverRequest request
                    = new CreateAndDeliverRequest()
                    .setOutTrackId(outTrackId)
                    .setCardTemplateId(TEMPLATE_ID)
                    .setCallbackType("STREAM")
                    .setCardData(cardData)
                    .setImGroupOpenSpaceModel(imGroupOpenSpaceModel)
                    .setImRobotOpenSpaceModel(imRobotOpenSpaceModel)
                    .setImRobotOpenDeliverModel(imRobotOpenDeliverModel)
                    .setOpenSpaceId(getGroupOpenSpaceId(openConvId))
                    .setUserIdType(1);
            try {
                CreateAndDeliverResponse resp = client.createAndDeliverWithOptions(request, headers,
                        new RuntimeOptions());
                System.out.println("CardManager#sendCard get resp:"+ JSON.toJSONString(resp));
            }catch (Exception e) {
                if (e.getMessage().contains("不合法的access_token")){
                    accessTokenService.refreshAccessToken();
                    CreateAndDeliverResponse resp = client.createAndDeliverWithOptions(request, headers,
                            new RuntimeOptions());
                    System.out.println("CardManager#sendCard get resp:"+ JSON.toJSONString(resp));
                }
                System.out.println("CardManager#sendCard get exception, msg:"+e.getMessage());
            }


        } catch (Exception e) {
            System.out.println("CardManager#sendCard get exception, msg:"+e.getMessage());
        }
    }

    public void updateFunction(String outTrackId, Map<String, String> cardDataCardParamMap) {
        try {
            UpdateCardRequest.UpdateCardRequestCardUpdateOptions cardUpdateOptions = new UpdateCardRequest.UpdateCardRequestCardUpdateOptions()
                    .setUpdateCardDataByKey(true)
                    .setUpdatePrivateDataByKey(false);
            UpdateCardRequest.UpdateCardRequestCardData cardData = new UpdateCardRequest.UpdateCardRequestCardData()
                    .setCardParamMap(cardDataCardParamMap);
            UpdateCardRequest updateCardRequest = new UpdateCardRequest()
                    .setOutTrackId(outTrackId)
                    .setCardData(cardData)
                    .setCardUpdateOptions(cardUpdateOptions)
                    .setUserIdType(1);
            UpdateCardHeaders updateCardHeaders = new UpdateCardHeaders();
            updateCardHeaders.xAcsDingtalkAccessToken = accessTokenService.getAccessToken();
            client.updateCardWithOptions(updateCardRequest, updateCardHeaders, new RuntimeOptions());
        } catch (Exception e) {
            System.out.println("CardManager#streamUpdate get exception, msg:"+ e.getMessage());
        }

    }

    public void streamUpdate(String outTrackId, String content) {
        try {
            System.out.println("开始流式"+ LocalDateTime.now());
            StreamingUpdateHeaders headers = new StreamingUpdateHeaders();
            headers.xAcsDingtalkAccessToken = accessTokenService.getAccessToken();
            StreamingUpdateRequest request =
                    new StreamingUpdateRequest().setOutTrackId(outTrackId).setGuid(UUID.randomUUID().toString()).setKey(
                            "content").setContent(content).setIsFull(false).setIsFinalize(false);
            client.streamingUpdateWithOptions(request, headers, new RuntimeOptions());
            System.out.println("流式结束"+ LocalDateTime.now());
        } catch (Exception e) {
            System.out.println("CardManager#streamUpdate get exception, msg:"+ e.getMessage());
        }

    }

    public void finishAiCard(String outTrackId, String content) {
        try {
            StreamingUpdateHeaders headers = new StreamingUpdateHeaders();
            headers.xAcsDingtalkAccessToken = accessTokenService.getAccessToken();
            StreamingUpdateRequest request =
                    new StreamingUpdateRequest().setOutTrackId(outTrackId).setGuid(UUID.randomUUID().toString()).setKey(
                            "content").setContent(content).setIsFull(false).setIsFinalize(true);
            client.streamingUpdateWithOptions(request, headers, new RuntimeOptions());
        } catch (Exception e) {
            System.out.println("CardManager#finishAiCard get exception = " + e);
        }
    }

    protected Client createClient() {
        try {
            Config config = new Config();
            config.protocol = "https";
            config.regionId = "central";
            config.endpoint = "api.dingtalk.com";
            return new Client(config);
        } catch (Exception e) {
            System.out.println("createClient get excpetion, msg:"+ e.getMessage());
        }
        return null;
    }

    protected String getGroupOpenSpaceId(String openConvId) {
        return "dtv1.card//IM_ROBOT." + openConvId;
    }

    public void streamCallWithCallback(String query, GbiCardParamBO cardParamBO, Function<GbiCardParamBO, String> streamingFunction, Function<GbiCardParamBO, String> updateFunction, String dingId) throws Exception {
        Map<String, String> result = milvusUtils.semanticSearch2(query,"pdf_markdown",dingId);
        //流式输出文本结果
        cardParamBO.setContent("### 执行结果\n");
        streamingFunction.apply(cardParamBO);
        if (StringUtils.isEmpty(result.get("text"))){
            if (StringUtils.isEmpty(result.get("title"))){
                cardParamBO.setContent("");
            }else {
                cardParamBO.setContent(result.get("title"));
            }
        }else {
            cardParamBO.setContent(result.get("text"));
        }
        streamingFunction.apply(cardParamBO);
        //输出参考文档
        String title = result.get("title");
        String source = result.get("source");
        Map<String, String> cardDataCardParamMap = new HashMap<>();
        JSONArray resources = new JSONArray();
        if (StringUtils.isNotEmpty(title) && StringUtils.isNotEmpty(source)){
            String[] titles = title.split(",");
            String[] sources = source.split(",");
            for (int i = 0; i < titles.length; i++) {
                JSONObject resource = new JSONObject();
                resource.put("name", titles[i]);
                resource.put("url", "https://api.peidigroup.cn/ai/common/gbiDownload?objectName="+ URLUtil.encode(sources[i]));
                System.out.println("url:"+resource.get("url"));
                resources.add(resource);
            }
        }
        cardDataCardParamMap.put("resources",resources.toJSONString());
        cardParamBO.setCardDataCardParamMap(cardDataCardParamMap);
        updateFunction.apply(cardParamBO);
        this.finishAiCard(cardParamBO.getOutTrackId(),"");
    }
}
