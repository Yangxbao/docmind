package com.docmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 文档表实体（对应 document 表）。
 * 一条记录 = 一个上传的文件 + 它提取出来的全文（content）。
 */
@TableName("document")
public class Document {

    /** 处理状态常量 */
    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fileName;

    /** md / txt / pdf / image */
    private String fileType;

    private String filePath;

    private Long fileSize;

    private String fileMd5;

    /** 提取出的全文（含 OCR 结果），关键词检索和摘要都基于它 */
    private String content;

    private String summary;

    private Integer status;

    private String failReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updateTime;

    /** 逻辑删除标记：删除只把 deleted 置 1，配合 @TableLogic 查询自动过滤 */
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }

    public void setUserId(Long userId) { this.userId = userId; }

    public String getFileName() { return fileName; }

    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }

    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getFilePath() { return filePath; }

    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileSize() { return fileSize; }

    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileMd5() { return fileMd5; }

    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getSummary() { return summary; }

    public void setSummary(String summary) { this.summary = summary; }

    public Integer getStatus() { return status; }

    public void setStatus(Integer status) { this.status = status; }

    public String getFailReason() { return failReason; }

    public void setFailReason(String failReason) { this.failReason = failReason; }

    public LocalDateTime getCreateTime() { return createTime; }

    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }

    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Integer getDeleted() { return deleted; }

    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
