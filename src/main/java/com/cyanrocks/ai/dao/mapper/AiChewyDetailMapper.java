package com.cyanrocks.ai.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyanrocks.ai.dao.entity.AiChewyDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface AiChewyDetailMapper extends BaseMapper<AiChewyDetail> {

    /**
     * Retrieve all AiChewyDetail rows with id greater than 9753, ordered by id ascending.
     *
     * @return a list of AiChewyDetail entities where id > 9753, ordered by id ascending
     */
    @Select("select * from ai_chewy_detail where id > 9753 order by id asc")
    List<AiChewyDetail> selectAll();

}
