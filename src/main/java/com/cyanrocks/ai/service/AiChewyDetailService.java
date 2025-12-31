package com.cyanrocks.ai.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyanrocks.ai.dao.entity.AiChewyDetail;
import com.cyanrocks.ai.dao.mapper.AiChewyDetailMapper;
import com.cyanrocks.ai.exception.BusinessException;
import com.cyanrocks.ai.utils.AiModelUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author wjq
 * @Date 2025/12/23 17:08
 */
@Service
public class AiChewyDetailService extends ServiceImpl<AiChewyDetailMapper, AiChewyDetail> {

    @Autowired
    private AiModelUtils aiModelUtils;

    public void parseChewy(String url, String title, MultipartFile detailFile, MultipartFile ingredientInformationFile) {
        String detail = null;
        if (null != detailFile && !detailFile.isEmpty()) {
            try {
                detail = aiModelUtils.processPageWithQwen(detailFile);
            } catch (Exception e) {
                throw new BusinessException(500, "处理详情文件失败: " + e.getMessage());
            }
        }

        String ingredientInformation = null;
        if (null != ingredientInformationFile && !ingredientInformationFile.isEmpty()) {
            try {
                ingredientInformation = aiModelUtils.processPageWithQwen(ingredientInformationFile);
            } catch (Exception e) {
                throw new BusinessException(500, "处理成分信息文件失败: " + e.getMessage());
            }
        }
        AiChewyDetail record = new AiChewyDetail();
        record.setUrl(url);
        record.setTitle(title);
        record.setDetail(detail);
        record.setIngredientInformation(ingredientInformation);
        baseMapper.insert(record);
    }
}
