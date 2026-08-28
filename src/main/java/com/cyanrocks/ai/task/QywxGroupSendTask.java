package com.cyanrocks.ai.task;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cyanrocks.ai.config.QywxConfig;
import com.cyanrocks.ai.dao.entity.WecomContacts;
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
 * 每天下午15:00执行，给N天前当天（按created_at）加入的客户发送群发消息
 * 流程: 查询目标客户 -> 上传临时素材 -> 创建群发任务
 * @author yangshihao
 */
@Slf4j
@Component
public class QywxGroupSendTask {

    @Autowired
    private QywxService qywxService;

    @Autowired
    private WecomContactsService wecomContactsService;

    @Autowired
    private QywxConfig qywxConfig;

    /**
     * 每天下午3点执行
     * cron表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 15 * * ?",zone = "Asia/Shanghai")
    public void sendGroupMessage() {
        QywxConfig.GroupSend config = qywxConfig.getGroupSend();
        if (config == null || !config.isEnabled()) {
            log.info("企微群发任务未启用，跳过执行");
            return;
        }

        log.info("========== 开始执行企微客户群发任务 ==========");
        long startTime = System.currentTimeMillis();

        try {
            // 计算目标日期: N天前当天（如今天24号、N=7，则给17号当天加入的客户发送）
            LocalDate targetDate = LocalDate.now().minusDays(config.getDaysAgo());
            LocalDateTime dayStart = LocalDateTime.of(targetDate, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(targetDate, LocalTime.MAX);

            // 查询目标客户
            List<WecomContacts> contacts = wecomContactsService
                    .findByFollowUseridAndCreatedAtBetween(config.getSender(), dayStart, dayEnd);

            if (contacts == null || contacts.isEmpty()) {
                log.info("没有找到 {} 于 {} 加入的客户，跳过群发", config.getSender(), targetDate);
                return;
            }

            List<String> externalUserids = contacts.stream()
                    .map(WecomContacts::getExternalUserid)
                    .filter(id -> id != null && !id.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            if (externalUserids.isEmpty()) {
                log.info("目标客户列表为空（external_userid均为空），跳过群发");
                return;
            }

            log.info("查询到 {} 于 {} 加入的客户 {} 人，开始群发...",
                    config.getSender(), targetDate, externalUserids.size());

            // 上传临时素材获取media_id（media_id仅3天有效，每次群发前重新上传）
            String mediaId = null;
            if (config.getImagePath() != null && !config.getImagePath().isEmpty()) {
                mediaId = qywxService.uploadMedia(config.getImagePath());
            } else {
                log.warn("未配置群发图片路径(image-path)，将只发送文本消息");
            }

            // 创建群发任务
            JSONObject result = qywxService.addMsgTemplate(
                    externalUserids, config.getSender(), config.getContent(), mediaId);

            // 处理失败列表
            JSONArray failList = result.getJSONArray("fail_list");
            int failCount = (failList == null) ? 0 : failList.size();
            if (failCount > 0) {
                log.warn("群发任务存在失败客户 {} 人: {}", failCount, failList.toJSONString());
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("========== 企微群发任务完成 ========== 目标日期={}, 发送者={}, 目标客户数={}, 失败数={}, msgid={}, 耗时={}ms",
                    targetDate, config.getSender(), externalUserids.size(), failCount,
                    result.getString("msgid"), costTime);

        } catch (Exception e) {
            log.error("企微客户群发任务执行失败", e);
        }
    }
}
