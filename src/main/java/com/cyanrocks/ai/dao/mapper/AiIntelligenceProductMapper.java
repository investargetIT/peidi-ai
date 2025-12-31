package com.cyanrocks.ai.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiIntelligenceProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiIntelligenceProductMapper extends BaseMapper<AiIntelligenceProduct> {
    IPage<AiIntelligenceProduct> getIntelligenceProductPage(IPage<?> page, @Param("search")String search, @Param("sort") String sort);

}
