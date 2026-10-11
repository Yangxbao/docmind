package com.docmind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docmind.entity.DocChunk;
import com.docmind.entity.Document;
import com.docmind.entity.DocumentTag;
import com.docmind.entity.Tag;
import com.docmind.mapper.DocChunkMapper;
import com.docmind.mapper.DocumentMapper;
import com.docmind.mapper.DocumentTagMapper;
import com.docmind.mapper.TagMapper;
import com.docmind.util.VectorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 文档处理流水线（本项目的"智能归档"核心）。
 *
 * 上传接口只负责存文件、建记录（状态=处理中）就立即返回，
 * 这里的异步方法在后台线程完成剩下的全部工作：
 *
 *   ① 解析正文：MD/TXT 直读，PDF 提文字层，图片/扫描PDF 走 OCR
 *   ② 全文入库 + 生成摘要（首个非空段落前 200 字）
 *   ③ 文本分块（500 字/块，重叠 100 字）
 *   ④ 每块向量化，向量以 BLOB 存入 doc_chunk
 *   ⑤ 自动打标签（规则分类 + jieba 词频）
 *   ⑥ 状态置"完成"，并让该用户的内存向量索引失效（下次检索自动重载）
 *
 * 任何一步失败：状态置"失败"并记录原因，前端列表可见。
 * 注意：异步线程里拿不到 ThreadLocal，所以 userId 由调用方显式传入。
 */
@Service
public class DocProcessService {

    private static final Logger log = LoggerFactory.getLogger(DocProcessService.class);

    private final DocumentMapper documentMapper;
    private final DocChunkMapper docChunkMapper;
    private final TagMapper tagMapper;
    private final DocumentTagMapper documentTagMapper;
    private final FileStorageService fileStorageService;
    private final TextExtractService textExtractService;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final TagService tagService;
    private final VectorIndexService vectorIndexService;

    public DocProcessService(DocumentMapper documentMapper,
                             DocChunkMapper docChunkMapper,
                             TagMapper tagMapper,
                             DocumentTagMapper documentTagMapper,
                             FileStorageService fileStorageService,
                             TextExtractService textExtractService,
                             ChunkService chunkService,
                             EmbeddingService embeddingService,
                             TagService tagService,
                             VectorIndexService vectorIndexService) {
        this.documentMapper = documentMapper;
        this.docChunkMapper = docChunkMapper;
        this.tagMapper = tagMapper;
        this.documentTagMapper = documentTagMapper;
        this.fileStorageService = fileStorageService;
        this.textExtractService = textExtractService;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.tagService = tagService;
        this.vectorIndexService = vectorIndexService;
    }

    /** 异步入口：调用方是上传接口/重建索引，方法带 @Async，实际在 doc-process 线程池执行 */
    @Async("docProcessExecutor")
    public void processAsync(Long docId, Long userId) {
        try {
            process(docId, userId);
        } catch (Exception e) {
            log.error("文档处理失败 docId={}", docId, e);
            markFailed(docId, e.getMessage());
        }
    }

    /** 完整流水线（公开方法，方便单元测试和重建索引复用） */
    public void process(Long docId, Long userId) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null) {
            return;   // 可能刚上传就被删除了
        }
        long start = System.currentTimeMillis();

        // ① 解析正文
        Path file = fileStorageService.resolve(doc.getFilePath());
        String text = textExtractService.extract(file, doc.getFileType());

        // ② 全文入库 + 摘要
        doc.setContent(text);
        doc.setSummary(buildSummary(text));
        documentMapper.updateById(doc);

        // ③④ 分块 + 向量化入库
        if (!text.isBlank()) {
            List<String> chunks = chunkService.chunk(text);
            int seq = 0;
            for (String chunk : chunks) {
                List<Float> vector = embeddingService.embed(chunk);
                DocChunk entity = new DocChunk();
                entity.setDocumentId(docId);
                entity.setUserId(userId);
                entity.setSeq(seq++);
                entity.setContent(chunk);
                entity.setEmbedding(VectorUtil.toBytes(vector));
                entity.setEmbeddingDim(vector.size());
                docChunkMapper.insert(entity);
            }
            log.info("文档[{}]归档完成: {} 块, 耗时 {} ms", doc.getFileName(), chunks.size(),
                    System.currentTimeMillis() - start);
        } else {
            // 常见于纯图片且未配置 OCR 语言包：文档保留可预览，只是没有可检索的文本
            log.warn("文档[{}]未提取到文本内容（可能是纯图片且 OCR 语言包未配置）", doc.getFileName());
        }

        // ⑤ 自动标签（先清旧的再重建，"重建索引"功能复用同一条流水线）
        saveAutoTags(doc, text);

        // ⑥ 完成 + 索引失效
        doc.setStatus(Document.STATUS_DONE);
        documentMapper.updateById(doc);
        vectorIndexService.invalidate(userId);
    }

    /** 标记失败：状态置 2，记录原因（截断到 500 字，与表字段长度一致） */
    private void markFailed(Long docId, String reason) {
        try {
            Document doc = documentMapper.selectById(docId);
            if (doc == null) {
                return;
            }
            doc.setStatus(Document.STATUS_FAILED);
            doc.setFailReason(reason == null ? "未知错误"
                    : reason.substring(0, Math.min(reason.length(), 500)));
            documentMapper.updateById(doc);
        } catch (Exception e) {
            log.error("标记文档失败状态时出错 docId={}", docId, e);
        }
    }

    /** 保存自动标签：清空旧关联 → 生成标签 → 找到或创建 → 建立关联 */
    private void saveAutoTags(Document doc, String text) {
        tagService.clearDocTags(doc.getId());
        List<String> tags = tagService.autoTags(doc.getFileName(), text);
        for (String name : tags) {
            Tag tag = tagService.findOrCreate(doc.getUserId(), name);
            tagService.bindTag(doc.getId(), tag.getId());
        }
    }

    /** 摘要：取第一个长度≥10 的段落前 200 字；全文太短就直接取全文开头 */
    private String buildSummary(String text) {
        if (text == null || text.isBlank()) {
            return "（未提取到文本内容）";
        }
        String stripped = text.strip();
        for (String para : stripped.split("\n+")) {
            String t = para.strip();
            if (t.length() >= 10) {
                return t.substring(0, Math.min(200, t.length())).replaceAll("\\s+", " ");
            }
        }
        return stripped.substring(0, Math.min(200, stripped.length())).replaceAll("\\s+", " ");
    }

    /** 删除某文档的全部分块（删除文档时调用） */
    public void deleteChunks(Long documentId) {
        docChunkMapper.delete(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getDocumentId, documentId));
    }

    /** 删除某用户的全部分块（重建索引前调用，旧向量全部作废） */
    public void deleteUserChunks(Long userId) {
        docChunkMapper.delete(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getUserId, userId));
    }
}
