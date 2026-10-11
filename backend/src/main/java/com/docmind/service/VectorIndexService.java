package com.docmind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docmind.entity.DocChunk;
import com.docmind.mapper.DocChunkMapper;
import com.docmind.util.VectorUtil;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量索引（本项目的"轻量向量库"）。
 *
 * 为什么不用 Milvus/Qdrant 这类独立向量数据库？
 *   个人场景的量级：500 篇文档 × 平均 20 块 ≈ 1 万个向量，
 *   按最大 1024 维算也就 40MB 内存，一次全量余弦计算毫秒级返回。
 *   为这个规模引入一个常驻向量库服务，部署复杂度远大于收益。
 *   —— 用匹配规模的简单方案，同时把向量读写收敛在本类里：
 *   将来数据量大了，只需要替换这个类，检索接口完全不用动。
 *
 * 工作方式：
 *   1. 检索时把"当前用户"的全部分块向量一次性加载进内存（按用户惰性加载）；
 *   2. 之后每次检索都是纯内存余弦计算；
 *   3. 文档上传/删除/重建索引后调用 invalidate() 让缓存失效，下次检索重新加载。
 */
@Service
public class VectorIndexService {

    /** 内存中的一块向量：属于哪个文档、原文是什么、向量值 */
    public record ChunkVector(long chunkId, long docId, String content, float[] vector) {
    }

    /** 带相似度得分的结果 */
    public record ScoredChunk(ChunkVector chunk, double score) {
    }

    private final DocChunkMapper docChunkMapper;

    /** userId → 该用户的内存索引 */
    private final Map<Long, List<ChunkVector>> cache = new ConcurrentHashMap<>();

    public VectorIndexService(DocChunkMapper docChunkMapper) {
        this.docChunkMapper = docChunkMapper;
    }

    /** 获取某用户的内存索引（没有则从数据库加载一次） */
    public List<ChunkVector> getIndex(Long userId) {
        return cache.computeIfAbsent(userId, this::loadFromDb);
    }

    /** 索引失效：文档有增删改后调用，下次检索重新从数据库加载 */
    public void invalidate(Long userId) {
        cache.remove(userId);
    }

    /**
     * 全量余弦检索：query 向量与该用户所有块算相似度，返回最相似的 topK 个块。
     * 1 万块的余弦计算是纯 CPU 乘加，毫秒级完成。
     */
    public List<ScoredChunk> searchTopK(Long userId, float[] queryVector, int topK) {
        return getIndex(userId).stream()
                .map(cv -> new ScoredChunk(cv, VectorUtil.cosine(queryVector, cv.vector())))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    /** 从 doc_chunk 表加载某用户所有带向量的块 */
    private List<ChunkVector> loadFromDb(Long userId) {
        List<DocChunk> chunks = docChunkMapper.selectList(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getUserId, userId)
                .isNotNull(DocChunk::getEmbedding));
        // 个别块的向量如果损坏（比如中途切换了向量模型），跳过而不是让整体失败
        return chunks.stream()
                .map(chunk -> {
                    try {
                        float[] vector = VectorUtil.fromBytes(chunk.getEmbedding());
                        return new ChunkVector(chunk.getId(), chunk.getDocumentId(), chunk.getContent(), vector);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
