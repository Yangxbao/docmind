package com.docmind.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 文档信息返回（列表页/详情页通用） */
public record DocVO(
        Long id,
        String fileName,
        String fileType,
        Long fileSize,
        String summary,
        Integer status,
        String failReason,
        LocalDateTime createTime,
        List<String> tags
) {
}
