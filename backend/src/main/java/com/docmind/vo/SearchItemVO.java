package com.docmind.vo;

import java.util.List;

/**
 * 单条检索结果。
 * score   —— 语义检索时的余弦相似度(0~1)，关键词模式下为 null
 * snippet —— 命中片段：语义模式取相似度最高的分块，关键词模式取正文里首次命中的位置附近
 */
public record SearchItemVO(
        Long docId,
        String fileName,
        String fileType,
        String summary,
        List<String> tags,
        Double score,
        String snippet,
        String createTime
) {
}
