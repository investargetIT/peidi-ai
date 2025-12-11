package com.cyanrocks.ai.utils;

import com.cyanrocks.ai.dao.entity.AiQueryHistory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Author wjq
 * @Date 2025/9/24 15:18
 * 批量移除token超长
 */
@Component
public class SmartHistoryTruncator {
    private static final int MAX_TOKENS = 120000;
    private static final int BATCH_REMOVE_COUNT = 5; // 批量移除数量，提高性能
    /**
     * 智能截断历史记录
     */
    public List<AiQueryHistory> smartTruncate(List<AiQueryHistory> historyList) {
        if (historyList == null || historyList.isEmpty()) {
            return new ArrayList<>();
        }

        // 创建可修改的副本
        List<AiQueryHistory> workingList = new CopyOnWriteArrayList<>(historyList);
        int totalTokens = calculateTotalTokens(workingList);

        System.out.println("初始token数: " + totalTokens);

        // 如果已经满足要求，直接返回
        if (totalTokens <= MAX_TOKENS) {
            return new ArrayList<>(workingList);
        }

        // 按创建时间排序
        workingList.sort(Comparator.comparing(AiQueryHistory::getCreateAt));

        int removedCount = 0;
        int currentTokens = totalTokens;

        // 批量移除最早的项目
        while (currentTokens > MAX_TOKENS && workingList.size() > 1) {
            int batchTokensRemoved = removeEarliestBatch(workingList, BATCH_REMOVE_COUNT);
            currentTokens -= batchTokensRemoved;
            removedCount += BATCH_REMOVE_COUNT;

            System.out.printf("移除%d条记录，当前token: %d, 剩余记录: %d%n",
                    BATCH_REMOVE_COUNT, currentTokens, workingList.size());

            // 如果批量移除后仍然超限，但列表已经很小，改为逐条移除
            if (workingList.size() <= BATCH_REMOVE_COUNT * 2) {
                break;
            }
        }

        // 逐条精确移除（处理最后几条）
        while (currentTokens > MAX_TOKENS && !workingList.isEmpty()) {
            AiQueryHistory oldest = workingList.get(0);
            int tokens = calculateTokens(oldest);
            workingList.remove(0);
            currentTokens -= tokens;
            removedCount++;
        }

        System.out.printf("总共移除%d条记录，最终token: %d%n", removedCount, currentTokens);
        return new ArrayList<>(workingList);
    }

    /**
     * 批量移除最早的一批记录
     */
    private static int removeEarliestBatch(List<AiQueryHistory> list, int batchSize) {
        int tokensRemoved = 0;
        int removeCount = Math.min(batchSize, list.size());

        for (int i = 0; i < removeCount; i++) {
            if (i < list.size()) {
                tokensRemoved += calculateTokens(list.get(i));
            }
        }

        // 批量移除
        list.subList(0, removeCount).clear();
        return tokensRemoved;
    }

    private static int calculateTotalTokens(List<AiQueryHistory> historyList) {
        return historyList.parallelStream() // 使用并行流提高性能
                .mapToInt(SmartHistoryTruncator::calculateTokens)
                .sum();
    }

    private static int calculateTokens(AiQueryHistory history) {
        // 实现你的token计算逻辑
        int tokens = 0;

        // 示例：计算query和response的token
        if (history.getQuery() != null) {
            tokens += estimateTokens(history.getQuery());
        }
        if (history.getResult() != null) {
            tokens += estimateTokens(history.getResult());
        }

        // 添加固定开销
        tokens += 30;

        return tokens;
    }

    private static int estimateTokens(String text) {
        // 使用更精确的估算方法
        if (text == null) return 0;

        double tokenCount = 0;
        for (char c : text.toCharArray()) {
            if (isChineseCharacter(c)) {
                tokenCount += 0.67; // 中文约1.5字符=1token
            } else {
                tokenCount += 0.25; // 英文约4字符=1token
            }
        }

        return (int) Math.ceil(tokenCount);
    }

    private static boolean isChineseCharacter(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }
}
