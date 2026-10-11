package com.docmind.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis 安全封装层。
 *
 * 为什么要包一层？本项目的定位是"开箱即用的本地工具"，
 * Redis 只承担两个锦上添花的功能（登录态管理、检索缓存），
 * 不应该因为它没启动就让整个系统跑不起来。
 * 所以所有操作都包在 safely() 里：Redis 挂了只打一条警告并降级，业务照常运行。
 *
 * 用途：
 *   1. 登录态：login:token:{userId} = token（TTL 72h），支持注销/过期管理
 *   2. 检索缓存：search:{userId}:{mode}:{md5(query)}（TTL 5 分钟）
 */
@Service
public class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);

    private final StringRedisTemplate redis;

    /** 记录 Redis 可用性，状态变化时只打一条日志，避免刷屏 */
    private volatile boolean available = true;

    public RedisService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 统一的安全执行模板：捕获所有异常并降级返回兜底值 */
    private <T> T safely(Supplier<T> action, T fallback) {
        try {
            T result = action.get();
            if (!available) {
                available = true;
                log.info("Redis 恢复可用");
            }
            return result;
        } catch (Exception e) {
            if (available) {
                available = false;
                log.warn("Redis 不可用，已自动降级为无 Redis 模式（登录校验退化为纯 JWT，检索无缓存）: {}", e.getMessage());
            }
            return fallback;
        }
    }

    // ==================== 登录态管理 ====================

    public void saveLoginToken(Long userId, String token, long expireHours) {
        safely(() -> {
            redis.opsForValue().set("login:token:" + userId, token, Duration.ofHours(expireHours));
            return null;
        }, null);
    }

    /**
     * 校验 Redis 中的登录态。
     * 返回 false = 该 token 已被注销或被新登录替换，应立即失效；
     * 返回 true  = Redis 无记录（未用 Redis / 已过期降级）或与 token 一致。
     */
    public boolean checkLoginToken(Long userId, String token) {
        String saved = safely(() -> redis.opsForValue().get("login:token:" + userId), null);
        return saved == null || saved.equals(token);
    }

    public void removeLoginToken(Long userId) {
        safely(() -> {
            redis.delete("login:token:" + userId);
            return null;
        }, null);
    }

    // ==================== 通用缓存 ====================

    public String get(String key) {
        return safely(() -> redis.opsForValue().get(key), null);
    }

    public void set(String key, String value, long ttlSeconds) {
        safely(() -> {
            redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            return null;
        }, null);
    }
}
