package com.cyanrocks.ai.controller;

import com.cyanrocks.ai.dao.entity.BiGoodsEvaluation;
import com.cyanrocks.ai.dao.mapper.BiGoodsEvaluationMapper;
import com.cyanrocks.ai.service.AiEvaluationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author wjq
 * @Date 2026/1/20 10:12
 */
@RestController
@RequestMapping("/ai/evaluation")
@Api(tags = {"ai回复接口"})
public class EvaluationController {

    @Autowired
    private AiEvaluationService aiEvaluationService;

    @PostMapping("/new")
    @ApiOperation(value = "生成ai回复")
    public void newEvaluation(@RequestBody BiGoodsEvaluation evaluation) {
        aiEvaluationService.newEvaluation(evaluation);
    }
}
