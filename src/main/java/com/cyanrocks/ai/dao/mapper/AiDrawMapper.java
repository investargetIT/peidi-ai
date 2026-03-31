package com.cyanrocks.ai.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiDraw;
import com.cyanrocks.ai.dao.entity.AiDrawMaterials;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface AiDrawMapper extends BaseMapper<AiDraw> {

    IPage<AiDraw> getPage(IPage<?> page, @Param("search")String search, @Param("sort") String sort);

    IPage<AiDrawMaterials> getMaterialsPage(IPage<?> page, @Param("search")String search, @Param("sort") String sort);

}
