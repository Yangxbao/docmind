package com.docmind.vo;

/** 登录成功返回：token 前端存 localStorage，之后每次请求放在 Authorization 头里 */
public record LoginVO(
        String token,
        Long userId,
        String username,
        String nickname
) {
}
