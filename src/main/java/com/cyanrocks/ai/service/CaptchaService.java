package com.cyanrocks.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 验证码服务
 */
@Service
public class CaptchaService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 验证码 key 前缀
     */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    /**
     * 验证码过期时间（分钟）
     */
    private static final long CAPTCHA_EXPIRE_MINUTES = 3;

    /**
     * 保存验证码到 Redis
     * @param key 验证码标识（如手机号、邮箱等）
     * @param value 验证码值
     */
    public void saveCaptcha(String key, String value) {
        String redisKey = CAPTCHA_KEY_PREFIX + key;
        stringRedisTemplate.opsForValue().set(redisKey, value, CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 获取验证码
     * @param key 验证码标识
     * @return 验证码值，不存在则返回 null
     */
    public String getCaptcha(String key) {
        String redisKey = CAPTCHA_KEY_PREFIX + key;
        return stringRedisTemplate.opsForValue().get(redisKey);
    }

    /**
     * 验证码是否存在
     * @param key 验证码标识
     * @return true 存在，false 不存在
     */
    public boolean hasCaptcha(String key) {
        String redisKey = CAPTCHA_KEY_PREFIX + key;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey));
    }

    /**
     * 删除验证码
     * @param key 验证码标识
     */
    public void deleteCaptcha(String key) {
        String redisKey = CAPTCHA_KEY_PREFIX + key;
        stringRedisTemplate.delete(redisKey);
    }

    /**
     * 验证码校验（校验后自动删除）
     * @param key 验证码标识
     * @param value 待校验的验证码值
     * @return true 校验通过，false 校验失败
     */
    public boolean verifyCaptcha(String key, String value) {
        String storedValue = getCaptcha(key);
        if (storedValue != null && storedValue.equals(value)) {
            deleteCaptcha(key);
            return true;
        }
        return false;
    }
}
