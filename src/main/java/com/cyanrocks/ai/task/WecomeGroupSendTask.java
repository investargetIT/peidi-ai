package com.cyanrocks.ai.task;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.dao.entity.WecomeGroupSendConfig;
import com.cyanrocks.ai.dao.entity.WecomContacts;
import com.cyanrocks.ai.service.WecomeGroupSendConfigService;
import com.cyanrocks.ai.service.QywxService;
import com.cyanrocks.ai.service.WecomContactsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 企业微信客户群发定时任务
 * 每天下午15:00执行，遍历所有启用的群发策略配置执行群发
 * 流程: 从数据库加载策略 -> 查询目标客户 -> 上传临时素材 -> 创建群发任务
 * 核心字段：days_ago（客户入群N天后触发群发）
 * @author yangshihao
 */
@Slf4j
@Component
public class WecomeGroupSendTask {

    @Autowired
    private QywxService qywxService;

    @Autowired
    private WecomContactsService wecomContactsService;

    @Autowired
    private WecomeGroupSendConfigService wecomeGroupSendConfigService;

    /**
     * 每天下午3点执行
     * cron表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 15 * * ?", zone = "Asia/Shanghai")
    public void sendGroupMessage() {
        log.info("========== 开始执行企微客户群发任务 ==========");
        long startTime = System.currentTimeMillis();

        // 1. 从数据库查询所有已启用的群发策略（实时读取，无需重启服务）
        List<WecomeGroupSendConfig> groupSendConfigs = wecomeGroupSendConfigService.listEnabled();

        if (groupSendConfigs.isEmpty()) {
            log.info("没有找到任何已启用的群发策略，跳过执行");
            return;
        }

        log.info("找到 {} 条已启用的群发策略，开始遍历执行", groupSendConfigs.size());

        int successCount = 0;
        int failCount = 0;

        // 2. 遍历每条策略执行群发
        for (WecomeGroupSendConfig config : groupSendConfigs) {
            try {
                boolean success = executeSingleStrategy(config);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("执行群发策略失败，id={}", config.getId(), e);
            }
        }

        long costTime = System.currentTimeMillis() - startTime;
        log.info("========== 企微群发任务完成 ========== 策略总数={}, 成功={}, 失败={}, 耗时={}ms",
                groupSendConfigs.size(), successCount, failCount, costTime);
    }

    /**
     * 执行单条群发策略
     * @param config 策略配置
     * @return 是否成功
     */
    private boolean executeSingleStrategy(WecomeGroupSendConfig config) {
        Long id = config.getId();
        Integer daysAgo = config.getDaysAgo();
        String sender = config.getSender();

        log.info("开始执行群发策略: id={}, daysAgo={}, sender={}", id, daysAgo, sender);

        // 校验核心字段 daysAgo
        if (daysAgo == null || daysAgo < 0) {
            log.warn("策略 {} daysAgo配置无效（{}），跳过", id, daysAgo);
            return false;
        }
        if (sender == null || sender.isEmpty()) {
            log.warn("策略 {} 未配置sender，跳过", id);
            return false;
        }

        // 计算目标日期: N天前当天（核心逻辑：daysAgo天前当天加入的客户）
        LocalDate targetDate = LocalDate.now().minusDays(daysAgo);
        LocalDateTime dayStart = LocalDateTime.of(targetDate, LocalTime.MIN);
        LocalDateTime dayEnd = LocalDateTime.of(targetDate, LocalTime.MAX);

        // 查询目标客户
        List<WecomContacts> contacts = wecomContactsService
                .findByFollowUseridAndCreatedAtBetween(sender, dayStart, dayEnd);

        if (contacts == null || contacts.isEmpty()) {
            log.info("策略 {}: 没有找到 {} 于 {} 加入的客户，跳过群发", id, sender, targetDate);
            return true;
        }

        List<String> externalUserids = contacts.stream()
                .map(WecomContacts::getExternalUserid)
                .filter(uid -> uid != null && !uid.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (externalUserids.isEmpty()) {
            log.info("策略 {}: 目标客户列表为空（external_userid均为空），跳过群发", id);
            return true;
        }

        log.info("策略 {}: 查询到 {} 于 {} 加入的客户 {} 人，开始群发...",
                id, sender, targetDate, externalUserids.size());

        // 上传临时素材获取media_id（图片URL已存储在数据库中）
        String mediaId = null;
        String imageUrl = config.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            try {
                mediaId = qywxService.uploadMedia(imageUrl);
            } catch (Exception e) {
                log.error("策略 {}: 上传图片失败，imageUrl={}", id, imageUrl, e);
            }
        } else {
            log.warn("策略 {}: 未配置群发图片(imageUrl)，将只发送文本消息", id);
        }

        // 创建群发任务
        String content = config.getContent();
        try {
            JSONObject result = qywxService.addMsgTemplate(externalUserids, sender, content, mediaId);

            // 处理失败列表
            JSONArray failList = result.getJSONArray("fail_list");
            int failCustomerCount = (failList == null) ? 0 : failList.size();
            if (failCustomerCount > 0) {
                log.warn("策略 {}: 群发任务存在失败客户 {} 人: {}", id, failCustomerCount, failList.toJSONString());
            }

            log.info("策略 {} 执行完成: 目标日期={}, 发送者={}, daysAgo={}, 目标客户数={}, 失败数={}, msgid={}",
                    id, targetDate, sender, daysAgo, externalUserids.size(), failCustomerCount,
                    result.getString("msgid"));
        } catch (Exception e) {
            log.error("策略 {}: 创建群发任务失败", id, e);
            return false;
        }

        return true;
    }
}
