package com.docmind.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 检索入参。
 * mode:
 *   semantic —— 语义检索（默认）：query 向量化后与文档块向量算余弦相似度
 *   keyword  —— 关键词检索：LIKE 匹配文件名/正文，作为兜底模式
 */
public record SearchDTO(

        @NotBlank(message = "搜索内容不能为空")
        String query,

        String mode
) {
}
