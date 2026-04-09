package com.cyanrocks.ai.controller;

import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.XmlUtil;
import com.cyanrocks.ai.utils.http.HttpClientService;
import com.cyanrocks.ai.utils.http.HttpResponseContent;
import com.cyanrocks.ai.utils.http.HttpTimeoutConfig;
import com.cyanrocks.ai.utils.http.HttpUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cyanrocks.ai.dao.entity.AiQueryHistory;
import com.cyanrocks.ai.dao.mapper.AiQueryHistoryMapper;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.utils.rabbitmq.RabbitMQConfig;
import com.cyanrocks.ai.utils.wechat.WXBizJsonMsgCrypt;
import com.cyanrocks.ai.utils.wechat.WXBizMsgCrypt;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @Author wjq
 * @Date 2026/1/30 9:21
 */
@RestController
@RequestMapping("/ai/wenwen")
@Api(tags = {"问问相关接口"})
public class WenwenControler {

    @Autowired
    private MilvusUtils milvusUtils;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AiQueryHistoryMapper aiQueryHistoryMapper;
    @Value("${wechat.kefu.secret}")
    private String secret;
    @Value("${wechat.kefu.cropid}")
    private String cropid;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private HttpClientService httpClientService;
    private static final String REDIS_KEY = "kefu:wechat:token";


