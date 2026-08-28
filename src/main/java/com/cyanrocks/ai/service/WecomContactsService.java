package com.cyanrocks.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyanrocks.ai.dao.entity.WecomContacts;
import com.cyanrocks.ai.dao.mapper.WecomContactsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企微联系人服务类
 * 使用 slave 数据源（peidi_wecom数据库）
 * @author yangshihao
 */
@Slf4j
@Service
public class WecomContactsService {

    @Autowired
    private WecomContactsMapper wecomContactsMapper;

    /**
     * 插入企微联系人
     * @param wecomContacts 联系人信息
     * @return 插入成功返回true，失败返回false
     */
    public WecomContacts insertWecomContact(WecomContacts wecomContacts) {
        try {
            // 设置创建时间和更新时间
            LocalDateTime now = LocalDateTime.now();
            if (wecomContacts.getCreatedAt() == null) {
                wecomContacts.setCreatedAt(now);
            }
            if (wecomContacts.getUpdatedAt() == null) {
                wecomContacts.setUpdatedAt(now);
            }
            
            // 设置默认状态
            if (wecomContacts.getState() == null || wecomContacts.getState().isEmpty()) {
                wecomContacts.setState("active");
            }
            
            int result = wecomContactsMapper.insert(wecomContacts);
            log.info("插入企微联系人成功，external_userid: {}, customer_id: {}", 
                    wecomContacts.getExternalUserid(), wecomContacts.getCustomerId());
            return wecomContacts;
        } catch (Exception e) {
            log.error("插入企微联系人失败", e);
            return null;
        }
    }

    /**
     * 根据external_userid查询联系人
     * @param externalUserid 企微外部联系人ID
     * @return 联系人信息
     */
    public WecomContacts getByExternalUserid(String externalUserid) {
        LambdaQueryWrapper<WecomContacts> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WecomContacts::getExternalUserid, externalUserid);
        return wecomContactsMapper.selectOne(queryWrapper);
    }

    /**
     * 查询指定跟进人在某时间段内（按created_at）入库的联系人
     * @param followUserid 跟进人企微成员ID
     * @param startTime 开始时间（含）
     * @param endTime 结束时间（含）
     * @return 联系人列表
     */
    public List<WecomContacts> findByFollowUseridAndCreatedAtBetween(String followUserid,
                                                                     LocalDateTime startTime,
                                                                     LocalDateTime endTime) {
        LambdaQueryWrapper<WecomContacts> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WecomContacts::getFollowUserid, followUserid)
                .ge(WecomContacts::getCreatedAt, startTime)
                .le(WecomContacts::getCreatedAt, endTime)
                .isNotNull(WecomContacts::getExternalUserid);
        return wecomContactsMapper.selectList(queryWrapper);
    }



    /**
     * 更新联系人信息
     * @param wecomContacts 联系人信息
     * @return 更新成功返回true，失败返回false
     */
    public WecomContacts updateWecomContact(WecomContacts wecomContacts) {
        try {
            wecomContacts.setUpdatedAt(LocalDateTime.now());
            int result = wecomContactsMapper.updateById(wecomContacts);
            log.info("更新企微联系人成功，id: {}, external_userid: {}", 
                    wecomContacts.getId(), wecomContacts.getExternalUserid());
            return wecomContacts;
        } catch (Exception e) {
            log.error("更新企微联系人失败", e);
            return null;
        }
    }

    /**
     * 插入或更新联系人（根据external_userid判断）
     * @param wecomContacts 联系人信息
     * @return 操作成功返回true，失败返回false
     */
    public WecomContacts insertOrUpdate(WecomContacts wecomContacts) {
        WecomContacts existing = getByExternalUserid(wecomContacts.getExternalUserid());
        if (existing != null) {
            // 已存在，执行更新
            wecomContacts.setId(existing.getId());
            wecomContacts.setCreatedAt(existing.getCreatedAt());
            return updateWecomContact(wecomContacts);
        } else {
            // 不存在，执行插入
            return insertWecomContact(wecomContacts);
        }
    }
}
