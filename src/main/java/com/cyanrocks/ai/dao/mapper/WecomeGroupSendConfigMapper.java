package com.cyanrocks.ai.dao.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyanrocks.ai.dao.entity.WecomeGroupSendConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 企微群发策略配置Mapper
 * @author yangshihao
 */
@Mapper
@DS("slave")
public interface WecomeGroupSendConfigMapper extends BaseMapper<WecomeGroupSendConfig> {
}
