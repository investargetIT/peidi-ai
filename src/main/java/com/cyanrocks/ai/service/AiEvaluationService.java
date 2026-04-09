package com.cyanrocks.ai.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyanrocks.ai.dao.entity.BiGoodsEvaluation;
import com.cyanrocks.ai.dao.mapper.BiGoodsEvaluationMapper;
import com.cyanrocks.ai.utils.AiModelUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author wjq
 * @Date 2025/12/23 17:08
 */
@Service
public class AiEvaluationService extends ServiceImpl<BiGoodsEvaluationMapper, BiGoodsEvaluation> {

    @Autowired
    private AiModelUtils aiModelUtils;

    /**
     * Generates a fresh AI evaluation for the persisted goods evaluation identified by the given object's id and updates it in the database.
     *
     * The method loads the existing BiGoodsEvaluation by id, replaces its `aiEvaluationContent` with the result from AiModelUtils, and persists the change.
     *
     * @param evaluation an object whose `id` is used to locate the persisted BiGoodsEvaluation to update
     */
    public void newEvaluation(BiGoodsEvaluation evaluation) {
        BiGoodsEvaluation biGoodsEvaluation = baseMapper.selectById(evaluation.getId());
        biGoodsEvaluation.setAiEvaluationContent(null);
        biGoodsEvaluation.setAiEvaluationContent(aiModelUtils.getEvaluation(biGoodsEvaluation));
        baseMapper.updateById(biGoodsEvaluation);
    }

}
