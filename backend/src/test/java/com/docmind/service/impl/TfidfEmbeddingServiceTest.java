package com.docmind.service.impl;

import com.docmind.util.VectorUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置词频向量单元测试。
 * 重点验证检索系统的根基：意思/用词越接近的文本，向量余弦相似度越高。
 */
class TfidfEmbeddingServiceTest {

    private final TfidfEmbeddingService embeddingService = new TfidfEmbeddingService();

    @Test
    @DisplayName("向量维度固定为512")
    void fixedDimension() {
        assertEquals(512, TfidfEmbeddingService.DIM);
        assertEquals(512, embeddingService.embed("向量检索是一件有趣的事情").size());
        assertEquals(512, embeddingService.embed("").size());
        assertEquals(512, embeddingService.embed(null).size());
    }

    @Test
    @DisplayName("向量应归一化（模长为1），空文本为零向量")
    void normalized() {
        List<Float> vector = embeddingService.embed("SpringBoot 是一款流行的 Java 框架，MyBatis-Plus 负责数据库访问");
        double norm = 0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        assertEquals(1.0, Math.sqrt(norm), 1e-3, "归一化后模长应为1");

        List<Float> empty = embeddingService.embed("");
        assertTrue(empty.stream().allMatch(v -> v == 0f), "空文本应得到零向量");
    }

    @Test
    @DisplayName("相同文本的向量必须完全一致（可复现性）")
    void deterministic() {
        String text = "个人文档智能归档检索平台，支持语义检索与自动打标签";
        assertEquals(embeddingService.embed(text), embeddingService.embed(text));
    }

    @Test
    @DisplayName("用词相近的文本相似度，应高于完全不相关的文本")
    void relatedTextScoresHigher() {
        String query = "如何实现向量语义检索";
        String related = "向量语义检索的实现原理是把文本转成向量再算余弦相似度";
        String unrelated = "今天天气不错，适合出门爬山和野餐";

        double relatedScore = VectorUtil.cosine(
                toArr(embeddingService.embed(query)), toArr(embeddingService.embed(related)));
        double unrelatedScore = VectorUtil.cosine(
                toArr(embeddingService.embed(query)), toArr(embeddingService.embed(unrelated)));

        assertTrue(relatedScore > unrelatedScore,
                "相关文本相似度(" + relatedScore + ")应高于无关文本(" + unrelatedScore + ")");
    }

    private float[] toArr(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}
