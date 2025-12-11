package com.cyanrocks.ai.xiyan.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author xxx
 * @desc
 * @date 2024/3/6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventData {

    private String event;
    private String sessionId;
    private String requestId;
    private String rewrite;
    private List<String> selector;
    private Map<String, List<String>> smrResult;
    private String sql;
    private String evidence;
    private List<SqlAttempt> attempts;
    private ResultSetBO sqlData;
    private String sqlError;
    private VisualizationBO visualization;
    private String chatContent;
    private String debug;
    private String errorMessage;

}
