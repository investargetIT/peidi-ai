package com.cyanrocks.ai.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class PdOmsConfig {

    @Value("${peidi.oms.url}")
    private String agentUrl;

    @Value("${peidi.oms.timeout-ms}")
    private String timeoutMs;

    @Value("${peidi.oms.token}")
    private String token;

}
