package com.cyanrocks.ui.listen.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.dingtalkcard_1_0.Client;
import com.aliyun.dingtalkcard_1_0.models.*;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.Common;
import com.aliyun.teautil.models.RuntimeOptions;
import com.cyanrocks.ui.listen.GbiAccessTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @Author wjq
 * @Date 2025/5/9 15:19
 */
@Service
public class GbiCardManager {

    public static String TEMPLATE_ID = "dad2e8c9-3d5e-48e7-8cbf-83297a704b57.schema";

    private final Client client = this.createClient();

    @Autowired
    private GbiAccessTokenService accessTokenService;

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
            lastMsgI18n.put("ZH_CN", "助理正在回复中……");
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

    public void updateCard(String outTrackId) {
        UpdateCardHeaders updateCardHeaders
                = new UpdateCardHeaders();
        updateCardHeaders.xAcsDingtalkAccessToken = accessTokenService.getAccessToken();
        UpdateCardRequest.UpdateCardRequestCardUpdateOptions cardUpdateOptions
                = new UpdateCardRequest.UpdateCardRequestCardUpdateOptions()
                .setUpdateCardDataByKey(true);

        Map<String, String> cardDataMap = new HashMap<>();
        cardDataMap.put("title", "AI助理回复完毕");
        UpdateCardRequest.UpdateCardRequestCardData cardData
                = new UpdateCardRequest.UpdateCardRequestCardData()
                .setCardParamMap(cardDataMap);
        UpdateCardRequest updateCardRequest
                = new UpdateCardRequest()
                .setOutTrackId(outTrackId)
                .setCardData(cardData)
                .setCardUpdateOptions(cardUpdateOptions)
                .setUserIdType(1);
        try {
            client.updateCardWithOptions(updateCardRequest, updateCardHeaders,
                    new RuntimeOptions());
        } catch (TeaException err) {
            if (!Common.empty(err.code) && !Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                System.out.println("CardManager#updateCard get TeaException, msg:{} "+ err.message);
            }

        } catch (Exception _err) {
            TeaException err = new TeaException(_err.getMessage(), _err);
            if (!Common.empty(err.code) && !Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                System.out.println("CardManager#updateCard get Exception, msg:{} "+ err.message);
            }

        }
    }

    public void updateFunction(String outTrackId, Map<String, String> cardDataCardParamMap) {
        try {
            com.aliyun.dingtalkcard_1_0.models.UpdateCardRequest.UpdateCardRequestCardUpdateOptions cardUpdateOptions = new com.aliyun.dingtalkcard_1_0.models.UpdateCardRequest.UpdateCardRequestCardUpdateOptions()
                    .setUpdateCardDataByKey(true)
                    .setUpdatePrivateDataByKey(false);
            com.aliyun.dingtalkcard_1_0.models.UpdateCardRequest.UpdateCardRequestCardData cardData = new com.aliyun.dingtalkcard_1_0.models.UpdateCardRequest.UpdateCardRequestCardData()
                    .setCardParamMap(cardDataCardParamMap);
            com.aliyun.dingtalkcard_1_0.models.UpdateCardRequest updateCardRequest = new com.aliyun.dingtalkcard_1_0.models.UpdateCardRequest()
                    .setOutTrackId(outTrackId)
                    .setCardData(cardData)
                    .setCardUpdateOptions(cardUpdateOptions)
                    .setUserIdType(1);
            com.aliyun.dingtalkcard_1_0.models.UpdateCardHeaders updateCardHeaders = new com.aliyun.dingtalkcard_1_0.models.UpdateCardHeaders();
            updateCardHeaders.xAcsDingtalkAccessToken = accessTokenService.getAccessToken();
            client.updateCardWithOptions(updateCardRequest, updateCardHeaders, new com.aliyun.teautil.models.RuntimeOptions());
        } catch (Exception e) {
            System.out.println("CardManager#streamUpdate get exception, msg:"+ e.getMessage());
        }

    }

    public void streamUpdate(String outTrackId, String content) {
        try {
            StreamingUpdateHeaders headers = new StreamingUpdateHeaders();
            headers.xAcsDingtalkAccessToken = accessTokenService.getAccessToken();
            StreamingUpdateRequest request =
                    new StreamingUpdateRequest().setOutTrackId(outTrackId).setGuid(UUID.randomUUID().toString()).setKey(
                            "content").setContent(content).setIsFull(false).setIsFinalize(false);
            client.streamingUpdateWithOptions(request, headers, new RuntimeOptions());
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
}
