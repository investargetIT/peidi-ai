package com.cyanrocks.ai.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class PdAgentConfig {

    @Value("${peidi.agent.url}")
    private String agentUrl;

    @Value("${peidi.agent.timeout-ms}")
    private String timeoutMs;


}
