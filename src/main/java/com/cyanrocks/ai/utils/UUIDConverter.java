package com.cyanrocks.ai.utils;

import java.util.UUID;

/**
 * @Author wjq
 * @Date 2025/10/31 11:45
 * Snowflake 分布式ID生成器
 */
public class UUIDConverter {
    /**
     * 将 UUID 转换为 Long 类型（取前64位）
     */
    public static long uuidToLong(UUID uuid) {
        long mostSigBits = uuid.getMostSignificantBits();
        long leastSigBits = uuid.getLeastSignificantBits();

        // 使用异或操作混合高低位，减少冲突概率
        return mostSigBits ^ leastSigBits;
    }

    /**
     * 生成 UUID 并转换为 Long
     */
    public static long generateUUIDAsLong() {
        return uuidToLong(UUID.randomUUID());
    }

    /**
     * 生成安全的 UUID Long（处理负数）
     */
    public static long generateSafeUUIDAsLong() {
        long rawId = generateUUIDAsLong();
        // 确保是正数
        return rawId & Long.MAX_VALUE;
    }
}