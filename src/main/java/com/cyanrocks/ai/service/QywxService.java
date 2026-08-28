package com.cyanrocks.ai.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyanrocks.ai.config.PdOmsConfig;
import com.cyanrocks.ai.config.QywxConfig;
import com.cyanrocks.ai.dao.entity.AiFirmWechatForm;
import com.cyanrocks.ai.dao.mapper.AiFirmWechatFormMapper;
import com.cyanrocks.ai.vo.ContactDetailVO;
import com.cyanrocks.ai.vo.ContactListResult;
import com.cyanrocks.ai.vo.WecomContactVO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 企业微信服务类
 * @author yangshihao
 */
@Slf4j
@Service
public class QywxService {

    @Autowired
    private QywxConfig qywxConfig;

    @Autowired
    private PdOmsConfig pdOmsConfig;

    @Autowired
    @Qualifier("okHttpClientShort")
    private OkHttpClient okHttpClient;

    @Autowired
    private AiFirmWechatFormMapper aiFirmWechatFormMapper;

    // 缓存 access_token
    private volatile String cachedAccessToken = null;
    private volatile long tokenExpireTime = 0;

    /**
     * 获取 access_token
     */
    public String getAccessToken() throws Exception {
        long now = System.currentTimeMillis();
        
        // 如果缓存还有效，直接返回
        if (cachedAccessToken != null && now < tokenExpireTime) {
            return cachedAccessToken;
        }

        String url = String.format(
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
            qywxConfig.getCorpId(),
            qywxConfig.getCorpSecret()
        );

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("获取access_token失败: 响应体为空");
            }
            String body = response.body().string();
            JSONObject json = JSONObject.parseObject(body);

            if (json.getIntValue("errcode") != 0) {
                log.error("获取access_token失败: {}", json.getString("errmsg"));
                throw new RuntimeException("获取access_token失败: " + json.getString("errmsg"));
            }

            cachedAccessToken = json.getString("access_token");
            tokenExpireTime = now + (json.getIntValue("expires_in") - 300) * 1000L; // 提前5分钟过期
            
