package com.docmind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 注册入参 */
public record RegisterDTO(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 20, message = "用户名长度需在 4~20 位之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
        String password,

        /** 昵称可不填，不填时默认用用户名 */
        String nickname
) {
}
