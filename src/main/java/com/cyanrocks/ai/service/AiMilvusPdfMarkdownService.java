package com.cyanrocks.ai.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyanrocks.ai.dao.entity.AiEnum;
import com.cyanrocks.ai.dao.entity.AiMilvusPdfMarkdown;
import com.cyanrocks.ai.dao.mapper.AiEnumMapper;
import com.cyanrocks.ai.dao.mapper.AiMilvusPdfMarkdownMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.utils.MilvusUtils;
import com.cyanrocks.ai.utils.SearchSqlUtils;
import com.cyanrocks.ai.vo.request.SearchReq;
import com.cyanrocks.ai.vo.request.SortReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author wjq
 * @Date 2025/10/31 13:45
 */
@Service
public class AiMilvusPdfMarkdownService extends ServiceImpl<AiMilvusPdfMarkdownMapper, AiMilvusPdfMarkdown> {

    @Autowired
    private SearchSqlUtils searchSqlUtils;

    @Autowired
    private MilvusUtils milvusUtils;

    @Autowired
    private AiEnumMapper aiEnumMapper;

    public IPage<AiMilvusPdfMarkdown> getMilvusPdfMarkdownPage(int pageNo, int pageSize, String sortStr, String searchStr){
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
        return baseMapper.getMilvusPdfMarkdownPage(new Page<>(pageNo, pageSize), searchSb, sortSb);
    }

    /**
     * Updates Milvus documents and corresponding database records for all PDF entries that share the provided title.
     *
     * Validates that any existing records with the same title have the same source; attempts to update each found Milvus document (by its stored Milvus ID) with values from `milvusPdfMarkdown`; if at least one Milvus update succeeds, applies the provided fields to database rows matching the title.
     *
     * @param milvusPdfMarkdown the DTO containing updated fields to apply to Milvus documents and database records; its `title` is used to locate existing rows and its `milvusId` is set per existing record before each Milvus update attempt
     * @param collection the Milvus collection name in which the documents are stored
     * @throws BusinessException if an existing record with the same title has a different `source` than `milvusPdfMarkdown`
     */
    public void updateMilvusPdfMarkdown(AiMilvusPdfMarkdown milvusPdfMarkdown, String collection){
            List<AiMilvusPdfMarkdown> sameTitle = baseMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                    .eq(AiMilvusPdfMarkdown::getTitle, milvusPdfMarkdown.getTitle()));
            sameTitle.forEach(title ->{
                if (!milvusPdfMarkdown.getSource().equals(title.getSource())){
                    throw new BusinessException(500,"存在同名文件");
                }
            });
        //根据旧的title获取对应的所有milvusId
        List<AiMilvusPdfMarkdown> olds = baseMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                .eq(AiMilvusPdfMarkdown::getTitle, milvusPdfMarkdown.getTitle()));
        boolean hasUpdate = false;
        for (AiMilvusPdfMarkdown old:olds){
            milvusPdfMarkdown.setMilvusId(old.getMilvusId());
            if (null!= milvusPdfMarkdown.getMilvusId() && milvusUtils.updateMilvusPdfMarkdownById(milvusPdfMarkdown, collection)){
                hasUpdate = true;
            }
        }
        if (hasUpdate){
            baseMapper.update(milvusPdfMarkdown,Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                    .eq(AiMilvusPdfMarkdown::getTitle, milvusPdfMarkdown.getTitle()));
        }
    }

    /**
     * Deletes database records that share the given title and removes their corresponding Milvus documents when applicable.
     *
     * For each database record with the same title as the provided `milvusPdfMarkdown`:
     * - If the record's `milvusId` is null, the database row is deleted.
     * - Otherwise, attempts to delete the document in Milvus by that `milvusId`; if the Milvus delete succeeds, the database row is deleted.
     *
     * @param milvusPdfMarkdown object whose title is used to find matching records; its `milvusId` field will be set per-record during processing
     * @param collection the Milvus collection name in which to delete documents
     */
    public void deleteMilvusPdfMarkdown(AiMilvusPdfMarkdown milvusPdfMarkdown, String collection){
        //根据旧的title获取对应的所有milvusId
        List<AiMilvusPdfMarkdown> olds = baseMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                .eq(AiMilvusPdfMarkdown::getTitle, milvusPdfMarkdown.getTitle()));
        olds.forEach(old -> {
            milvusPdfMarkdown.setMilvusId(old.getMilvusId());
            if (null == milvusPdfMarkdown.getMilvusId()){
                baseMapper.deleteById(old.getId());
            }else if (milvusUtils.deleteMilvusById(milvusPdfMarkdown.getMilvusId(), collection)){
                baseMapper.deleteById(old.getId());
            }
        });
    }

    public JSONObject getDashboard(){
        JSONObject result = new JSONObject();

        JSONArray usageList = new JSONArray();
        JSONArray validList = new JSONArray();

        List<String> fields = aiEnumMapper.selectList(Wrappers.<AiEnum>lambdaQuery()
                .eq(AiEnum::getType, "reportType")).stream().map(AiEnum::getValue).collect(Collectors.toList());
        fields.forEach(field -> {
            Integer usageCnt = baseMapper.getUsageCnt(field);
            JSONObject usage = new JSONObject();
            usage.put("field", field);
            usage.put("cnt", usageCnt == null ? 0 : usageCnt);
            usageList.add(usage);

            Integer validCnt = baseMapper.getValidCnt(field);
            JSONObject valid = new JSONObject();
            valid.put("field", field);
            valid.put("cnt", validCnt == null ? 0 : validCnt);
            validList.add(valid);
        });
        result.put("usageList", usageList);
        result.put("validList", validList);
        return result;
    }

}
