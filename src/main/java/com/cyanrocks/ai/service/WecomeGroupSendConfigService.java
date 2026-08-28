package com.cyanrocks.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyanrocks.ai.dao.entity.WecomeGroupSendConfig;
import com.cyanrocks.ai.dao.mapper.WecomeGroupSendConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企微群发策略配置服务类
 * 核心功能：从数据库动态加载群发策略配置
 * @author yangshihao
 */
@Slf4j
@Service
public class WecomeGroupSendConfigService {

    @Autowired
    private WecomeGroupSendConfigMapper wecomeGroupSendConfigMapper;

    // ==================== 查询方法 ====================

    /**
     * 根据ID查询配置
     * @param id 配置ID
     * @return 配置实体
     */
    public WecomeGroupSendConfig getById(Long id) {
        return wecomeGroupSendConfigMapper.selectById(id);
    }

    /**
     * 查询所有有效配置（未删除的）
     * @return 配置列表
     */
    public List<WecomeGroupSendConfig> listValid() {
        LambdaQueryWrapper<WecomeGroupSendConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WecomeGroupSendConfig::getIsDel, 0)
                .orderByDesc(WecomeGroupSendConfig::getCreatedAt);
        return wecomeGroupSendConfigMapper.selectList(queryWrapper);
    }

    /**
     * 查询所有已启用的策略配置（enabled=1 且未删除）
     * 定时任务使用此方法获取需要执行的策略
     * @return 已启用的策略列表
     */
    public List<WecomeGroupSendConfig> listEnabled() {
        LambdaQueryWrapper<WecomeGroupSendConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WecomeGroupSendConfig::getEnabled, 1)
                .eq(WecomeGroupSendConfig::getIsDel, 0)
                .orderByDesc(WecomeGroupSendConfig::getCreatedAt);
        return wecomeGroupSendConfigMapper.selectList(queryWrapper);
    }

    /**
     * 根据daysAgo查询策略
     * @param daysAgo 天数
     * @return 匹配的策略列表
     */
    public List<WecomeGroupSendConfig> listByDaysAgo(Integer daysAgo) {
        LambdaQueryWrapper<WecomeGroupSendConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WecomeGroupSendConfig::getDaysAgo, daysAgo)
                .eq(WecomeGroupSendConfig::getIsDel, 0)
                .orderByDesc(WecomeGroupSendConfig::getCreatedAt);
        return wecomeGroupSendConfigMapper.selectList(queryWrapper);
    }

    // ==================== 增删改方法 ====================

    /**
     * 新增策略配置
     * @param config 配置实体
     * @return 新增后的配置（含ID）
     */
    public WecomeGroupSendConfig insert(WecomeGroupSendConfig config) {
        try {
            LocalDateTime now = LocalDateTime.now();
            if (config.getCreatedAt() == null) {
                config.setCreatedAt(now);
            }
            if (config.getUpdatedAt() == null) {
                config.setUpdatedAt(now);
            }
            if (config.getIsDel() == null) {
                config.setIsDel(0);
            }
            if (config.getEnabled() == null) {
                config.setEnabled(1);
            }
            if (config.getDaysAgo() == null) {
                config.setDaysAgo(7);
            }
            wecomeGroupSendConfigMapper.insert(config);
            log.info("新增群发策略成功，id={}, daysAgo={}", config.getId(), config.getDaysAgo());
            return config;
        } catch (Exception e) {
            log.error("新增群发策略失败", e);
            return null;
        }
    }

    /**
     * 更新策略配置
     * 支持动态修改daysAgo等核心参数，修改后下次定时任务执行时生效
     * @param config 配置实体
     * @return 更新后的配置
     */
    public WecomeGroupSendConfig update(WecomeGroupSendConfig config) {
        try {
            config.setUpdatedAt(LocalDateTime.now());
            wecomeGroupSendConfigMapper.updateById(config);
            log.info("更新群发策略成功，id={}, daysAgo={}", config.getId(), config.getDaysAgo());
            return config;
        } catch (Exception e) {
            log.error("更新群发策略失败", e);
            return null;
        }
    }

    /**
     * 单独更新daysAgo字段（核心字段快捷修改）
     * @param id 配置ID
     * @param daysAgo 天数
     * @return 是否成功
     */
    public boolean updateDaysAgo(Long id, Integer daysAgo) {
        try {
            WecomeGroupSendConfig config = wecomeGroupSendConfigMapper.selectById(id);
            if (config == null) {
                return false;
            }
            config.setDaysAgo(daysAgo);
            config.setUpdatedAt(LocalDateTime.now());
            wecomeGroupSendConfigMapper.updateById(config);
            log.info("更新daysAgo成功，id={}, daysAgo={}", id, daysAgo);
            return true;
        } catch (Exception e) {
            log.error("更新daysAgo失败，id={}, daysAgo={}", id, daysAgo, e);
            return false;
        }
    }

    /**
     * 单独更新图片URL
     * @param id 配置ID
     * @param imageUrl 图片URL
     * @return 是否成功
     */
    public boolean updateImageUrl(Long id, String imageUrl) {
        try {
            WecomeGroupSendConfig config = wecomeGroupSendConfigMapper.selectById(id);
            if (config == null) {
                return false;
            }
            config.setImageUrl(imageUrl);
            config.setUpdatedAt(LocalDateTime.now());
            wecomeGroupSendConfigMapper.updateById(config);
            log.info("更新图片URL成功，id={}, imageUrl={}", id, imageUrl);
            return true;
        } catch (Exception e) {
            log.error("更新图片URL失败，id={}", id, e);
            return false;
        }
    }

    /**
     * 根据ID删除配置（逻辑删除）
     * 更新is_del=1，不物理删除数据
     * @param id 配置ID
     * @return 是否成功
     */
    public boolean deleteById(Long id) {
        try {
            WecomeGroupSendConfig config = wecomeGroupSendConfigMapper.selectById(id);
            if (config != null) {
                config.setIsDel(1);
                config.setUpdatedAt(LocalDateTime.now());
                wecomeGroupSendConfigMapper.updateById(config);
                log.info("删除群发策略成功，id={}", id);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("删除群发策略失败，id={}", id, e);
            return false;
        }
    }
}
