package com.cyanrocks.ai.controller;

import com.cyanrocks.ai.dao.entity.AgentChatReq;
import com.cyanrocks.ai.dao.entity.AgentChatResp;
import com.cyanrocks.ai.service.AgentQuestionReqService;
import com.cyanrocks.ai.vo.request.AgentChatReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/agent")
@Api(tags = {"agent接口"})
public class AgentQuestionController {
    @Autowired
    private AgentQuestionReqService agentQuestionReqService;
    @ApiOperation(value = "agent对话接口")
    @PostMapping("/chat")
    private ResponseEntity<AgentChatResp> message(@RequestBody AgentChatReqVO req) {
        if (req == null || StringUtils.isBlank(req.getMessage())) {
            return ResponseEntity.ok(AgentChatResp.builder().agentResult("有什么可以帮到您？").build());
        }
        if (StringUtils.isBlank(req.getChannel())) {
            return ResponseEntity.ok(AgentChatResp.builder().agentResult("缺少渠道信息").build());
        }
        if (StringUtils.isBlank(req.getOpenid())) {
            return ResponseEntity.ok(AgentChatResp.builder().agentResult("缺少用户标识").build());
        }
        AgentChatResp question = agentQuestionReqService.getQuestion(
                AgentChatReq.builder()
                        .channel(req.getChannel())
                        .message(req.getMessage())
                        .user_id(req.getOpenid())
                        .build());
        return ResponseEntity.ok(question);
    }


}
