package com.cyanrocks.ai.utils;

import java.security.SecureRandom;

/**
 * 12 位 Hex 字符串生成工具类
 */
public class Hex12Utils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private Hex12Utils() {}

    /**
     * 生成 12 位随机 hex 字符串
     */
    public static String random() {
        byte[] bytes = new byte[6];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * 从 long 值生成 12 位 hex 字符串（取低 48 位）
     */
    public static String fromLong(long value) {
        long masked = value & 0xFFFFFFFFFFFFL;
        return String.format("%012x", masked);
    }

    /**
     * 将 12 位 hex 字符串转换为 long
     */
    public static long toLong(String hex) {
        if (hex == null || hex.length() != 12) {
            throw new IllegalArgumentException("必须是 12 位 hex 字符串");
        }
        return Long.parseLong(hex, 16);
    }

    /**
     * 校验是否为有效的 12 位 hex 字符串
     */
    public static boolean isValid(String hex) {
        if (hex == null || hex.length() != 12) {
            return false;
        }
        for (char c : hex.toCharArray()) {
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                return false;
            }
        }
        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
}