package com.docmind.vo;

/** 搜索历史返回（已按用户去重，最多 8 条） */
public record HistoryVO(
        Long id,
        String keyword,
        String createTime
) {
}
