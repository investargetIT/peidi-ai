package com.cyanrocks.ai.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyanrocks.ai.dao.entity.AiChewyDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface AiChewyDetailMapper extends BaseMapper<AiChewyDetail> {

    @Select("select * from ai_chewy_detail where id > 9753 order by id asc")
    List<AiChewyDetail> selectAll();

}
