package com.docmind.controller;

import com.docmind.common.Result;
import com.docmind.dto.PageQueryDTO;
import com.docmind.dto.TagsUpdateDTO;
import com.docmind.service.DocumentService;
import com.docmind.vo.DocVO;
import com.docmind.vo.UploadResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 文档接口：上传 / 列表 / 详情 / 删除 / 标签 / 重建索引 / 文件预览下载 */
@Tag(name = "文档")
@RestController
@RequestMapping("/api/doc")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "上传文档（支持多文件/文件夹导入，立即返回，后台异步解析）")
    @PostMapping("/upload")
    public Result<List<UploadResultVO>> upload(@RequestParam("files") List<MultipartFile> files) {
        return Result.ok(documentService.upload(files));
    }

    @Operation(summary = "分页查询文档（关键词/类型/标签筛选）")
    @GetMapping("/page")
    public Result<PageVO<DocVO>> page(PageQueryDTO query) {
        var page = documentService.page(query);
        return Result.ok(new PageVO<>(page.getRecords(), page.getTotal(),
                page.getCurrent(), page.getSize()));
    }

    /** MyBatis-Plus 的 Page 直接返回会带一堆排序字段，这里转成干净的 {records,total,current,size} */
    public record PageVO<T>(List<T> records, long total, long current, long size) {
    }

    @Operation(summary = "文档详情")
    @GetMapping("/{id}")
    public Result<DocVO> detail(@PathVariable Long id) {
        return Result.ok(documentService.detail(id));
    }

    @Operation(summary = "修改文档标签（整体替换）")
    @PutMapping("/{id}/tags")
    public Result<Void> updateTags(@PathVariable Long id, @Valid @RequestBody TagsUpdateDTO dto) {
        documentService.updateTags(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "重建向量索引（切换向量模型后使用）")
    @PostMapping("/reindex")
    public Result<Integer> reindex() {
        return Result.ok(documentService.reindex());
    }

    /**
     * 文件预览/下载。
     * preview=true 时 Content-Disposition 为 inline（浏览器内嵌打开），
     * 前端预览页通过 axios 以 blob 拉取（自动带 token），所以这里不需要免登录。
     */
    @Operation(summary = "文件预览/下载")
    @GetMapping("/{id}/file")
    public ResponseEntity<FileSystemResource> file(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "false") boolean download) throws IOException {
        DocumentService.FilePayload payload = documentService.getFile(id);
        ContentDisposition disposition = ContentDisposition
                .builder(download ? "attachment" : "inline")
                .filename(payload.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(payload.mediaType())
                .contentLength(payload.resource().contentLength())
                .body(payload.resource());
    }
}
