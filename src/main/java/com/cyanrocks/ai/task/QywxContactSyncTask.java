package com.cyanrocks.ai.task;

import com.cyanrocks.ai.dao.entity.WecomContacts;
import com.cyanrocks.ai.dao.mapper.WecomContactsMapper;
import com.cyanrocks.ai.service.QywxService;
import com.cyanrocks.ai.service.WecomContactsService;
import com.cyanrocks.ai.vo.ContactDetailVO;
import com.cyanrocks.ai.vo.WecomContactVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业微信客户同步定时任务
 * 每5分钟执行一次，同步企微客户数据到本地数据库
 * @author yangshihao
 */
@Slf4j
@Component
public class QywxContactSyncTask {

    @Autowired
    private QywxService qywxService;

    @Autowired
    private WecomContactsService wecomContactsService;

    /**
     * 同步客户数据的跟进人ID
     * TODO: 根据实际情况修改
     */
    private static final String FOLLOW_USERID = "peidi2";

    /**
     * 每次同步的最大数量
     */
    private static final int SYNC_LIMIT = 100;
    @Autowired
    private WecomContactsMapper wecomContactsMapper;

    /**
     * 每5分钟执行一次同步
     * cron表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void syncContacts() {
        log.info("========== 开始同步企微客户数据 ==========");
        long startTime = System.currentTimeMillis();

        try {
            // 分页获取所有客户（最多SYNC_LIMIT条）
            List<WecomContactVO> allContacts = qywxService.getContactList(null, 100);

            if (allContacts == null || allContacts.isEmpty()) {
                log.info("没有获取到客户数据，跳过同步");
                return;
            }

            log.info("获取到 {} 条客户数据，开始处理...", allContacts.size());

            int newCount = 0;      // 新增数量
            int updateCount = 0;   // 更新跟进人数量
            int skipCount = 0;     // 跳过数量（非客户 + 已存在）
            int errorCount = 0;    // 错误数量

            for (WecomContactVO contact : allContacts) {
                try {
                    // 只处理真实客户（is_customer=true）
                    if (contact.getIsCustomer() == null || !contact.getIsCustomer()) {
                        skipCount++;
                        continue;
                    }

                    String externalUserid = contact.getExternalUserid();
                    if (externalUserid == null || externalUserid.isEmpty()) {
                        skipCount++;
                        continue;
                    }

                    // 检查是否已存在，存在则跳过
                    WecomContacts existingContact = wecomContactsService.getByExternalUserid(externalUserid);
                    if (existingContact != null) {
                        //如果为同平台客服接待就跳过
                        if (existingContact.getFollowUserid().equals(contact.getFollowUserid())) {
                            skipCount++;
                            log.debug("客户已存在，跳过: externalUserid={}", externalUserid);
                            continue;
                        }
                        //否则修改用户的跟进人
                        existingContact.setFollowUserid(contact.getFollowUserid());
                        existingContact.setUpdatedAt(LocalDateTime.now());
                        int i = wecomContactsMapper.updateById(existingContact);
                        if (i > 0) {
                            qywxService.setCustomerRemarkCompany(contact.getFollowUserid(), externalUserid, externalUserid.length() > 20
                                    ? externalUserid.substring(externalUserid.length() - 20)
                                    : externalUserid);
                            updateCount++;
                            log.debug("其他平台跟进客户,客户FollowUserId已更新={}", contact.getFollowUserid());
                            continue;
                        }

                    }

                    // 获取客户详情（标签和添加时间）
                    ContactDetailVO contactDetail = qywxService.getContactDetailInfo(externalUserid);

                    // 新增记录
                    WecomContacts wecomContacts = new WecomContacts();
                    wecomContacts.setExternalUserid(externalUserid);
                    wecomContacts.setTags(contactDetail.getTags());
                    if (contactDetail.getCreateTime() != null) {
                        wecomContacts.setAddTime(contactDetail.getCreateTime());
                    }
                    wecomContacts.setFollowUserid(contact.getFollowUserid() == null ? "" : contact.getFollowUserid());
                    wecomContacts.setState("lost");  // 默认状态为lost
                    wecomContacts.setCreatedAt(LocalDateTime.now());
                    String remarkCompany = externalUserid.length() > 20
                            ? externalUserid.substring(externalUserid.length() - 20)
                            : externalUserid;
                    wecomContacts.setCompanyId(remarkCompany);
                    wecomContactsService.insertWecomContact(wecomContacts);
                    newCount++;

                    // 设置客户企业备注（截取后20位）

                    qywxService.setCustomerRemarkCompany(contact.getFollowUserid(), externalUserid, remarkCompany);

                    log.debug("新增客户: externalUserid={}", externalUserid);

                } catch (Exception e) {
                    errorCount++;
                    log.error("处理客户数据失败: externalUserid={}, error={}",
                            contact.getExternalUserid(), e.getMessage(), e);
                }
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("========== 同步完成 ========== 总数={}, 新增={}, 更新跟进人={}, 跳过={}, 错误={}, 耗时={}ms",
                    allContacts.size(), newCount, updateCount, skipCount, errorCount, costTime);

        } catch (Exception e) {
            log.error("同步企微客户数据失败", e);
        }
    }
}
