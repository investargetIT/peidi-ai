package com.cyanrocks.ai.service;

import cn.hutool.core.util.XmlUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.utils.AiModelUtils;
import com.cyanrocks.ai.utils.http.HttpClientService;
import com.cyanrocks.ai.utils.http.HttpResponseContent;
import com.cyanrocks.ai.utils.http.HttpTimeoutConfig;
import com.cyanrocks.ai.utils.http.HttpUtils;
import com.cyanrocks.ai.utils.wechat.WXBizMsgCrypt;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class SiyuQuestionService {

    @Value("${wechat.siyu.token:}")
    private String token;
    @Value("${wechat.siyu.encodingAESKey:}")
    private String encodingAESKey;
    @Value("${wechat.siyu.corpId}")
    private String corpId;
    @Value("${wechat.kefu.secret}")
    private String secret;
    @Value("${wechat.siyu.miniprogram.appid:wxdacb5b4d2ee2f9cb}")
    private String miniprogramAppid;
    @Value("${wechat.siyu.miniprogram.title:佩蒂福利}")
    private String miniprogramTitle;
    @Value("${wechat.siyu.miniprogram.pagepath:http://t.weimob.com/Dr_YE9q}")
    private String miniprogramPagepath;
    @Value("${wechat.siyu.miniprogram.thumb_url:https://peidifiles.oss-cn-hangzhou.aliyuncs.com/ai/74EA4D25-5B01-4337-B26C-216CB6325F12.png?x-oss-credential=LTAI5t6M9FS3dXhwehCamNPP%2F20260521%2Fcn-hangzhou%2Foss%2Faliyun_v4_request&x-oss-date=20260521T023013Z&x-oss-expires=32400&x-oss-signature-version=OSS4-HMAC-SHA256&x-oss-signature=71c429ad2eb655c39de8ea191d005754a020c9f070fd61c8d5bf0438c305eae5}")
    private String miniprogramThumbUrl;
    @Value("${wechat.siyu.reply-text:咱们点击链接领取就可以了哈}")
    private String replyText;
    @Value("${wechat.siyu.welcome-text:哈喽~我是佩蒂官方的养宠专家-温温\n" +
            "咱们是来领取罐头的吗？}")
    private String welcomeText;
    @Value("${wechat.siyu.welcome-enabled:true}")
    private Boolean welcomeEnabled;

    private static final String REDIS_KEY = "siyu:wechat:token";

    @Autowired
    private AiModelUtils aiModelUtils;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private HttpClientService httpClientService;

    /**
     * 异步消息回复答案
     */
    public void getAnswer(String msgSignature,
                          String timestamp,
                          String nonce,
                          String requestBody) {
        try {
            // 1. 解析微信消息
            WXBizMsgCrypt wxcpt = new WXBizMsgCrypt(token, encodingAESKey, corpId);
            String sMsg = wxcpt.DecryptMsg(msgSignature, timestamp, nonce, requestBody);
            if (StringUtils.isEmpty(sMsg)) {
                return;
            }

            // XML 转 JSON
            Map<String, Object> msgMap = XmlUtil.xmlToMap(sMsg);
            String jsonStr = JSONUtil.toJsonStr(msgMap);
            JSONObject msgJson = JSONObject.parseObject(jsonStr);

            // 优先处理事件消息（如进入会话事件）
            String msgType = msgJson.getString("MsgType");
            if ("event".equalsIgnoreCase(msgType)) {
                handleEventMessage(msgJson);
                return;
            }

            // 2. 非事件消息，获取用户发送的具体消息内容
            String userMessage = parseUserMessage(msgJson);
            if (StringUtils.isEmpty(userMessage)) {
                return;
            }

            // 3. 调用大模型做意图识别
            IntentResult intentResult = recognizeIntent(userMessage);
            System.out.println("意图识别结果: intent=" + intentResult.getIntent()
                    + ", score=" + intentResult.getScore());

            // 4. 根据意图分数判断是否响应
            if (intentResult.getScore() >= 0.5) {
                // 意图识别分数 >= 0.5，执行对应意图的响应逻辑
                handleIntent(intentResult, msgJson);

                // 标记消息已处理，防止重复回复（缓存1小时）
                String msgId = msgJson.getString("msgid");
                if (StringUtils.isNotEmpty(msgId)) {
                    stringRedisTemplate.opsForValue().set("siyu:msg:processed:" + msgId, "1");
                    stringRedisTemplate.expire("siyu:msg:processed:" + msgId, Duration.ofHours(1));
                    System.out.println("标记消息已处理: msgId=" + msgId);
                }
            } else {
                // 意图识别分数 < 0.5，走默认回复或忽略
                System.out.println("意图识别分数过低，跳过响应");
            }

        } catch (Exception e) {
            System.err.println("处理私域运营客服消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理事件消息
     * 企业微信客服事件类型：enter_session(进入会话)、msg_send_fail(消息发送失败)
     */
    private void handleEventMessage(JSONObject msgJson) {
        String eventType = msgJson.getString("Event");
        System.out.println("收到事件消息: Event=" + eventType);

        if (StringUtils.isEmpty(eventType)) {
            return;
        }

        switch (eventType.toLowerCase()) {
            case "enter_session":
                // 用户进入客服会话，发送欢迎语
                handleEnterSessionEvent(msgJson);
                break;
            case "msg_send_fail":
                // 消息发送失败通知
                handleMsgSendFailEvent(msgJson);
                break;
            default:
                System.out.println("未处理的事件类型: " + eventType);
                break;
        }
    }

    /**
     * 处理进入会话事件 - 发送欢迎语
     * welcome_code 有效期 20 秒，需尽快调用 send_msg_on_event 接口
     */
    private void handleEnterSessionEvent(JSONObject msgJson) {
        if (!welcomeEnabled) {
            System.out.println("欢迎语功能已禁用");
            return;
        }

        String openKfId = msgJson.getString("OpenKfId");
        String externalUserId = msgJson.getString("ExternalUserID");
        String welcomeCode = msgJson.getString("WelcomeCode");

        System.out.println("进入会话事件: openKfId=" + openKfId
                + ", externalUserId=" + externalUserId
                + ", welcomeCode=" + welcomeCode);

        if (StringUtils.isEmpty(openKfId) || StringUtils.isEmpty(externalUserId)) {
            System.err.println("缺少必要的会话参数");
            return;
        }

        // 发送欢迎语
        sendWelcomeMessage(openKfId, externalUserId, welcomeCode);
    }

    /**
     * 发送欢迎语
     * 如果有 welcome_code（20秒内），使用 send_msg_on_event 接口
     * 否则使用普通 send_msg 接口
     */
    private void sendWelcomeMessage(String openKfId, String externalUserId, String welcomeCode) {
        try {
            String accessToken = getAccessToken();
            if (StringUtils.isEmpty(accessToken)) {
                System.err.println("获取access_token失败");
                return;
            }

            CloseableHttpClient httpClient = HttpClients.createDefault();

            // 判断是否有有效的 welcome_code
            if (StringUtils.isNotEmpty(welcomeCode)) {
                // 使用 send_msg_on_event 接口发送欢迎语（20秒内有效）
                sendWelcomeOnEvent(httpClient, accessToken, welcomeCode, openKfId);
            } else {
                // welcome_code 已失效或不存在，使用普通 send_msg 接口
                sendWelcomeNormal(httpClient, accessToken, externalUserId, openKfId);
            }

        } catch (Exception e) {
            System.err.println("发送欢迎语失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 使用 welcome_code 发送欢迎语（事件响应方式）
     * 适用于进入会话事件触发后 20 秒内
     */
    private void sendWelcomeOnEvent(CloseableHttpClient httpClient, String accessToken,
                                    String welcomeCode, String openKfId) {
        try {
            HttpPost httpPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg_on_event?access_token=" + accessToken);
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject request = new JSONObject();
            request.put("code", welcomeCode);
            request.put("msgtype", "text");

            JSONObject text = new JSONObject();
            text.put("content", welcomeText);
            request.put("text", text);

            httpPost.setEntity(new StringEntity(JSONObject.toJSONString(request), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("欢迎语事件响应结果: " + responseBody);

                JSONObject responseJson = JSONObject.parseObject(responseBody);
                Integer errcode = responseJson.getInteger("errcode");
                if (errcode != null && errcode != 0) {
                    System.err.println("欢迎语发送失败: " + responseJson.getString("errmsg"));
                    // 如果 welcome_code 失效，可能需要用普通方式重试
                    if (errcode == 40076) { // 无效的code
                        System.out.println("welcome_code已失效，需要使用普通发送方式");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("事件响应发送欢迎语失败: " + e.getMessage());
        }
    }

    /**
     * 使用普通方式发送欢迎语
     * 适用于 welcome_code 失效或非进入会话场景
     */
    private void sendWelcomeNormal(CloseableHttpClient httpClient, String accessToken,
                                   String externalUserId, String openKfId) {
        try {
            HttpPost httpPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg?access_token=" + accessToken);
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject request = new JSONObject();
            request.put("touser", externalUserId);
            request.put("open_kfid", openKfId);
            request.put("msgtype", "text");
            request.put("msgid", UUID.randomUUID().toString().replace("-", ""));

            JSONObject text = new JSONObject();
            text.put("content", welcomeText);
            request.put("text", text);

            httpPost.setEntity(new StringEntity(JSONObject.toJSONString(request), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("普通欢迎语发送结果: " + responseBody);
            }
        } catch (Exception e) {
            System.err.println("普通方式发送欢迎语失败: " + e.getMessage());
        }
    }

    /**
     * 处理消息发送失败事件
     */
    private void handleMsgSendFailEvent(JSONObject msgJson) {
        String failMsgId = msgJson.getString("FailMsgId");
        String failType = msgJson.getString("FailType");

        System.out.println("消息发送失败事件: failMsgId=" + failMsgId + ", failType=" + failType);

        // 根据失败类型记录日志，failType 含义：
        // 0 - 客户已拒绝接收消息
        // 1 - 客户48小时内未互动，无法发送主动消息
        // 2 - 客户已取消关注公众号
        switch (failType != null ? failType : "unknown") {
            case "0":
                System.err.println("客户已拒绝接收消息: msgId=" + failMsgId);
                break;
            case "1":
                System.err.println("客户48小时内未互动，无法发送消息: msgId=" + failMsgId);
                break;
            case "2":
                System.err.println("客户已取消关注公众号: msgId=" + failMsgId);
                break;
            default:
                System.err.println("未知失败类型: failType=" + failType);
                break;
        }
    }

    /**
     * 获取用户发送的消息内容
     * 调用微信 sync_msg 接口获取消息列表，提取最后一条文本消息
     * 同时将 external_userid 写入 msgJson 供后续回复使用
     */
    private String parseUserMessage(JSONObject msgJson) {
        try {
            // 获取access_token
            String accessToken = getAccessToken();
            if (StringUtils.isEmpty(accessToken)) {
                System.err.println("获取access_token失败");
                return null;
            }

            // 获取消息基本信息
            String tokenValue = msgJson.getString("Token");
            String openKfId = msgJson.getString("OpenKfId");

            if (StringUtils.isEmpty(tokenValue) || StringUtils.isEmpty(openKfId)) {
                System.err.println("缺少必要的消息参数");
                return null;
            }

            // 调用 sync_msg 接口获取对话信息
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/sync_msg?access_token=" + accessToken);
                httpPost.setHeader("Content-Type", "application/json");

                JSONObject msgReq = new JSONObject();
                msgReq.put("token", tokenValue);
                msgReq.put("open_kfid", openKfId);
                httpPost.setEntity(new StringEntity(JSONObject.toJSONString(msgReq), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                    JSONObject responseJson = JSONObject.parseObject(responseBody);

                    // 检查接口返回
                    if (responseJson.getInteger("errcode") != null && responseJson.getInteger("errcode") != 0) {
                        System.err.println("sync_msg 接口错误: " + responseJson.getString("errmsg"));
                        return null;
                    }

                    com.alibaba.fastjson.JSONArray msgArray = responseJson.getJSONArray("msg_list");
                    if (msgArray == null || msgArray.isEmpty()) {
                        System.out.println("消息列表为空");
                        return null;
                    }

                    // 获取最后一条消息
                    JSONObject lastMsg = msgArray.getJSONObject(msgArray.size() - 1);
                    String externalUserid = lastMsg.getString("external_userid");
                    String msgId = lastMsg.getString("msgid");

                    // 将 external_userid 和 msgid 写入 msgJson，供后续使用
                    msgJson.put("external_userid", externalUserid);
                    msgJson.put("msgid", msgId);

                    // 检查最后一条消息是否为文本消息
                    String msgType = lastMsg.getString("msgtype");
                    if (!"text".equals(msgType)) {
                        System.out.println("最新一条消息不是文本消息，类型: " + msgType);
                        return null;
                    }

                    // 检查是否已处理过该消息（防止重复回复）
                    String processedKey = "siyu:msg:processed:" + msgId;
                    if (stringRedisTemplate.hasKey(processedKey)) {
                        System.out.println("消息已处理过，跳过: msgId=" + msgId);
                        return null;
                    }

                    // 提取文本内容
                    JSONObject textObj = lastMsg.getJSONObject("text");
                    if (textObj != null) {
                        String content = textObj.getString("content");
                        System.out.println("获取到用户消息: " + content + ", msgId=" + msgId);
                        return content;
                    }

                    return null;
                }
            }

        } catch (Exception e) {
            System.err.println("获取用户消息失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 调用大模型进行意图识别
     */
    private IntentResult recognizeIntent(String userMessage) {
        IntentResult result = new IntentResult();
        result.setIntent(IntentType.UNKNOWN);
        result.setScore(0.0);
        result.setOriginalMessage(userMessage);

        try {
            // 调用大模型进行意图识别
            String response = aiModelUtils.getIntentRecognition(userMessage);
            if (StringUtils.isEmpty(response)) {
                System.err.println("意图识别模型返回为空");
                return result;
            }

            System.out.println("意图识别原始响应: " + response);

            // 解析模型返回的 JSON
            // 预期格式: {"intent": "RECEIVE_GIFT", "score": 0.85, "params": "xxx"}
            JSONObject intentJson = JSONObject.parseObject(response);

            // 解析意图类型
            String intentStr = intentJson.getString("intent");
            IntentType intentType = parseIntentType(intentStr);
            result.setIntent(intentType);

            // 解析置信度分数
            Double score = intentJson.getDouble("score");
            if (score != null) {
                result.setScore(score);
            }

            // 解析扩展参数
            String params = intentJson.getString("params");
            if (StringUtils.isNotEmpty(params)) {
                result.setParams(params);
            }

            // 保存原始响应
            result.setRawResponse(response);

        } catch (Exception e) {
            System.err.println("意图识别解析失败: " + e.getMessage());
            // 解析失败时返回默认值
        }

        return result;
    }

    /**
     * 将字符串意图类型转换为枚举
     */
    private IntentType parseIntentType(String intentStr) {
        if (StringUtils.isEmpty(intentStr)) {
            return IntentType.UNKNOWN;
        }

        // 尝试直接匹配枚举名称
        try {
            return IntentType.valueOf(intentStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试根据描述匹配
            switch (intentStr.toLowerCase()) {
                case "receive_gift":
                case "领取":
                case "领取奖品":
                    return IntentType.RECEIVE_GIFT;
                case "ask_receive_method":
                case "怎么领取":
                case "领取方式":
                    return IntentType.ASK_RECEIVE_METHOD;
                case "can_related":
                case "罐头":
                case "罐头相关":
                    return IntentType.CAN_RELATED;
                case "unknown":
                case "未知":
                    return IntentType.UNKNOWN;
                default:
                    return IntentType.OTHER;
            }
        }
    }

    /**
     * 根据意图执行响应逻辑
     * TODO: 实现各意图的具体响应
     */
    private void handleIntent(IntentResult intentResult, JSONObject msgJson) {
        IntentType intent = intentResult.getIntent();

        switch (intent) {
            case RECEIVE_GIFT:
                // TODO: 处理"领取xxxx"意图
                handleReceiveGift(intentResult, msgJson);
                break;
            case ASK_RECEIVE_METHOD:
                // TODO: 处理"怎么领取"意图
                handleAskReceiveMethod(intentResult, msgJson);
                break;
            case CAN_RELATED:
                // TODO: 处理"罐头"相关意图
                handleCanRelated(intentResult, msgJson);
                break;
            case UNKNOWN:
                // TODO: 处理未知意图
                handleUnknown(intentResult, msgJson);
                break;
            default:
                // TODO: 扩展其他意图
                handleDefault(intentResult, msgJson);
                break;
        }
    }

    // ========== 各意图的具体响应方法 ==========

    /**
     * 处理"领取xxxx"意图 - 发送回复和小程序链接
     */
    private void handleReceiveGift(IntentResult intentResult, JSONObject msgJson) {
        System.out.println("处理领取意图: " + intentResult.getOriginalMessage());
        sendCouponReply(msgJson);
    }

    /**
     * 处理"怎么领取"意图 - 发送回复和小程序链接
     */
    private void handleAskReceiveMethod(IntentResult intentResult, JSONObject msgJson) {
        System.out.println("处理领取方式意图: " + intentResult.getOriginalMessage());
        sendCouponReply(msgJson);
    }

    /**
     * 处理"罐头"相关意图 - 发送回复和小程序链接
     */
    private void handleCanRelated(IntentResult intentResult, JSONObject msgJson) {
        System.out.println("处理罐头相关意图: " + intentResult.getOriginalMessage());
        sendCouponReply(msgJson);
    }

    /**
     * 统一发送优惠券领取回复（文本 + 小程序卡片）
     */
    private void sendCouponReply(JSONObject msgJson) {
        try {
            // 获取access_token
            String accessToken = getAccessToken();
            if (StringUtils.isEmpty(accessToken)) {
                System.err.println("获取access_token失败");
                return;
            }

            // 获取用户ID和客服ID
            String externalUserid = msgJson.getString("external_userid");
            String openKfId = msgJson.getString("OpenKfId");

            if (StringUtils.isEmpty(externalUserid) || StringUtils.isEmpty(openKfId)) {
                System.err.println("缺少必要的消息参数");
                return;
            }

            CloseableHttpClient httpClient = HttpClients.createDefault();

            // 1. 先发送文本消息
            HttpPost textPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg?access_token=" + accessToken);
            textPost.setHeader("Content-Type", "application/json");
            JSONObject textReq = new JSONObject();
            textReq.put("touser", externalUserid);
            System.out.println("发送给touser: " + externalUserid);
            textReq.put("open_kfid", openKfId);
            textReq.put("msgid", UUID.randomUUID().toString().replace("-", ""));
            textReq.put("msgtype", "text");
            textReq.put("text", new JSONObject().fluentPut("content", replyText));
            textPost.setEntity(new StringEntity(JSONObject.toJSONString(textReq), ContentType.APPLICATION_JSON));
            try (CloseableHttpResponse textRes = httpClient.execute(textPost)) {
                String responseBody = EntityUtils.toString(textRes.getEntity(), "UTF-8");
                System.out.println("文本消息响应: " + responseBody);
            }

            // 2. 发送小程序卡片消息
            if (StringUtils.isNotEmpty(miniprogramAppid)) {
                // 获取封面图片的 media_id
                String thumbMediaId = getThumbMediaId(accessToken);

                HttpPost miniPost = new HttpPost("https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg?access_token=" + accessToken);
                miniPost.setHeader("Content-Type", "application/json");
                JSONObject miniReq = new JSONObject();
                miniReq.put("touser", externalUserid);
                miniReq.put("open_kfid", openKfId);
                miniReq.put("msgid", UUID.randomUUID().toString().replace("-", ""));
                miniReq.put("msgtype", "link");
                JSONObject miniprogram = new JSONObject();
//                miniprogram.put("appid", miniprogramAppid);
                miniprogram.put("title", miniprogramTitle);
                miniprogram.put("description", "超值优惠，点击即可领取");
                miniprogram.put("url", miniprogramPagepath);
                // 使用 thumb_media_id（微信要求）
                if (StringUtils.isNotEmpty(thumbMediaId)) {
                    miniprogram.put("thumb_media_id", thumbMediaId);
                }
                miniReq.put("link", miniprogram);
                miniPost.setEntity(new StringEntity(JSONObject.toJSONString(miniReq), ContentType.APPLICATION_JSON));
                try (CloseableHttpResponse miniRes = httpClient.execute(miniPost)) {
                    String responseBody = EntityUtils.toString(miniRes.getEntity(), "UTF-8");
                    System.out.println("小程序卡片响应: " + responseBody);
                }
            }

        } catch (Exception e) {
            System.err.println("发送优惠券回复失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取微信access_token（从Redis缓存或重新获取）
     */
    private String getAccessToken() {
        try {
            // 先从Redis获取
            String token = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (StringUtils.isNotEmpty(token)) {
                return token;
            }

            // Redis中没有，重新获取
            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + secret;
            HttpResponseContent content = httpClientService.doGet(url, null, null,
                    HttpUtils.initHttpClientContext(null, new HttpTimeoutConfig(300000)));

            cn.hutool.json.JSONObject contentJson = JSONUtil.parseObj(content.getContent());
            if (200 != content.getStatusCode()) {
                throw new RuntimeException(contentJson.getStr("errmsg"));
            }

            token = contentJson.getStr("access_token");
            if (StringUtils.isNotEmpty(token)) {
                stringRedisTemplate.opsForValue().set(REDIS_KEY, token);
                stringRedisTemplate.expire(REDIS_KEY, Duration.ofSeconds(7200));
            }

            return token;
        } catch (Exception e) {
            System.err.println("获取access_token失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取小程序封面图片的 media_id
     * 从 Redis 缓存获取，或从 URL 下载后上传到微信
     */
    private String getThumbMediaId(String accessToken) {
        String mediaIdRedisKey = "siyu:wechat:thumb_media_id";

        try {
            // 先从Redis获取缓存的 media_id
            String cachedMediaId = stringRedisTemplate.opsForValue().get(mediaIdRedisKey);
            if (StringUtils.isNotEmpty(cachedMediaId)) {
                System.out.println("使用缓存的 thumb_media_id: " + cachedMediaId);
                return cachedMediaId;
            }

            // 没有缓存，下载图片并上传
            if (StringUtils.isEmpty(miniprogramThumbUrl)) {
                System.err.println("未配置小程序封面图片URL");
                return null;
            }

            // 下载图片
            byte[] imageBytes = httpClientService.doGetBytes(miniprogramThumbUrl, null);
            if (imageBytes == null || imageBytes.length == 0) {
                System.err.println("下载封面图片失败");
                return null;
            }

            // 上传图片到微信获取 media_id
            String uploadUrl = "https://qyapi.weixin.qq.com/cgi-bin/media/upload?access_token=" + accessToken + "&type=image";

            OkHttpClient okHttpClient = new OkHttpClient();
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("media", "thumb.png",
                            RequestBody.create(imageBytes, MediaType.parse("image/png")))
                    .build();

            Request uploadRequest = new Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build();

            try (Response uploadRes = okHttpClient.newCall(uploadRequest).execute()) {
                String responseBody = uploadRes.body() != null ? uploadRes.body().string() : "";
                System.out.println("上传图片响应: " + responseBody);

                if (uploadRes.isSuccessful()) {
                    JSONObject uploadJson = JSONObject.parseObject(responseBody);
                    String mediaId = uploadJson.getString("media_id");
                    if (StringUtils.isNotEmpty(mediaId)) {
                        // 缓存 media_id（临时素材有效期3天，缓存2天）
                        stringRedisTemplate.opsForValue().set(mediaIdRedisKey, mediaId);
                        stringRedisTemplate.expire(mediaIdRedisKey, Duration.ofDays(2));
                        System.out.println("获取并缓存 thumb_media_id: " + mediaId);
                        return mediaId;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            System.err.println("获取 thumb_media_id 失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 处理未知意图
     */
    private void handleUnknown(IntentResult intentResult, JSONObject msgJson) {
        // TODO: 具体实现
        System.out.println("处理未知意图: " + intentResult.getOriginalMessage());
    }

    /**
     * 默认处理
     */
    private void handleDefault(IntentResult intentResult, JSONObject msgJson) {
        // TODO: 具体实现
        System.out.println("默认处理: " + intentResult.getOriginalMessage());
    }
}
