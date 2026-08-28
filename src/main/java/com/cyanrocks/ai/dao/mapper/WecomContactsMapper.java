package com.cyanrocks.ai.dao.mapper;

// import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyanrocks.ai.dao.entity.WecomContacts;
import org.apache.ibatis.annotations.Mapper;

/**
 * 企微联系人Mapper
 * @author yangshihao
 */
@Mapper
// @DS("slave")  // TODO: 需要添加 dynamic-datasource 依赖后启用
public interface WecomContactsMapper extends BaseMapper<WecomContacts> {
}
