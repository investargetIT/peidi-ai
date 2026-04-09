package com.cyanrocks.ai.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiDrawRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiDrawRecordMapper extends BaseMapper<AiDrawRecord> {

    /**
 * Retrieve a paginated list of AiDrawRecord matching the provided search and sort criteria.
 *
 * @param page   the pagination request (page number, size and related paging settings)
 * @param search optional search string used to filter records; may be null or empty to disable filtering
 * @param sort   optional sort expression or key used to order results; may be null or empty for default ordering
 * @return       a page of AiDrawRecord containing the query results for the requested page
 */
IPage<AiDrawRecord> getRecordPage(IPage<?> page, @Param("search")String search, @Param("sort") String sort);

}
