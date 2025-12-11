package com.cyanrocks.ai.dao.mapper;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyanrocks.ai.dao.entity.BiReddit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BiRedditMapper extends BaseMapper<BiReddit> {
    @Select("select * from bi_reddit")
    List<BiReddit> selectAll();
}
