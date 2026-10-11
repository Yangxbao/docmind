package com.docmind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.docmind.common.BizException;
import com.docmind.common.UserContext;
import com.docmind.dto.SearchDTO;
import com.docmind.entity.Document;
import com.docmind.entity.SearchHistory;
import com.docmind.mapper.DocumentMapper;
import com.docmind.mapper.SearchHistoryMapper;
import com.docmind.vo.HistoryVO;
import com.docmind.vo.SearchItemVO;
import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索服务：本项目最核心的用户价值——"用一句话找到内容所在的文档"。
 *
 * 语义模式（semantic）：
 *   query → 向量化 → 与该用户所有文档块算余弦相似度 → 取 Top50 块
 *   → 按文档聚合（文档得分 = 它名下相似度最高的那块）→ 过滤低分 → 返回 Top10 文档。
 *   每条结果附带"命中片段"（相似度最高那块的原文），前端高亮展示。
 *
 * 关键词模式（keyword）：
 *   MySQL LIKE 同时匹配文件名和正文，作为向量化服务不可用时的兜底。
 *
 * 附加：结果写 Redis 缓存 5 分钟；每次搜索记录历史（查询时去重取最近 8 条）。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** 语义检索召回的块数：先多召回一些块，再按文档聚合 */
    private static final int RECALL_TOP_K = 50;
    /** 最终返回的文档数 */
    private static final int RESULT_LIMIT = 10;
    /** 相似度低于这个值的块视为"不相关"，避免强行凑结果 */
    private static final double MIN_SCORE = 0.10;
    private static final long CACHE_SECONDS = 300;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DocumentMapper documentMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final EmbeddingService embeddingService;
    private final VectorIndexService vectorIndexService;
    private final TagService tagService;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    public SearchService(DocumentMapper documentMapper,
                         SearchHistoryMapper searchHistoryMapper,
                         EmbeddingService embeddingService,
                         VectorIndexService vectorIndexService,
                         TagService tagService,
                         RedisService redisService,
                         ObjectMapper objectMapper) {
        this.documentMapper = documentMapper;
        this.searchHistoryMapper = searchHistoryMapper;
        this.embeddingService = embeddingService;
        this.vectorIndexService = vectorIndexService;
        this.tagService = tagService;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    /** 检索主入口 */
    public List<SearchItemVO> search(SearchDTO dto) {
        Long userId = UserContext.get();
        String query = dto.query() == null ? "" : dto.query().strip();
        if (query.isEmpty()) {
            throw new BizException("搜索内容不能为空");
        }
        String mode = "keyword".equals(dto.mode()) ? "keyword" : "semantic";

        // ① 先查 Redis 缓存：同一用户 + 同模式 + 同关键词，5 分钟内直接返回
        String cacheKey = "search:" + userId + ":" + mode + ":" + DigestUtil.md5Hex(query);
        String cached = redisService.get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<SearchItemVO>>() {
                });
            } catch (Exception ignore) {
                // 缓存数据坏了就当作没有缓存，走正常检索
            }
        }

        // ② 执行检索
        List<SearchItemVO> items = "semantic".equals(mode)
                ? semanticSearch(userId, query)
                : keywordSearch(userId, query);

        // ③ 记录搜索历史 + 写缓存（缓存失败不影响返回）
        saveHistory(userId, query, items.size());
        try {
            redisService.set(cacheKey, objectMapper.writeValueAsString(items), CACHE_SECONDS);
        } catch (Exception ignore) {
        }
        return items;
    }

    /**
     * 语义检索：向量余弦相似度，"用意思找文档"。
     */
    private List<SearchItemVO> semanticSearch(Long userId, String query) {
        // 1. 查询词向量化（和文档块用同一个 EmbeddingService，保证在同一向量空间）
        float[] queryVector = toArray(embeddingService.embed(query));

        // 2. 内存索引全量余弦检索，取最相似的 50 块
        List<VectorIndexService.ScoredChunk> hits =
                vectorIndexService.searchTopK(userId, queryVector, RECALL_TOP_K);
        if (hits.isEmpty()) {
            return List.of();
        }

        // 3. 按文档聚合：hits 已按得分降序，每个文档第一次出现的那块就是它的最高分块
        Map<Long, Double> bestScore = new LinkedHashMap<>();
        Map<Long, String> bestSnippet = new HashMap<>();
        for (VectorIndexService.ScoredChunk hit : hits) {
            if (hit.score() < MIN_SCORE) {
                break;   // 后面的更低，直接停
            }
            long docId = hit.chunk().docId();
            if (!bestScore.containsKey(docId)) {
                bestScore.put(docId, hit.score());
                bestSnippet.put(docId, compact(hit.chunk().content(), 200));
            }
        }

        // 4. 按得分排序取前 10 篇文档
        List<Long> topDocIds = bestScore.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(RESULT_LIMIT)
                .map(Map.Entry::getKey)
                .toList();

        // 5. 批量补齐文档信息（文件名/类型/摘要/标签），并校验归属
        Map<Long, Document> docs = new HashMap<>();
        for (Document doc : documentMapper.selectBatchIds(topDocIds)) {
            if (doc.getUserId().equals(userId)) {
                docs.put(doc.getId(), doc);
            }
        }
        Map<Long, List<String>> tagsMap = tagService.loadTagsMap(topDocIds);

        List<SearchItemVO> items = new ArrayList<>();
        for (Long docId : topDocIds) {
            Document doc = docs.get(docId);
            if (doc == null) {
                continue;
            }
            items.add(new SearchItemVO(
                    doc.getId(), doc.getFileName(), doc.getFileType(), doc.getSummary(),
                    tagsMap.getOrDefault(docId, List.of()),
                    round2(bestScore.get(docId)),
                    bestSnippet.get(docId),
                    doc.getCreateTime() == null ? "" : doc.getCreateTime().format(TIME_FMT)));
        }
        log.info("语义检索 [{}] 命中 {} 块, 返回 {} 篇文档", query, hits.size(), items.size());
        return items;
    }

    /**
     * 关键词检索：LIKE 兜底模式。不依赖向量服务，Ollama 没启动也能搜。
     */
    private List<SearchItemVO> keywordSearch(Long userId, String query) {
        Page<Document> page = documentMapper.selectPage(new Page<>(1, 20),
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getUserId, userId)
                        .and(w -> w.like(Document::getFileName, query)
                                .or().like(Document::getContent, query))
                        .orderByDesc(Document::getCreateTime));

        Map<Long, List<String>> tagsMap = tagService.loadTagsMap(
                page.getRecords().stream().map(Document::getId).toList());

        List<SearchItemVO> items = new ArrayList<>();
        for (Document doc : page.getRecords()) {
            items.add(new SearchItemVO(
                    doc.getId(), doc.getFileName(), doc.getFileType(), doc.getSummary(),
                    tagsMap.getOrDefault(doc.getId(), List.of()),
                    null,   // 关键词模式没有相似度分数
                    buildKeywordSnippet(doc, query),
                    doc.getCreateTime() == null ? "" : doc.getCreateTime().format(TIME_FMT)));
        }
        log.info("关键词检索 [{}] 返回 {} 篇文档", query, items.size());
        return items;
    }

    /**
     * 关键词命中片段：在正文里找到第一次出现 query 的位置，
     * 截取前后各 80 字作为上下文；文件名命中但正文没有时退回摘要。
     */
    private String buildKeywordSnippet(Document doc, String query) {
        String content = doc.getContent();
        if (content == null || content.isBlank()) {
            return doc.getSummary() == null ? "" : doc.getSummary();
        }
        int idx = content.indexOf(query);
        if (idx < 0) {
            return compact(content, 160);
        }
        int start = Math.max(0, idx - 80);
        int end = Math.min(content.length(), idx + query.length() + 80);
        return (start > 0 ? "…" : "") + compact(content.substring(start, end), 300) + (end < content.length() ? "…" : "");
    }

    /** 搜索历史：最近 20 条里按关键词去重，取前 8 条 */
    public List<HistoryVO> history() {
        Long userId = UserContext.get();
        List<SearchHistory> recent = searchHistoryMapper.selectList(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .orderByDesc(SearchHistory::getId));
        List<SearchHistory> limited = recent.size() > 20 ? recent.subList(0, 20) : recent;
        List<HistoryVO> result = new ArrayList<>();
        var seen = new java.util.LinkedHashSet<String>();
        for (SearchHistory h : limited) {
            if (seen.add(h.getKeyword())) {
                result.add(new HistoryVO(h.getId(), h.getKeyword(),
                        h.getCreateTime() == null ? "" : h.getCreateTime().format(TIME_FMT)));
            }
            if (result.size() >= 8) {
                break;
            }
        }
        return result;
    }

    private void saveHistory(Long userId, String query, int resultCount) {
        try {
            SearchHistory history = new SearchHistory();
            history.setUserId(userId);
            history.setKeyword(query.length() > 200 ? query.substring(0, 200) : query);
            history.setResultCount(resultCount);
            searchHistoryMapper.insert(history);
        } catch (Exception e) {
            log.warn("保存搜索历史失败: {}", e.getMessage());
        }
    }

    // ==================== 小工具 ====================

    private float[] toArray(List<Float> vector) {
        float[] arr = new float[vector.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = vector.get(i);
        }
        return arr;
    }

    /** 压缩成一行并截断，用于结果摘要片段 */
    private String compact(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= maxLength ? oneLine : oneLine.substring(0, maxLength) + "…";
    }

    private Double round2(double score) {
        return Math.round(score * 1000) / 1000.0;
    }
}
