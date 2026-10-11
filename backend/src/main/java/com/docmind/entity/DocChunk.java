package com.docmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * 文档分块实体（对应 doc_chunk 表）。
 * 一块 = 一段正文 + 这段文字的向量，是语义检索的最小检索单位：
 * 检索时 query 会被向量化，然后和这里每一块的向量算余弦相似度。
 */
@TableName("doc_chunk")
public class DocChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    /** 冗余了 user_id：检索时"按用户一次捞全所有块"，不用关联 document 表 */
    private Long userId;

    /** 块序号，同一文档内从 0 递增 */
    private Integer seq;

    private String content;

    /** 向量（float[] 序列化后的字节）。永远不返回给前端，所以 @JsonIgnore */
    @JsonIgnore
    private byte[] embedding;

    private Integer embeddingDim;

    private LocalDateTime createTime;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getDocumentId() { return documentId; }

    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Long getUserId() { return userId; }

    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getSeq() { return seq; }

    public void setSeq(Integer seq) { this.seq = seq; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public byte[] getEmbedding() { return embedding; }

    public void setEmbedding(byte[] embedding) { this.embedding = embedding; }

    public Integer getEmbeddingDim() { return embeddingDim; }

    public void setEmbeddingDim(Integer embeddingDim) { this.embeddingDim = embeddingDim; }

    public LocalDateTime getCreateTime() { return createTime; }

    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
