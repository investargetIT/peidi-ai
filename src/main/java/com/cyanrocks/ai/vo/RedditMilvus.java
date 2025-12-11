package com.cyanrocks.ai.vo;

import lombok.Data;
import org.apache.poi.hpsf.Decimal;

import java.util.List;

/**
 * @Author wjq
 * @Date 2025/3/25 9:50
 */
@Data
public class RedditMilvus {
    private String id;
    private String subreddit;
    private String title;
    private String selfText;
    private Integer downs;
    private Integer ups;
    private Double score;
    private Integer reviewCnt;
    private String sentiment;
    private List<Review> reviews;

    @Data
    public static class Review {
        private String body;
        private Integer downs;
        private Integer ups;
    }
}
