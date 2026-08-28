package com.cyanrocks.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信配置类
 * @author yangshihao
 */
@Data
@Component
@ConfigurationProperties(prefix = "qywx")
public class QywxConfig {
    /**
     * 企业ID
     */
    private String corpId;
    
    /**
     * 应用Secret
     */
    private String corpSecret;

    /**
     * 群发任务配置
     */
    private GroupSend groupSend = new GroupSend();

    @Data
    public static class GroupSend {
        /**
         * 是否启用群发定时任务
         */
        private boolean enabled = false;

        /**
         * 发送者企微成员ID
         */
        private String sender = "peidi3";

        /**
         * 群发图片本地路径（每次群发前重新上传临时素材）
         */
        private String imagePath;

        /**
         * 群发文案内容
         */
        private String content;

        /**
         * 客户加入天数（给N天前当天加入的客户发送）
         */
        private int daysAgo = 7;
    }
}
