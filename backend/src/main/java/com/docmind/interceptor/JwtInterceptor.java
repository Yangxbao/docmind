package com.docmind.interceptor;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.docmind.common.UserContext;
import com.docmind.service.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 登录拦截器（本项目不引入重量级的 Spring Security，用拦截器实现同等能力，逻辑一目了然）。
 *
 * 工作流程：
 *   1. 从请求头 Authorization 里取出 "Bearer xxx" 格式的 token
 *   2. 用密钥验签 + 校验过期时间
 *   3. （如果 Redis 可用）校验 Redis 中的登录态是否一致，实现"注销后 token 立即失效"
 *   4. 校验通过 → 把 userId 放进 UserContext(ThreadLocal)，业务层随时可取
 *
 * 请求结束后 afterCompletion 会清理 ThreadLocal，防止线程复用导致用户串号。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);

    private final RedisService redisService;
    private final String secret;

    public JwtInterceptor(RedisService redisService,
                          @Value("${docmind.jwt.secret}") String secret) {
        this.redisService = redisService;
        this.secret = secret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行跨域预检请求
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : "";

        Long userId = verify(token);
        if (userId == null) {
            return reject(response);
        }
        // Redis 登录态校验：token 被注销或被新登录替换时，旧 token 立即失效。
        // Redis 不可用时会自动放行（降级为纯 JWT 模式），系统照常工作。
        if (!redisService.checkLoginToken(userId, token)) {
            return reject(response);
        }
        UserContext.set(userId);
        return true;
    }

    /**
     * 验签 + 过期校验。
     * @return 校验通过返回 userId；任何一步失败返回 null
     */
    private Long verify(String token) {
        if (token.isBlank()) {
            return null;
        }
        try {
            JWT jwt = JWTUtil.parseToken(token);
            // 1) 用密钥验证签名：token 内容被篡改会在这里失败
            if (!jwt.setKey(secret.getBytes(StandardCharsets.UTF_8)).verify()) {
                return null;
            }
            // 2) 校验自定义的过期时间（exp 是秒级时间戳）
            Object exp = jwt.getPayload("exp");
            Object uid = jwt.getPayload("userId");
            if (exp == null || uid == null) {
                return null;
            }
            if (System.currentTimeMillis() > Long.parseLong(exp.toString()) * 1000) {
                return null;
            }
            return Long.parseLong(uid.toString());
        } catch (Exception e) {
            // token 格式非法等，一律视为未登录
            log.debug("token 校验失败: {}", e.getMessage());
            return null;
        }
    }

    /** 校验失败：返回 401 和统一格式的 JSON */
    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"登录已过期，请重新登录\",\"data\":null}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束必须清理，否则线程池里的线程会把上一个用户的 userId 带给下一个请求
        UserContext.clear();
    }
}
