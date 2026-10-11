package com.docmind.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.docmind.common.BizException;
import com.docmind.common.UserContext;
import com.docmind.dto.PageQueryDTO;
import com.docmind.dto.TagsUpdateDTO;
import com.docmind.entity.Document;
import com.docmind.entity.Tag;
import com.docmind.mapper.DocumentMapper;
import com.docmind.vo.DocVO;
import com.docmind.vo.UploadResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档服务：上传、分页列表、详情、删除、改标签、重建索引、文件预览下载。
 * 这是最贴近 HTTP 接口的一层业务；耗时的"智能处理"都委托给 DocProcessService 异步流水线。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_MANUAL_TAGS = 10;

    /** 允许上传的扩展名 → 文件类型（安全：白名单之外的文件一律拒绝） */
    private static final Map<String, String> EXT_TYPE = Map.of(
            "md", "md", "txt", "txt", "pdf", "pdf",
            "png", "image", "jpg", "image", "jpeg", "image", "bmp", "image", "webp", "image");

    private final DocumentMapper documentMapper;
    private final DocProcessService docProcessService;
    private final FileStorageService fileStorageService;
    private final TagService tagService;
    private final VectorIndexService vectorIndexService;

    public DocumentService(DocumentMapper documentMapper,
                           DocProcessService docProcessService,
                           FileStorageService fileStorageService,
                           TagService tagService,
                           VectorIndexService vectorIndexService) {
        this.documentMapper = documentMapper;
        this.docProcessService = docProcessService;
        this.fileStorageService = fileStorageService;
        this.tagService = tagService;
        this.vectorIndexService = vectorIndexService;
    }

    /** 文件预览/下载的返回载体：文件资源 + 文件名 + Content-Type */
    public record FilePayload(FileSystemResource resource, String fileName, MediaType mediaType) {
    }

    // ==================== 上传 ====================

    /**
     * 批量上传：逐个文件处理，每个文件都有独立结果（成功/重复/不支持/失败），
     * 单个文件出问题不影响其他文件。
     */
    public List<UploadResultVO> upload(List<MultipartFile> files) {
        Long userId = UserContext.get();
        List<UploadResultVO> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadOne(userId, file));
        }
        return results;
    }

    private UploadResultVO uploadOne(Long userId, MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return new UploadResultVO("未知文件", false, "文件名为空", null);
        }
        // 文件夹导入时浏览器会带相对路径，这里只保留文件名本身
        name = Paths.get(name.replace("\\", "/")).getFileName().toString();
        try {
            String type = EXT_TYPE.get(FileStorageService.getExtension(name));
            if (type == null) {
                return new UploadResultVO(name, false, "不支持的文件类型", null);
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                return new UploadResultVO(name, false, "超过 50MB 大小限制", null);
            }
            byte[] data = file.getBytes();

            // MD5 内容去重：同一用户下内容完全相同的文件只存一份
            String md5 = DigestUtil.md5Hex(data);
            Long dup = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                    .eq(Document::getUserId, userId)
                    .eq(Document::getFileMd5, md5));
            if (dup != null && dup > 0) {
                return new UploadResultVO(name, false, "内容与已有文档重复，已跳过", null);
            }

            String path = fileStorageService.save(userId, name, data);
            Document doc = new Document();
            doc.setUserId(userId);
            doc.setFileName(name);
            doc.setFileType(type);
            doc.setFilePath(path);
            doc.setFileSize((long) data.length);
            doc.setFileMd5(md5);
            doc.setStatus(Document.STATUS_PROCESSING);
            documentMapper.insert(doc);

            // 关键设计：耗时处理丢给异步流水线，接口立刻返回，前端轮询文档状态
            docProcessService.processAsync(doc.getId(), userId);
            log.info("文件已接收: {} ({} 字节)，后台解析中", name, data.length);
            return new UploadResultVO(name, true, "已提交，后台解析中", doc.getId());
        } catch (Exception e) {
            log.error("文件上传失败: {}", name, e);
            return new UploadResultVO(name, false, "上传失败: " + e.getMessage(), null);
        }
    }

    // ==================== 分页列表 ====================

    public Page<DocVO> page(PageQueryDTO query) {
        Long userId = UserContext.get();
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 10 : Math.min(query.getPageSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            // 关键词同时模糊匹配文件名和正文（content 是提取出的全文）
            wrapper.and(w -> w.like(Document::getFileName, query.getKeyword())
                    .or().like(Document::getContent, query.getKeyword()));
        }
        if (query.getType() != null && !"all".equals(query.getType())) {
            wrapper.eq(Document::getFileType, query.getType());
        }
        if (query.getTagId() != null) {
            // 先按标签反查文档id列表；该标签下没有文档就直接返回空页
            List<Long> docIds = tagService.docIdsByTag(query.getTagId());
            if (docIds.isEmpty()) {
                return emptyPage(pageNum, pageSize);
            }
            wrapper.in(Document::getId, docIds);
        }
        wrapper.orderByDesc(Document::getCreateTime);

        Page<Document> page = documentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        // 批量加载这页文档的标签（固定两次查询，避免 N+1）
        Map<Long, List<String>> tagsMap = tagService.loadTagsMap(
                page.getRecords().stream().map(Document::getId).toList());

        Page<DocVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(doc -> toVO(doc, tagsMap.getOrDefault(doc.getId(), List.of())))
                .toList());
        return result;
    }

    private Page<DocVO> emptyPage(int pageNum, int pageSize) {
        Page<DocVO> page = new Page<>(pageNum, pageSize, 0);
        page.setRecords(List.of());
        return page;
    }

    // ==================== 详情 / 标签 / 删除 ====================

    public DocVO detail(Long id) {
        Document doc = getOwned(id);
        return toVO(doc, tagService.loadTagsMap(List.of(id)).getOrDefault(id, List.of()));
    }

    /** 手动修改标签：传入的标签列表整体替换旧标签（传空列表即清空标签） */
    public void updateTags(Long id, TagsUpdateDTO dto) {
        Document doc = getOwned(id);
        List<String> tags = dto.tags() == null ? List.of() : dto.tags().stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::strip)
                .distinct()
                .limit(MAX_MANUAL_TAGS)
                .toList();
        tagService.clearDocTags(id);
        for (String name : tags) {
            Tag tag = tagService.findOrCreate(doc.getUserId(), name);
            tagService.bindTag(id, tag.getId());
        }
    }

    /** 删除文档：逻辑删文档 + 物理删分块 + 删磁盘文件 + 内存索引失效 */
    public void delete(Long id) {
        Document doc = getOwned(id);
        documentMapper.deleteById(id);                       // 逻辑删除（deleted=1）
        docProcessService.deleteChunks(id);                  // 分块没有逻辑删除字段，物理删
        fileStorageService.delete(doc.getFilePath());        // 磁盘文件
        vectorIndexService.invalidate(doc.getUserId());      // 内存索引失效
        log.info("文档已删除: {}", doc.getFileName());
    }

    /**
     * 重建索引：清空该用户全部分块 → 所有文档重新走一遍处理流水线。
     * 使用场景：切换向量模型（tfidf ↔ ollama）后，旧向量和新查询向量不在同一空间，必须重建。
     */
    public int reindex() {
        Long userId = UserContext.get();
        List<Document> docs = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId));
        docProcessService.deleteUserChunks(userId);
        for (Document doc : docs) {
            doc.setStatus(Document.STATUS_PROCESSING);
            doc.setFailReason("");
            documentMapper.updateById(doc);
            docProcessService.processAsync(doc.getId(), userId);
        }
        vectorIndexService.invalidate(userId);
        log.info("用户 {} 触发重建索引，共 {} 篇文档", userId, docs.size());
        return docs.size();
    }

    // ==================== 文件预览 / 下载 ====================

    /** 读取磁盘文件，用于在线预览（inline）或下载（attachment） */
    public FilePayload getFile(Long id) {
        Document doc = getOwned(id);
        Path path = fileStorageService.resolve(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BizException("磁盘文件已丢失");
        }
        return new FilePayload(new FileSystemResource(path), doc.getFileName(), mediaTypeOf(doc));
    }

    /** 按文件类型返回浏览器能识别的 Content-Type */
    private MediaType mediaTypeOf(Document doc) {
        String ext = FileStorageService.getExtension(doc.getFileName());
        return switch (doc.getFileType()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "image" -> switch (ext) {
                case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
                case "webp" -> MediaType.parseMediaType("image/webp");
                case "bmp" -> MediaType.parseMediaType("image/bmp");
                default -> MediaType.IMAGE_PNG;
            };
            // md/txt 用 UTF-8 文本返回（前端拿原文渲染 Markdown）
            default -> new MediaType("text", "plain", StandardCharsets.UTF_8);
        };
    }

    // ==================== 私有辅助 ====================

    /** 校验"文档存在且属于当前用户"，防止越权访问别人的文档 */
    private Document getOwned(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !doc.getUserId().equals(UserContext.get())) {
            throw new BizException("文档不存在");
        }
        return doc;
    }

    private DocVO toVO(Document doc, List<String> tags) {
        return new DocVO(doc.getId(), doc.getFileName(), doc.getFileType(), doc.getFileSize(),
                doc.getSummary(), doc.getStatus(), doc.getFailReason(), doc.getCreateTime(), tags);
    }
}
