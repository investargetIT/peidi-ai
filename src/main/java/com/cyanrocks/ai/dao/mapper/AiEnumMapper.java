package com.cyanrocks.ai.dao.mapper;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyanrocks.ai.dao.entity.AiEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiEnumMapper extends BaseMapper<AiEnum> {
    @Select("${sql}")
    List<JSONObject> doSql(@Param("sql") String sql);
}
