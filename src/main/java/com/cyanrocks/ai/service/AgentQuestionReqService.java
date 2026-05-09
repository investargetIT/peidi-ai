package com.cyanrocks.ai.service;

import com.alibaba.dashscope.utils.JsonUtils;
import com.cyanrocks.ai.config.PdAgentConfig;
import com.cyanrocks.ai.dao.entity.AgenResponseServerError;
import com.cyanrocks.ai.dao.entity.AgentChatReq;
import com.cyanrocks.ai.dao.entity.AgentChatResp;
import com.cyanrocks.ai.dao.entity.AiAgentChainInfo;
import com.cyanrocks.ai.dao.mapper.AiAgentChainInfoMapper;
import com.cyanrocks.ai.utils.Hex12Utils;
import com.cyanrocks.ai.utils.http.PdAgentHttpBuilder;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

@Service
public class AgentQuestionReqService {

    private static final Logger logger = LoggerFactory.getLogger(AgentQuestionReqService.class);

    @Autowired
    private PdAgentConfig pdAgentConfig;
    @Autowired
    private AiAgentChainInfoMapper chainInfoMapper;

    public AgentChatResp getQuestion(AgentChatReq chatReq) {
        logger.info("服务开始调用....");
        if (chatReq.getTimeoutMs() != null) {
            pdAgentConfig.setTimeoutMs(chatReq.getTimeoutMs());
        }
        AiAgentChainInfo chainInfo = new AiAgentChainInfo();
        String reqBody = JsonUtils.toJson(chatReq);
        String reqId = Hex12Utils.random();
        chainInfo.setReqBody(reqBody);
        chainInfo.setXRequestId(reqId);
        chainInfo.setCreateTime(LocalDateTime.now());
        chainInfo.setUserId(chatReq.getUser_id());


        try (Response response = PdAgentHttpBuilder.create(pdAgentConfig)
                .path("/agent/chat").body(reqBody)
                .header("X-Request-ID", reqId)
                .postResponse()) {
            int respCode = response.code();
            chainInfo.setState(String.valueOf(respCode));
            String respBodyStr = getResponseBody(response);
            chainInfo.setRespBody(respBodyStr);
            chainSave(chainInfo);
            return handleResponse(respCode, respBodyStr);
        } catch (IOException e) {
            chainInfo.setErrorMsg(e.getMessage());
            chainSave(chainInfo);
            logger.error("请求agent服务失败, reqId={}", reqId, e);
            return AgentChatResp.builder().agentResult("服务调用异常: " + e.getMessage()).build();
        }
    }

    private void chainSave(AiAgentChainInfo chainInfo) {
        chainInfoMapper.insert(chainInfo);
    }

    private String getResponseBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            return response.body().string();
        } catch (IOException e) {
            logger.warn("读取响应体失败", e);
            return "";
        }
    }

    private AgentChatResp handleResponse(int respCode, String respBodyStr) throws IOException {
        if (respCode == 200) {
            return JsonUtils.fromJson(respBodyStr, AgentChatResp.class);
        }

        if (respCode == 422) {
            return AgentChatResp.builder().stateCode(respCode).agentResult("缺少channel/user_Id信息。").build();
        }

        if (respCode == 500) {
            AgenResponseServerError error = JsonUtils.fromJson(respBodyStr, AgenResponseServerError.class);
            String detail = error != null ? error.getDetail() : "未知错误";
            String requestId = error != null ? error.getRequestId() : "unknown";
            return AgentChatResp.builder()
                    .agentResult("服务调用失败:" + detail + "--request_id:" + requestId)
                    .stateCode(respCode)
                    .build();
        }

        throw new IOException("请求失败，状态码：" + respCode);
    }


}
