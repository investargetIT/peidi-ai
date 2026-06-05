package com.cyanrocks.ai.service;

/**
 * 意图识别结果
 */
public class IntentResult {

    /** 意图类型 */
    private IntentType intent;

    /** 置信度分数 (0.0 - 1.0) */
    private double score;

    /** 用户原始消息 */
    private String originalMessage;

    /** 意图识别返回的原始 JSON（用于扩展字段） */
    private String rawResponse;

    /** 扩展参数（如领取的商品名称等） */
    private String params;

    public IntentType getIntent() {
        return intent;
    }

    public void setIntent(IntentType intent) {
        this.intent = intent;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    /**
     * 判断是否命中意图（分数 >= 0.5）
     */
    public boolean isHit() {
        return score >= 0.5;
    }
}