package com.docmind.dto;

/**
 * 文档列表分页查询入参（GET 查询参数自动绑定到这些字段）。
 * 用普通类而不是 record，是为了让 pageNum/pageSize 有默认值。
 */
public class PageQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    /** 关键词：同时模糊匹配文件名和正文内容 */
    private String keyword;

    /** 文件类型筛选：all/md/txt/pdf/image */
    private String type = "all";

    /** 标签id筛选 */
    private Long tagId;

    public Integer getPageNum() { return pageNum; }

    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }

    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public String getKeyword() { return keyword; }

    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public Long getTagId() { return tagId; }

    public void setTagId(Long tagId) { this.tagId = tagId; }
}
