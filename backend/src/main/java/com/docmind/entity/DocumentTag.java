package com.docmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 文档-标签关联实体（对应 document_tag 表），维护"文档 ↔ 标签"多对多关系 */
@TableName("document_tag")
public class DocumentTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long tagId;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getDocumentId() { return documentId; }

    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Long getTagId() { return tagId; }

    public void setTagId(Long tagId) { this.tagId = tagId; }
}
