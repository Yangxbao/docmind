package com.docmind.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分块服务单元测试（纯逻辑，不依赖数据库和 Spring 容器，秒级跑完）。
 */
class ChunkServiceTest {

    private final ChunkService chunkService = new ChunkService();

    @Test
    @DisplayName("空文本/空白文本应返回空列表")
    void chunkBlankText() {
        assertTrue(chunkService.chunk(null).isEmpty());
        assertTrue(chunkService.chunk("   \n\n  ").isEmpty());
    }

    @Test
    @DisplayName("短文本应聚合成一个块")
    void shortTextSingleChunk() {
        List<String> chunks = chunkService.chunk("第一段内容\n\n第二段内容");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("第一段内容"));
        assertTrue(chunks.get(0).contains("第二段内容"));
    }

    @Test
    @DisplayName("超过500字的长段应硬切成多块，且相邻块有重叠")
    void longParagraphSplitWithOverlap() {
        // 生成 1200 个字符的连续长文（没有空行，段落无法分割）
        String longText = "甲".repeat(1200);
        List<String> chunks = chunkService.chunk(longText);
        assertTrue(chunks.size() >= 3, "1200字至少应切成3块");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 500, "每块不能超过 CHUNK_SIZE=500");
        }
        // 相邻块之间应有 OVERLAP=100 的重叠
        String first = chunks.get(0);
        String second = chunks.get(1);
        String tail = first.substring(first.length() - 100);
        assertTrue(second.startsWith(tail), "第二块应以第一块的末尾100字开头（重叠区）");
    }

    @Test
    @DisplayName("多段落文本不应丢失内容")
    void paragraphsPreserved() {
        String text = ("段落" + "内容").repeat(30) + "\n\n" + ("结尾" + "部分").repeat(30);
        List<String> chunks = chunkService.chunk(text);
        String joined = String.join("", chunks);
        assertTrue(joined.contains("结尾"), "后置段落内容不能丢");
        assertFalse(chunks.isEmpty());
    }
}
