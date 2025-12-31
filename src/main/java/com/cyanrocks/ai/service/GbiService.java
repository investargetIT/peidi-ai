package com.cyanrocks.ai.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyanrocks.ai.dao.entity.AiGbiExplain;
import com.cyanrocks.ai.dao.entity.AiGbiTable;
import com.cyanrocks.ai.dao.entity.AiMilvusPdfMarkdown;
import com.cyanrocks.ai.dao.mapper.AiGbiExplainMapper;
import com.cyanrocks.ai.dao.mapper.AiGbiTableMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.utils.SearchSqlUtils;
import com.cyanrocks.ai.vo.request.SearchReq;
import com.cyanrocks.ai.vo.request.SortReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/12/18 13:56
 */
@Service
public class GbiService {

    @Autowired
    private AiGbiTableMapper gbiTableMapper;

    @Autowired
    private AiGbiExplainMapper gbiExplainMapper;

    @Autowired
    private SearchSqlUtils searchSqlUtils;

    @Autowired
    private MilvusUtils milvusUtils;

    public IPage<AiGbiTable> getGbiTablePage(int pageNo, int pageSize, String sortStr, String searchStr){
        String searchSb = null;
        if (null != searchStr) {
            List<SearchReq> searchReqs = JSONObject.parseArray(searchStr, SearchReq.class);
            searchSb = searchSqlUtils.buildSearchSql(searchReqs);
        }
        String sortSb = null;
        if (null != sortStr) {
            List<SortReq> sortReqs = JSONObject.parseArray(sortStr, SortReq.class);
            sortSb = searchSqlUtils.buildSortSql(sortReqs);
        }
        return gbiTableMapper.getGbiTablePage(new Page<>(pageNo, pageSize), searchSb, sortSb);
    }

    public void updateGbiTable(AiGbiTable aiGbiTable, String collection){
        if (milvusUtils.updateGbiTableById(aiGbiTable, collection)){
            gbiTableMapper.updateById(aiGbiTable);
        }
    }

    public void deleteGbiTable(AiGbiTable aiGbiTable, String collection){
        if (milvusUtils.deleteMilvusById(aiGbiTable.getMilvusId(), collection)){
            gbiTableMapper.deleteById(aiGbiTable.getId());
        }
    }

    public IPage<AiGbiExplain> getGbiExplainPage(int pageNo, int pageSize, String sortStr, String searchStr){
        String searchSb = null;
        if (null != searchStr) {
            List<SearchReq> searchReqs = JSONObject.parseArray(searchStr, SearchReq.class);
            searchSb = searchSqlUtils.buildSearchSql(searchReqs);
        }
        String sortSb = null;
        if (null != sortStr) {
            List<SortReq> sortReqs = JSONObject.parseArray(sortStr, SortReq.class);
            sortSb = searchSqlUtils.buildSortSql(sortReqs);
        }
        return gbiExplainMapper.getGbiExplainPage(new Page<>(pageNo, pageSize), searchSb, sortSb);
    }

    public void updateGbiExplain(AiGbiExplain aiGbiExplain, String collection){
        if (milvusUtils.updateGbiExpainById(aiGbiExplain, collection)){
            gbiExplainMapper.updateById(aiGbiExplain);
        }
    }

    public void deleteGbiExplain(AiGbiExplain aiGbiExplain, String collection){
        if (milvusUtils.deleteMilvusById(aiGbiExplain.getMilvusId(), collection)){
            gbiExplainMapper.deleteById(aiGbiExplain.getId());
        }
    }

}
