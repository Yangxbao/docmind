package com.docmind.service;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docmind.common.BizException;
import com.docmind.dto.LoginDTO;
import com.docmind.dto.RegisterDTO;
import com.docmind.entity.User;
import com.docmind.mapper.UserMapper;
import com.docmind.vo.LoginVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 认证服务：注册 / 登录 / 退出。
 *
 * 安全要点：
 *   1. 密码用 BCrypt 加盐哈希后入库，数据库泄露也拿不到明文；
 *   2. 登录成功签发 JWT（自带过期时间），后续请求带 Authorization: Bearer <token>；
 *   3. 登录态同时写入 Redis，退出登录后旧 token 立即失效（Redis 不可用则退化为纯 JWT）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final RedisService redisService;

    @Value("${docmind.jwt.secret}")
    private String secret;

    @Value("${docmind.jwt.expire-hours}")
    private long expireHours;

    public AuthService(UserMapper userMapper, RedisService redisService) {
        this.userMapper = userMapper;
        this.redisService = redisService;
    }

    /** 注册：用户名唯一校验 → BCrypt 加密 → 落库 */
    public void register(RegisterDTO dto) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.username()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.username());
        user.setPassword(BCrypt.hashpw(dto.password()));
        user.setNickname((dto.nickname() == null || dto.nickname().isBlank())
                ? dto.username() : dto.nickname());
        userMapper.insert(user);
        log.info("新用户注册: {}", user.getUsername());
    }

    /** 登录：查用户 → BCrypt 比对密码 → 签发 JWT → 登录态写入 Redis */
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.username()));
        // 用户不存在或密码错误，都返回同一句提示，避免被试探出"哪些用户名存在"
        if (user == null || !BCrypt.checkpw(dto.password(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        // 生成 JWT：载荷放 userId 和过期时间，用配置里的密钥签名
        String token = JWT.create()
                .setPayload("userId", user.getId())
                .setExpiresAt(new Date(System.currentTimeMillis() + expireHours * 3600_000L))
                .setKey(secret.getBytes(StandardCharsets.UTF_8))
                .sign();
        redisService.saveLoginToken(user.getId(), token, expireHours);
        log.info("用户登录: {}", user.getUsername());
        return new LoginVO(token, user.getId(), user.getUsername(), user.getNickname());
    }

    /** 退出：解析 token 拿到 userId，删除 Redis 里的登录态（旧 token 立即失效） */
    public void logout(String token) {
        try {
            Number userId = (Number) JWTUtil.parseToken(token).getPayload("userId");
            if (userId != null) {
                redisService.removeLoginToken(userId.longValue());
            }
        } catch (Exception ignore) {
            // token 已经无效了，退出时忽略即可
        }
    }
}
