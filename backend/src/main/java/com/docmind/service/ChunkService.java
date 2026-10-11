package com.docmind.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块服务。
 *
 * 为什么要分块？整篇文档压缩成一个向量会"稀释"语义——5 万字的文档压成 1 个向量，
 * 检索时根本说不清这篇文档里哪一段和你的问题相关。
 * 把文档切成 500 字左右的块、每块单独向量化，才能做到"命中具体段落"。
 */
@Service
public class ChunkService {

    /** 每块目标长度（字符）。太小语义不完整，太大向量被稀释，500 是常用的经验值 */
    private static final int CHUNK_SIZE = 500;

    /** 相邻块的重叠长度，避免关键句子刚好被切断导致语义丢失 */
    private static final int OVERLAP = 100;

    /**
     * 把长文本切成适合向量化的块：
     *   1. 统一换行符、压缩多余空行；
     *   2. 按"段落"（空行分隔）聚合，凑满 CHUNK_SIZE 就成一块；
     *   3. 单段超过 CHUNK_SIZE 的（比如没有空行的长文），按固定步长硬切，块间保留 OVERLAP 重叠。
     */
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").replaceAll("\n{3,}", "\n\n").strip();

        StringBuilder buffer = new StringBuilder();
        for (String rawPara : normalized.split("\n\n+")) {
            String para = rawPara.strip();
            if (para.isEmpty()) {
                continue;
            }
            if (para.length() > CHUNK_SIZE) {
                // 超长段落：先把 buffer 里积累的内容落成一块，再硬切本段
                flush(buffer, chunks);
                int step = CHUNK_SIZE - OVERLAP;
                for (int i = 0; i < para.length(); i += step) {
                    chunks.add(para.substring(i, Math.min(para.length(), i + CHUNK_SIZE)));
                    if (para.length() - i <= CHUNK_SIZE) {
                        break;
                    }
                }
                continue;
            }
            // 普通段落：累积到 CHUNK_SIZE 就成块
            if (buffer.length() + para.length() + 1 > CHUNK_SIZE) {
                flush(buffer, chunks);
            }
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(para);
        }
        flush(buffer, chunks);
        return chunks;
    }

    /** 把积累的 buffer 输出为一个块并清空 */
    private void flush(StringBuilder buffer, List<String> chunks) {
        if (buffer.length() > 0) {
            chunks.add(buffer.toString());
            buffer.setLength(0);
        }
    }
}