            log.info("成功获取access_token, 过期时间: {}", tokenExpireTime);
            return cachedAccessToken;
        }
    }

    /**
     * 用 code 获取用户信息
     */
    public JSONObject getUserInfo(String code) throws Exception {
        String accessToken = getAccessToken();
        String url = String.format(
            "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=%s&code=%s",
            accessToken, code
        );

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("获取用户信息失败: 响应体为空");
            }
            String body = response.body().string();
            JSONObject json = JSONObject.parseObject(body);

            if (json.getIntValue("errcode") != 0) {
                log.error("获取用户信息失败: {}", json.getString("errmsg"));
                throw new RuntimeException("获取用户信息失败: " + json.getString("errmsg"));
            }

            log.info("成功获取用户信息: {}", json.toJSONString());
            return json;
        }
    }

    /**
     * 获取客户详情（包含标签信息）
     * 文档: https://developer.work.weixin.qq.com/document/path/92114
     * @param externalUserid 外部联系人ID
     * @return 客户详情JSON
     */
    public JSONObject getContactDetail(String externalUserid) throws Exception {
        String accessToken = getAccessToken();
        String url = String.format(
            "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/get?access_token=%s&external_userid=%s",
            accessToken, externalUserid
        );

        log.info("调用企微获取客户详情接口, externalUserid={}, URL={}", externalUserid, url);

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            // 记录响应状态码
            int statusCode = response.code();
            String statusMessage = response.message();

            if (response.body() == null) {
                log.error("获取客户详情失败: 响应体为空, externalUserid={}, statusCode={}, statusMessage={}",
                        externalUserid, statusCode, statusMessage);
                throw new RuntimeException("获取客户详情失败: 响应体为空, statusCode=" + statusCode);
            }

            String body = response.body().string();
            log.info("企微接口响应: externalUserid={}, statusCode={}, response={}",
                    externalUserid, statusCode, body);

            JSONObject json = JSONObject.parseObject(body);

            if (json.getIntValue("errcode") != 0) {
                int errcode = json.getIntValue("errcode");
                String errmsg = json.getString("errmsg");
                log.error("获取客户详情失败: externalUserid={}, errcode={}, errmsg={}, 完整响应={}",
                        externalUserid, errcode, errmsg, body);
                if (errcode == 84061) {
                    //不处理
                    return null;
                }
//                throw new RuntimeException("获取客户详情失败: errcode=" + errcode + ", errmsg=" + errmsg);
            }

            log.info("成功获取客户详情: externalUserid={}", externalUserid);
            return json;
        } catch (Exception e) {
            log.error("调用企微获取客户详情接口异常: externalUserid={}, URL={}, 异常信息={}",
                    externalUserid, url, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取客户标签和添加时间信息
     * 文档: https://developer.work.weixin.qq.com/document/path/92114
     * @param externalUserid 外部联系人ID
     * @return 客户详情VO（包含标签和添加时间）
     */
    public ContactDetailVO getContactDetailInfo(String externalUserid) {
        ContactDetailVO result = new ContactDetailVO();
        result.setTags("");
        result.setCreateTime(null); // createTime 默认 null，获取不到就保持 null

        try {
            JSONObject contactDetail = getContactDetail(externalUserid);
            if (contactDetail == null) {
                return null;
            }

            // 解析 follow_user 数组中的标签和添加时间
            // 响应格式: {"errcode":0,"errmsg":"ok","external_contact":{"external_userid":"...","name":"..."},"follow_user":[{"userid":"...","createtime":1234567890,"tags":[{"tag_id":"...","tag_name":"VIP","type":1}]}]}
            JSONObject followUser = null;
            if (!contactDetail.containsKey("follow_user")) {
                log.warn("客户详情中没有 follow_user 字段: externalUserid={}", externalUserid);
            } else {
                // follow_user 是一个数组，取第一个元素
                com.alibaba.fastjson.JSONArray followUsers = contactDetail.getJSONArray("follow_user");
                if (followUsers == null || followUsers.isEmpty()) {
                    log.warn("follow_user 数组为空: externalUserid={}", externalUserid);
                } else {
                    followUser = followUsers.getJSONObject(0);
                }
            }

            // 如果成功获取到 followUser，解析标签和添加时间
            if (followUser != null) {

            // 解析添加时间（createtime 字段）
            if (followUser.containsKey("createtime")) {
                Long createTime = followUser.getLong("createtime");
                if (createTime != null && createTime > 0) {
                    result.setCreateTime(createTime);
                    log.info("成功获取添加时间: externalUserid={}, createTime={}", externalUserid, createTime);
                }
            } else {
                log.warn("follow_user 中没有 createtime 字段: externalUserid={}", externalUserid);
            }

            // 解析标签
            if (followUser.containsKey("tags")) {
                com.alibaba.fastjson.JSONArray tagsArray = followUser.getJSONArray("tags");
                if (tagsArray != null && !tagsArray.isEmpty()) {
                    // 使用 "|" 拼接所有 tag_name
                    StringBuilder tagsBuilder = new StringBuilder();
                    for (int i = 0; i < tagsArray.size(); i++) {
                        JSONObject tag = tagsArray.getJSONObject(i);
                        String tagName = tag.getString("tag_name");
                        if (tagName != null && !tagName.isEmpty()) {
                            if (tagsBuilder.length() > 0) {
                                tagsBuilder.append("|");
                            }
                            tagsBuilder.append(tagName);
                        }
                    }
                    result.setTags(tagsBuilder.toString());
                    log.info("成功获取客户标签: externalUserid={}, tags={}", externalUserid, result.getTags());
                }
            } else {
                log.info("客户没有标签: externalUserid={}", externalUserid);
            }
            } // end of if (followUser != null)

            return result;

        } catch (Exception e) {
            log.error("获取客户详情信息失败: externalUserid={}", externalUserid, e);
            return result; // 返回默认值
        }
    }

    /**
     * 获取客户列表（带游标返回，用于分页查询）
     * 文档: https://developer.work.weixin.qq.com/document/path/99434
     * @param cursor 游标，首次请求可不填或填空字符串
     * @param limit 返回的最大数据量，默认为1000，最大为1000
     * @return 分页结果（包含客户列表和下一次请求的游标）
     */
    public ContactListResult getContactListWithCursor(String cursor, Integer limit) {
        ContactListResult result = new ContactListResult();
        List<WecomContactVO> resultList = new ArrayList<>();
        result.setContacts(resultList);
        result.setHasMore(false);

        try {
            String accessToken = getAccessToken();
            String url = String.format(
                "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/contact_list?access_token=%s",
                accessToken
            );

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("cursor", cursor == null ? "" : cursor);
            requestBody.put("limit", limit == null ? 1000 : limit);

            log.info("调用企微获取客户列表接口, cursor={}, limit={}, URL={}", cursor, limit, url);
            log.info("请求体: {}", requestBody.toJSONString());

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody.toJSONString(), MediaType.parse("application/json")))
                .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                // 记录响应状态码
                int statusCode = response.code();
                String statusMessage = response.message();

                if (response.body() == null) {
                    log.error("获取客户列表失败: 响应体为空, statusCode={}, statusMessage={}", statusCode, statusMessage);
                    return result;
                }

                String body = response.body().string();
//                log.info("企微接口响应: statusCode={}, response={}", statusCode, body);

                JSONObject json = JSONObject.parseObject(body);

                if (json.getIntValue("errcode") != 0) {
                    int errcode = json.getIntValue("errcode");
                    String errmsg = json.getString("errmsg");
                    log.error("获取客户列表失败: errcode={}, errmsg={}, 完整响应={}", errcode, errmsg, body);
                    return result;
                }

                // 解析 next_cursor（用于下次请求）
                String nextCursor = json.getString("next_cursor");
                result.setNextCursor(nextCursor);
                result.setHasMore(nextCursor != null && !nextCursor.isEmpty());

                // 解析 info_list 数组
                if (!json.containsKey("info_list")) {
                    log.warn("响应中没有 info_list 字段");
                    return result;
                }

                com.alibaba.fastjson.JSONArray infoList = json.getJSONArray("info_list");
                if (infoList == null || infoList.isEmpty()) {
                    log.info("客户列表为空");
                    return result;
                }

                // 提取 external_userid 和 add_time
                for (int i = 0; i < infoList.size(); i++) {
                    JSONObject contact = infoList.getJSONObject(i);

                    WecomContactVO contactVO = new WecomContactVO();

                    // 提取 is_customer 字段
                    Boolean isCustomer = contact.getBoolean("is_customer");
                    contactVO.setIsCustomer(isCustomer);

                    // 提取 external_userid（仅当 is_customer=true 时存在）
                    if (isCustomer != null && isCustomer && contact.containsKey("external_userid")) {
                        String externalUserid = contact.getString("external_userid");
                        contactVO.setExternalUserid(externalUserid);
                    }

                    // 提取 add_time
                    if (contact.containsKey("add_time")) {
                        Long addTime = contact.getLong("add_time");
                        contactVO.setAddTime(addTime);
                    }

                    // 提取 user_userid（contact_list 接口返回的跟进成员userid字段）
                    String followUserid = contact.getString("user_userid");
                    if (followUserid == null || followUserid.isEmpty()) {
                        // 兼容旧字段名
                        followUserid = contact.getString("follow_userid");
                    }
                    contactVO.setFollowUserid(followUserid);

                    resultList.add(contactVO);
                }

                log.info("成功获取客户列表: 数量={}, next_cursor={}, hasMore={}",
                        resultList.size(), nextCursor, result.isHasMore());

                return result;

            } catch (Exception e) {
                log.error("调用企微获取客户列表接口异常: URL={}, 异常信息={}", url, e.getMessage(), e);
                return result;
            }

        } catch (Exception e) {
            log.error("获取客户列表失败", e);
            return result;
        }
    }

    /**
     * 获取客户列表（原有方法，保持兼容）
     * 文档: https://developer.work.weixin.qq.com/document/path/99434
     * @param cursor 游标，首次请求可不填或填空字符串
     * @param limit 返回的最大数据量，默认为1000
     * @return 客户列表（包含external_userid和add_time）
     */
    public List<WecomContactVO> getContactList(String cursor, Integer limit) {
        List<WecomContactVO> resultList = new ArrayList<>();

        try {
            String accessToken = getAccessToken();
            String url = String.format(
                "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/contact_list?access_token=%s",
                accessToken
            );

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("cursor", cursor == null ? "" : cursor);
            requestBody.put("limit", limit == null ? 1000 : limit);

            log.info("调用企微获取客户列表接口, cursor={}, limit={}, URL={}", cursor, limit, url);
            log.info("请求体: {}", requestBody.toJSONString());

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody.toJSONString(), MediaType.parse("application/json")))
                .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                // 记录响应状态码
                int statusCode = response.code();
                String statusMessage = response.message();

                if (response.body() == null) {
                    log.error("获取客户列表失败: 响应体为空, statusCode={}, statusMessage={}", statusCode, statusMessage);
                    return resultList;
                }

                String body = response.body().string();
