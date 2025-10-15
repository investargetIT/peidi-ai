package com.cyanrocks.ui.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cyanrocks.ui.dao.entity.AiEnum;
import com.cyanrocks.ui.dao.mapper.AiEnumMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author wjq
 * @Date 2024/9/19 16:49
 */
@Service
public class CommonSettingService {

    @Autowired
    private AiEnumMapper uiEnumMapper;

    public List<AiEnum> getEnumList(String type){
        return uiEnumMapper.selectList(Wrappers.<AiEnum>lambdaQuery().eq(AiEnum::getType,type));
    }

    public void setEnumList(List<AiEnum> reqs){
        reqs.forEach(req->{
            if (null == req.getId()){
                uiEnumMapper.insert(req);
            }else {
                uiEnumMapper.updateById(req);
            }
        });
    }

    public void deleteEnum(Long id){
        uiEnumMapper.deleteById(id);
    }
}
