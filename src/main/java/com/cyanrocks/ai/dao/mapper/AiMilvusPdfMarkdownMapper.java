package com.cyanrocks.ai.dao.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiMilvusPdfMarkdown;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface AiMilvusPdfMarkdownMapper extends BaseMapper<AiMilvusPdfMarkdown> {
    IPage<AiMilvusPdfMarkdown> getMilvusPdfMarkdownPage(IPage<?> page, @Param("search")String search, @Param("sort") String sort);

    Integer getUsageCnt(@Param("reportType")String reportType);

    Integer getValidCnt(@Param("reportType")String reportType);
}
