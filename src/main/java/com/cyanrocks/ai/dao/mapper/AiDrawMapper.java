package com.cyanrocks.ai.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cyanrocks.ai.dao.entity.AiDraw;
import com.cyanrocks.ai.dao.entity.AiDrawMaterials;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface AiDrawMapper extends BaseMapper<AiDraw> {

    /**
 * Retrieves a paginated list of AiDraw records matching the given search and sort criteria.
 *
 * @param page   pagination settings (page number, size, etc.)
 * @param search search filter applied to AiDraw fields; bound to MyBatis parameter "search"
 * @param sort   sort expression or key; bound to MyBatis parameter "sort"
 * @return       an IPage of AiDraw containing the requested page of results
 */
IPage<AiDraw> getPage(IPage<?> page, @Param("search")String search, @Param("sort") String sort);

    /**
 * Query a paginated list of AiDrawMaterials that match the provided search and sorting criteria.
 *
 * @param page   pagination parameters (page number, size and related settings)
 * @param search text or expression used to filter materials
 * @param sort   sorting directive (e.g., column and direction)
 * @return       a page of matching AiDrawMaterials
 */
IPage<AiDrawMaterials> getMaterialsPage(IPage<?> page, @Param("search")String search, @Param("sort") String sort);

}
