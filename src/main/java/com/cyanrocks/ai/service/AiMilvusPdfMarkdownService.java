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

    public void updateMilvusPdfMarkdown(AiMilvusPdfMarkdown milvusPdfMarkdown, String collection){
            List<AiMilvusPdfMarkdown> sameTitle = baseMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                    .eq(AiMilvusPdfMarkdown::getTitle, milvusPdfMarkdown.getTitle()));
            sameTitle.forEach(title ->{
                if (!milvusPdfMarkdown.getSource().equals(title.getSource())){
                    throw new BusinessException(500,"存在同名文件");
                }
            });

        //由于milvus不能修改，根据要修改的其中一个milvusId获取旧的title
        AiMilvusPdfMarkdown title = baseMapper.selectOne(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                .eq(AiMilvusPdfMarkdown::getMilvusId, milvusPdfMarkdown.getMilvusId()));
        //根据旧的title获取对应的所有milvusId
        List<AiMilvusPdfMarkdown> olds = baseMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                .eq(AiMilvusPdfMarkdown::getTitle, title.getTitle()));
        olds.forEach(old -> {
            milvusPdfMarkdown.setMilvusId(old.getMilvusId());
            if (milvusUtils.updateMilvusPdfMarkdownById(milvusPdfMarkdown, collection)){
                baseMapper.update(milvusPdfMarkdown,Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                        .eq(AiMilvusPdfMarkdown::getMilvusId, old.getMilvusId()));
            }
        });
    }

    public void deleteMilvusPdfMarkdown(AiMilvusPdfMarkdown milvusPdfMarkdown, String collection){
        //由于milvus不能修改，根据要修改的其中一个milvusId获取旧的title
        AiMilvusPdfMarkdown title = baseMapper.selectOne(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                .eq(AiMilvusPdfMarkdown::getMilvusId, milvusPdfMarkdown.getMilvusId()));
        //根据旧的title获取对应的所有milvusId
        List<AiMilvusPdfMarkdown> olds = baseMapper.selectList(Wrappers.<AiMilvusPdfMarkdown>lambdaQuery()
                .eq(AiMilvusPdfMarkdown::getTitle, title.getTitle()));
        olds.forEach(old -> {
            milvusPdfMarkdown.setMilvusId(old.getMilvusId());
            if (milvusUtils.deleteMilvusById(milvusPdfMarkdown.getMilvusId(), collection)){
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
