package com.cyanrocks.ai.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiGbiExplain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface AiGbiExplainMapper extends BaseMapper<AiGbiExplain> {

    IPage<AiGbiExplain> getGbiExplainPage(IPage<?> page, @Param("search")String search, @Param("sort") String sort);

}