//                log.info("企微接口响应: statusCode={}, response={}", statusCode, body);

                JSONObject json = JSONObject.parseObject(body);

                if (json.getIntValue("errcode") != 0) {
                    int errcode = json.getIntValue("errcode");
                    String errmsg = json.getString("errmsg");
                    log.error("获取客户列表失败: errcode={}, errmsg={}, 完整响应={}", errcode, errmsg, body);
                    return resultList;
                }

                // 解析 info_list 数组
                if (!json.containsKey("info_list")) {
                    log.warn("响应中没有 info_list 字段");
                    return resultList;
                }

                com.alibaba.fastjson.JSONArray infoList = json.getJSONArray("info_list");
                if (infoList == null || infoList.isEmpty()) {
                    log.info("客户列表为空");
                    return resultList;
                }

                // 提取 external_userid 和 add_time
                for (int i = 0; i < infoList.size(); i++) {
                    JSONObject contact = infoList.getJSONObject(i);

                    WecomContactVO contactVO = new WecomContactVO();

                    // 提取 is_customer 字段
                    Boolean isCustomer = contact.getBoolean("is_customer");
                    contactVO.setIsCustomer(isCustomer);

                    // 提取 external_userid（仅当 is_customer=true 时存在）
                    if (isCustomer != null && isCustomer && contact.containsKey("external_userid")) {
                        String externalUserid = contact.getString("external_userid");
                        contactVO.setExternalUserid(externalUserid);
                    }

                    // 提取 add_time
                    if (contact.containsKey("add_time")) {
                        Long addTime = contact.getLong("add_time");
                        contactVO.setAddTime(addTime);
                    }

                    // 提取 user_userid（contact_list 接口返回的跟进成员userid字段）
                    String followUserid = contact.getString("user_userid");
                    if (followUserid == null || followUserid.isEmpty()) {
                        // 兼容旧字段名
                        followUserid = contact.getString("follow_userid");
                    }
                    contactVO.setFollowUserid(followUserid);

                    resultList.add(contactVO);
                }

                log.info("成功获取客户列表: 数量={}, next_cursor={}",
                        resultList.size(), json.getString("next_cursor"));

                return resultList;

            } catch (Exception e) {
                log.error("调用企微获取客户列表接口异常: URL={}, 异常信息={}", url, e.getMessage(), e);
                return resultList;
            }

        } catch (Exception e) {
            log.error("获取客户列表失败", e);
            return resultList;
        }
    }

    /**
     * 获取所有客户列表（自动分页，直到获取完所有数据）
     * 文档: https://developer.work.weixin.qq.com/document/path/99434
     * @param maxTotal 最大获取总数，用于限制总数，防止无限循环。传null或0表示不限制
     * @param pageSize 每页数量，默认1000，最大1000
     * @return 所有客户列表
     */
    public List<WecomContactVO> getAllContactList(Integer maxTotal, Integer pageSize) {
        List<WecomContactVO> allContacts = new ArrayList<>();
        String cursor = null;
        int pageSizeValue = (pageSize == null || pageSize <= 0) ? 1000 : Math.min(pageSize, 1000);
        int maxTotalValue = (maxTotal == null || maxTotal <= 0) ? Integer.MAX_VALUE : maxTotal;

        log.info("开始分页获取所有客户列表, 每页数量={}, 最大总数={}", pageSizeValue, maxTotalValue == Integer.MAX_VALUE ? "不限制" : maxTotalValue);

        int pageNum = 0;
        do {
            pageNum++;
            log.info("正在获取第 {} 页数据, 当前cursor={}", pageNum, cursor);

            ContactListResult result = getContactListWithCursor(cursor, pageSizeValue);
            List<WecomContactVO> contacts = result.getContacts();

            if (contacts != null && !contacts.isEmpty()) {
                // 检查是否超过最大总数
                if (allContacts.size() + contacts.size() > maxTotalValue) {
                    int remain = maxTotalValue - allContacts.size();
                    if (remain > 0) {
                        allContacts.addAll(contacts.subList(0, remain));
                    }
                    log.info("已达到最大总数限制 {}, 停止获取", maxTotalValue);
                    break;
                }
                allContacts.addAll(contacts);
                log.info("第 {} 页获取到 {} 条数据, 累计 {} 条", pageNum, contacts.size(), allContacts.size());
            } else {
                log.info("第 {} 页没有数据，停止获取", pageNum);
                break;
            }

            // 更新游标
            cursor = result.getNextCursor();

            // 如果没有更多数据，退出循环
            if (!result.isHasMore()) {
                log.info("没有更多数据，停止获取");
                break;
            }

        } while (true);

        log.info("分页获取完成，共获取 {} 条客户数据", allContacts.size());
        return allContacts;
    }

    /**
     * 设置客户企业备注
     * 将 external_userid 放到企业信息里（remark_company字段）
     */
    public void setCustomerRemarkCompany(String userid, String externalUserid,String corpId) throws Exception {
        if (userid == null || userid.isEmpty()) {
            log.warn("设置客户企业备注失败: userid为空, 跳过. externalUserid={}", externalUserid);
            return;
        }
        if (externalUserid == null || externalUserid.isEmpty()) {
            log.warn("设置客户企业备注失败: externalUserid为空, 跳过. userid={}", userid);
            return;
        }
        String accessToken = getAccessToken();
        String url = String.format(
            "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/remark?access_token=%s",
            accessToken
        );

        JSONObject requestBody = new JSONObject();
        requestBody.put("userid", userid);
        requestBody.put("external_userid", externalUserid);
        requestBody.put("remark_company",corpId);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody.toJSONString(),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("设置客户企业备注失败: 响应体为空");
            }
            String body = response.body().string();
            JSONObject json = JSONObject.parseObject(body);

            if (json.getIntValue("errcode") != 0) {
                log.error("设置客户企业备注失败: {}", json.getString("errmsg"));
                throw new RuntimeException("设置客户企业备注失败: " + json.getString("errmsg"));
            }

            log.info("成功设置客户企业备注: userid={}, externalUserid={}, remark_company={}",
                    userid, externalUserid, corpId);
        }
    }

    /**
     * 上传临时素材（图片）
     * 文档: https://developer.work.weixin.qq.com/document/path/90253
     * @param imagePath 图片路径（优先按文件系统路径读取，不存在则按classpath路径读取）
     * @return media_id（仅三天内有效）
     */
    public String uploadMedia(String imagePath) throws Exception {
        String accessToken = getAccessToken();
        String url = String.format(
            "https://qyapi.weixin.qq.com/cgi-bin/media/upload?access_token=%s&type=image",
            accessToken
        );

        byte[] fileBytes;
        String fileName;
        File file = new File(imagePath);
        if (file.exists() && file.isFile()) {
            // 文件系统路径
            fileBytes = Files.readAllBytes(file.toPath());
            fileName = file.getName();
        } else {
            // classpath 路径（如 img/xxx.jpg）
            ClassPathResource resource = new ClassPathResource(imagePath);
            if (!resource.exists()) {
                throw new RuntimeException("群发图片文件不存在: " + imagePath);
            }
            try (InputStream is = resource.getInputStream()) {
                fileBytes = StreamUtils.copyToByteArray(is);
            }
            fileName = resource.getFilename();
        }

        // 根据文件后缀推断MediaType
        String lowerName = fileName.toLowerCase();
        String mime = lowerName.endsWith(".png") ? "image/png" : "image/jpeg";

        RequestBody fileBody = RequestBody.create(fileBytes, MediaType.parse(mime));
        MultipartBody multipartBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("media", fileName, fileBody)
            .build();

        Request request = new Request.Builder().url(url).post(multipartBody).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("上传临时素材失败: 响应体为空");
            }
            String body = response.body().string();
            JSONObject json = JSONObject.parseObject(body);

            if (json.getIntValue("errcode") != 0) {
                log.error("上传临时素材失败: {}", json.getString("errmsg"));
                throw new RuntimeException("上传临时素材失败: " + json.getString("errmsg"));
            }

            String mediaId = json.getString("media_id");
            log.info("上传临时素材成功: file={}, media_id={}, created_at={}",
                    fileName, mediaId, json.getString("created_at"));
            return mediaId;
        }
    }

    /**
     * 创建企业微信群发任务（单聊群发）
     * 文档: https://developer.work.weixin.qq.com/document/path/92135
     * @param externalUserids 客户external_userid列表
     * @param sender 发送者企微成员ID
     * @param content 文本内容
     * @param mediaId 图片素材media_id（可为null表示不附带图片）
     * @return 响应JSON（包含msgid和fail_list）
     */
    public JSONObject addMsgTemplate(List<String> externalUserids, String sender,
                                     String content, String mediaId) throws Exception {
        String accessToken = getAccessToken();
        String url = String.format(
            "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/add_msg_template?access_token=%s",
            accessToken
        );

        JSONObject requestBody = new JSONObject();
        requestBody.put("chat_type", "single");
        requestBody.put("external_userid", externalUserids);
        requestBody.put("sender", sender);
        requestBody.put("allow_select", false);

        JSONObject text = new JSONObject();
        text.put("content", content);
        requestBody.put("text", text);

        if (mediaId != null && !mediaId.isEmpty()) {
            JSONObject attachment = new JSONObject();
            attachment.put("msgtype", "image");
            JSONObject image = new JSONObject();
            image.put("media_id", mediaId);
            attachment.put("image", image);
            requestBody.put("attachments", Collections.singletonList(attachment));
        }

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(requestBody.toJSONString(), MediaType.parse("application/json")))
            .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("创建群发任务失败: 响应体为空");
            }
            String body = response.body().string();
            log.info("创建群发任务响应: 发送者={}, 客户数={}, response={}",
                    sender, externalUserids.size(), body);

            JSONObject json = JSONObject.parseObject(body);
            if (json.getIntValue("errcode") != 0) {
                log.error("创建群发任务失败: errcode={}, errmsg={}",
                        json.getIntValue("errcode"), json.getString("errmsg"));
                throw new RuntimeException("创建群发任务失败: " + json.getString("errmsg"));
            }
            return json;
        }
    }

    /**
     * 检查 externalUserid 是否已存在
     */
    public boolean existsByExternalUserid(String externalUserid) {
        LambdaQueryWrapper<AiFirmWechatForm> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiFirmWechatForm::getExternalUserid, externalUserid)
                    .eq(AiFirmWechatForm::getIsDel, 0);
        return aiFirmWechatFormMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 通过订单号(otid)调用OMS接口获取客户ID
     * @param otid 订单号
     * @return 客户ID，如果获取失败返回null
     */
    public String getCustomerIdByOtid(String otid) {
        if (otid == null || otid.trim().isEmpty()) {
            log.warn("订单号otid为空，无法获取客户ID");
            return null;
        }

        try {
            // 构建请求URL - 根据实际OMS接口路径调整
            String url = String.format("%s/orders/getCustomerIdByOtid?otid=%s",
                pdOmsConfig.getAgentUrl(), otid);

            log.info("调用OMS接口获取客户ID, URL: {}", url);

            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", pdOmsConfig.getToken())
                .addHeader("Content-Type", "application/json")
                .get()
                .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.body() == null) {
                    log.error("调用OMS接口失败: 响应体为空");
                    return null;
                }

                String body = response.body().string();
                log.info("OMS接口响应: {}", body);

                JSONObject json = JSONObject.parseObject(body);

                // 解析嵌套的JSON响应
                // 响应格式: {"code":200,"msg":"success","success":true,"data":{"code":200,"msg":"success","success":true,"data":null}}
                if (json.getIntValue("code") != 200 || !json.getBooleanValue("success")) {
                    log.error("调用OMS接口失败: code={}, msg={}",
                        json.getInteger("code"), json.getString("msg"));
                    return null;
                }

                // 获取内层data
                JSONObject dataObj = json.getJSONObject("data");
                if (dataObj == null) {
                    log.warn("OMS接口返回的data为空");
                    return null;
                }

                // 检查内层响应
                if (dataObj.getIntValue("code") != 200 || !dataObj.getBooleanValue("success")) {
                    log.error("OMS接口内层响应失败: code={}, msg={}",
                        dataObj.getInteger("code"), dataObj.getString("msg"));
                    return null;
                }

                // 获取实际的客户ID数据
                Object customerIdData = dataObj.get("data");
                if (customerIdData == null) {
                    log.warn("OMS接口返回的客户ID数据为空");
                    return null;
                }

                String customerId = customerIdData.toString();
                log.info("成功获取客户ID: otid={}, customerId={}", otid, customerId);
                return customerId;

            } catch (Exception e) {
                log.error("调用OMS接口异常, otid={}", otid, e);
                return null;
            }

        } catch (Exception e) {
            log.error("获取客户ID失败, otid={}", otid, e);
            return null;
        }
    }

}
