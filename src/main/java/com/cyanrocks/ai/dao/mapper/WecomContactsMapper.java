package com.cyanrocks.ai.dao.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyanrocks.ai.dao.entity.WecomContacts;
import org.apache.ibatis.annotations.Mapper;

/**
 * 企微联系人Mapper
 * 使用 slave 数据源（peidi_wecom数据库）
 * @author yangshihao
 */
@Mapper
@DS("slave")
public interface WecomContactsMapper extends BaseMapper<WecomContacts> {
}