    /**
     * Validate the WeCom robot callback URL and return the decrypted echo string for verification.
     *
     * @param msgSignature the message signature from WeCom request
     * @param timestamp    the timestamp from WeCom request
     * @param nonce        the nonce from WeCom request
     * @param echostr      the encrypted echo string provided by WeCom for URL verification
     * @return the decrypted echo string expected by WeCom for successful verification, or an empty string on failure
     */
    @GetMapping("/wechat/question")
    @ApiOperation(value = "企业微信机器人验证url")
    public String wechatVerificationUrl(@RequestParam("msg_signature") String msgSignature,
                                           @RequestParam("timestamp") String timestamp,
                                           @RequestParam("nonce") String nonce,
                                           @RequestParam("echostr") String echostr) {
        try {
            System.out.println("msg_signature: " + msgSignature);
            System.out.println("timestamp: " + timestamp);
            System.out.println("nonce: " + nonce);
            System.out.println("echostr: " + echostr);

            WXBizJsonMsgCrypt wxcpt = new WXBizJsonMsgCrypt("wGMU3UHrN0baB7dkHazVdSlvHO", "NwIfuw690g1ogOmyA9OK5ipcbEL8CECWMfFBCQzylgi", "");
            String sEchoStr = wxcpt.VerifyURL(msgSignature, timestamp,
                    nonce, echostr);
            System.out.println("verifyurl echostr: " + sEchoStr);
            return sEchoStr;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Handle incoming enterprise WeCom robot messages: process feedback events that update query acceptance and Milvus records, forward user text messages to RabbitMQ for processing, and send an immediate encrypted stream reply indicating the query is in progress.
     *
     * @param msgSignature the WeCom message signature from the request query string
     * @param timestamp the WeCom timestamp from the request query string
     * @param nonce the WeCom nonce from the request query string
     * @param requestBody the encrypted request payload received from WeCom
     * @return the encrypted reply message to return to WeCom, or an empty string when no reply is required or on error
     */
    @PostMapping("/wechat/question")
    @ApiOperation(value = "企业微信机器人问问题")
    public String wechatQuestion(@RequestParam("msg_signature") String msgSignature,
                               @RequestParam("timestamp") String timestamp,
                               @RequestParam("nonce") String nonce,
                               @RequestBody String requestBody) {
        try {
            WXBizJsonMsgCrypt wxcpt = new WXBizJsonMsgCrypt("wGMU3UHrN0baB7dkHazVdSlvHO", "NwIfuw690g1ogOmyA9OK5ipcbEL8CECWMfFBCQzylgi", "");
            String sMsg = wxcpt.DecryptMsg(msgSignature, timestamp, nonce, requestBody);
            System.out.println("after decrypt msg: " + sMsg);
            if (StringUtils.isEmpty(sMsg)){
                return "";
            }
            //评价消息
            if ("event".equals(JSONObject.parseObject(sMsg).get("msgtype"))){
                System.out.println("charge");
                JSONObject event = JSONObject.parseObject(sMsg).getJSONObject("event");
                JSONObject feedback = event.getJSONObject("feedback_event");
                int type = feedback.getInteger("type");
                String id = feedback.getString("id");
                AiQueryHistory aiQueryHistory = aiQueryHistoryMapper.selectById(Long.valueOf(id));
                if (null == aiQueryHistory){
                    return "";
                }
                if (1 == type){
                    //认同
                    System.out.println("charge 1");
                    aiQueryHistory.setAccept(true);
                    aiQueryHistory.setMilvusId(milvusUtils.processLlmBackMilvus(aiQueryHistory,"query_accept").toString());
                    aiQueryHistoryMapper.updateById(aiQueryHistory);
                }else if (2 == type){
                    //不认同
                    if (StringUtils.isNotEmpty(aiQueryHistory.getMilvusId())){
                        milvusUtils.deleteMilvusById(aiQueryHistory.getMilvusId(),"query_accept");
                    }
                    aiQueryHistory.setAccept(false);
                    aiQueryHistory.setMilvusId("");
                    StringBuilder acceptReason = new StringBuilder();
                    if (null != feedback.getJSONArray("inaccurate_reason_list")){
                        JSONArray inaccurateReasonList = feedback.getJSONArray("inaccurate_reason_list");
                        if (inaccurateReasonList != null) {
                            inaccurateReasonList.forEach(inaccurateReason -> {
                                if (Integer.valueOf(1).equals(inaccurateReason)) {
                                    acceptReason.append("与问题无关").append(",");
                                }else if (Integer.valueOf(2).equals(inaccurateReason)) {
                                    acceptReason.append("内容不完整").append(",");
                                }else if (Integer.valueOf(3).equals(inaccurateReason)) {
                                    acceptReason.append("内容有错误").append(",");
                                }else if (Integer.valueOf(4).equals(inaccurateReason)) {
                                    acceptReason.append("数据分析错误").append(",");
                                }
                            });
                        }
                    }
                    if (null != feedback.getString("content")){
                        acceptReason.append(feedback.getString("content"));
                    }
                    aiQueryHistory.setAcceptReason(acceptReason.toString());
                    aiQueryHistoryMapper.updateById(aiQueryHistory);
                }else if (3 == type){
                    //取消认同/取消不认同
                    if (StringUtils.isNotEmpty(aiQueryHistory.getMilvusId())){
                        milvusUtils.deleteMilvusById(aiQueryHistory.getMilvusId(),"query_accept");
                    }
                    aiQueryHistoryMapper.update(
                            null,
                            Wrappers.<AiQueryHistory>lambdaUpdate()
                                    .set(AiQueryHistory::getAccept, null)
                                    .set(AiQueryHistory::getMilvusId, null)
                                    .set(AiQueryHistory::getAcceptReason, null)
                                    .eq(AiQueryHistory::getId, id)
                    );
                }
                return "";
            }
            //没有文本输入，为第一次进入私聊框。直接返回
            if (null == JSONObject.parseObject(sMsg).get("text")){
                return "";
            }

            //消息发送到rebbitMq中
            JSONObject rabbit = new JSONObject();
            rabbit.put("sMsg", sMsg);
            rabbitTemplate.convertAndSend(RabbitMQConfig.WECOM_PROCESS_QUEUE, JSONObject.toJSONString(rabbit));

            // 立即回复
            JSONObject firstReply = new JSONObject();
            firstReply.put("msgtype", "stream");
            JSONObject stream = new JSONObject();
            stream.put("id", UUID.randomUUID().toString());
            stream.put("finish", true);
            stream.put("content", "正在查询中，请稍候...");
            firstReply.put("stream", stream);

            return wxcpt.EncryptMsg(JSONObject.toJSONString(firstReply), timestamp, nonce);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Validate an enterprise WeChat (customer service) callback and produce the verification echo.
     *
     * @param msgSignature the WeChat msg_signature query parameter used to verify the request
     * @param timestamp    the WeChat timestamp query parameter
     * @param nonce        the WeChat nonce query parameter
     * @param echostr      the WeChat echostr value to be verified and echoed on success
     * @return the verified echo string required by WeChat when verification succeeds, or an empty string on failure
     */
    @GetMapping("/kefu/question")
    @ApiOperation(value = "企业微信客服验证url")
    public String kefuVerificationUrl(@RequestParam("msg_signature") String msgSignature,
                                        @RequestParam("timestamp") String timestamp,
                                        @RequestParam("nonce") String nonce,
                                        @RequestParam("echostr") String echostr) {
        try {
            System.out.println("msg_signature: " + msgSignature);
            System.out.println("timestamp: " + timestamp);
            System.out.println("nonce: " + nonce);
            System.out.println("echostr: " + echostr);

            WXBizJsonMsgCrypt wxcpt = new WXBizJsonMsgCrypt("gm1uvAB1LInOuV9rMYbQrt7Xy6W", "uzkzTGkHTlJDkqbIvDgbBBsRalhaE6YOliwS7rZTBfB", "ww3e4d6806d3572c2e");
            String sEchoStr = wxcpt.VerifyURL(msgSignature, timestamp,
                    nonce, echostr);
            System.out.println("verifyurl echostr: " + sEchoStr);
            return sEchoStr;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Processes an incoming WeCom customer-service webhook: decrypts the request, retrieves recent conversation messages,
     * extracts the latest text question and any images uploaded after the previous text from the same external user,
     * sends an immediate "正在查询中，请稍候..." acknowledgement to the user, and publishes the assembled question payload
     * (including image media IDs) to the KEFU RabbitMQ processing queue.
     *
     * @param msgSignature the message signature from WeCom used for decryption
     * @param timestamp    the timestamp from WeCom used for decryption
     * @param nonce        the nonce from WeCom used for decryption
     * @param requestBody  the raw encrypted request body received from WeCom
     * @return an empty string (endpoint responds with an empty body)
    @PostMapping("/kefu/question")
    @ApiOperation(value = "企业微信客服问问题")
    public String kefuQuestion(@RequestParam("msg_signature") String msgSignature,
                                 @RequestParam("timestamp") String timestamp,
                                 @RequestParam("nonce") String nonce,
                                 @RequestBody String requestBody) {
        try {
            WXBizMsgCrypt wxcpt = new WXBizMsgCrypt("gm1uvAB1LInOuV9rMYbQrt7Xy6W", "uzkzTGkHTlJDkqbIvDgbBBsRalhaE6YOliwS7rZTBfB", "ww3e4d6806d3572c2e");
            String sMsg = wxcpt.DecryptMsg(msgSignature, timestamp, nonce, requestBody);
            if (StringUtils.isEmpty(sMsg)){
                return "";
            }
            Map<String, Object> msgMap = XmlUtil.xmlToMap(sMsg);
            String jsonStr = JSONUtil.toJsonStr(msgMap);
            JSONObject msgJson = JSONObject.parseObject(jsonStr);
            //获取access_token
            String token = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (null == token){
                String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="+ cropid +"&corpsecret="+ secret;
                HttpResponseContent content;
                try {
                    content = httpClientService.doGet(url, null, null,
                            HttpUtils.initHttpClientContext(null, new HttpTimeoutConfig(300000)));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    return null;
                }
                cn.hutool.json.JSONObject contentJson = JSONUtil.parseObj(content.getContent());
                if (200 != content.getStatusCode()){
                    throw new RuntimeException(contentJson.getStr("errmsg"));
                }
                token = contentJson.getStr("access_token");
                stringRedisTemplate.opsForValue().set(REDIS_KEY, token);
                stringRedisTemplate.expire(REDIS_KEY, Duration.ofSeconds(7200));
            }
            //获取对话信息
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/sync_msg?access_token="+token);
            httpPost.setHeader("Content-Type", "application/json");
            JSONObject msgReq = new JSONObject();
            msgReq.put("token", msgJson.get("Token"));
            msgReq.put("open_kfid", msgJson.get("OpenKfId"));
            httpPost.setEntity(new StringEntity(
                    JSONObject.toJSONString(msgReq),
                    ContentType.APPLICATION_JSON
            ));
            CloseableHttpResponse response = httpClient.execute(httpPost);
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            JSONObject responseJson = JSONObject.parseObject(responseBody);
            JSONArray msgArray = responseJson.getJSONArray("msg_list");
            if (msgArray != null && !msgArray.isEmpty()) {
                JSONObject lastMsg = msgArray.getJSONObject(msgArray.size() - 1);
                String externalUserid = lastMsg.getString("external_userid");
                if (null == lastMsg.getString("msgtype") || !"text".equals(lastMsg.getString("msgtype"))){
                    //最新一条不是文本消息，不做搭理
                    return "";
                }

                // 从后往前遍历，找到此人上一个msgtype="text" 的消息
                int prevTextIndex = -1;
                for (int i = msgArray.size() - 2; i >= 0; i--) {
                    JSONObject msg = msgArray.getJSONObject(i);
                    if ("text".equals(msg.getString("msgtype")) && externalUserid.equals(msg.getString("external_userid"))) {
                        prevTextIndex = i;
                        break;
                    }
                }

                // 获取从上一个text之后到最后一消息之间，所有相同external_userid的上传图片
                List<String> imgList = new ArrayList<>();
                if (prevTextIndex > -1){
                    for (int i = prevTextIndex + 1; i < msgArray.size(); i++) {
                        JSONObject msg = msgArray.getJSONObject(i);
                        String currentExternalUserid = msg.getString("external_userid");
                        if (externalUserid.equals(currentExternalUserid) && "image".equals(msg.getString("msgtype"))) {
                            imgList.add(msg.getJSONObject("image").getString("media_id"));
                        }
                    }
                }else {
                    for (int i = 0; i < msgArray.size(); i++) {
                        JSONObject msg = msgArray.getJSONObject(i);
                        String currentExternalUserid = msg.getString("external_userid");
                        if (externalUserid.equals(currentExternalUserid) && "image".equals(msg.getString("msgtype"))) {
                            imgList.add(msg.getJSONObject("image").getString("media_id"));
                        }
                    }
                }

                String content = lastMsg.getJSONObject("text").getString("content");

                //变更会话状态
//                HttpPost sessionPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/service_state/trans?access_token="+token);
//                sessionPost.setHeader("Content-Type", "application/json");
//                JSONObject sessionReq = new JSONObject();
//                sessionReq.put("external_userid", externalUserid);
//                sessionReq.put("open_kfid", msgJson.get("OpenKfId"));
//                sessionReq.put("service_state", 1);
//                sessionPost.setEntity(new StringEntity(
//                        JSONObject.toJSONString(sessionReq),
//                        ContentType.APPLICATION_JSON
//                ));
//                try (CloseableHttpResponse sessionRes = httpClient.execute(sessionPost)) {
//                    String sendBody = EntityUtils.toString(sessionRes.getEntity(), "UTF-8");
//                    System.out.println("WeChat Response: " + sendBody);
//
//                    int status = sessionRes.getStatusLine().getStatusCode();
//                    if (status == 200) {
//                        System.out.println("发送成功");
//                    } else {
//                        System.err.println("发送失败，状态码: " + status);
//                    }
//                }


                //发送消息已经收到问题
                HttpPost sendPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg?access_token="+token);
                sendPost.setHeader("Content-Type", "application/json");
                JSONObject sendReq = new JSONObject();
                sendReq.put("touser", externalUserid);
                sendReq.put("open_kfid", msgJson.get("OpenKfId"));
                sendReq.put("msgid", UUID.randomUUID().toString().replace("-", ""));
                sendReq.put("msgtype", "text");
                sendReq.put("text", new JSONObject().fluentPut("content", "正在查询中，请稍候..."));
                sendPost.setEntity(new StringEntity(
                        JSONObject.toJSONString(sendReq),
                        ContentType.APPLICATION_JSON
                ));
                try (CloseableHttpResponse sendRes = httpClient.execute(sendPost)) {
                    String sendBody = EntityUtils.toString(sendRes.getEntity(), "UTF-8");
                    System.out.println("WeChat Response: " + sendBody);

                    int status = sendRes.getStatusLine().getStatusCode();
                    if (status == 200) {
                        System.out.println("发送成功");
                    } else {
                        System.err.println("发送失败，状态码: " + status);
                    }
                }

                //组装消息发送到rebbitMq中
                sendReq.put("que",content);
                sendReq.put("imgList",JSONObject.toJSONString(imgList));
                System.out.println("接收到"+imgList.size()+"张图片");
                rabbitTemplate.convertAndSend(RabbitMQConfig.KEFU_PROCESS_QUEUE, JSONObject.toJSONString(sendReq));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
