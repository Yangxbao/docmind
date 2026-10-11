package com.docmind.vo;

/**
 * 单个文件的上传结果。
 * 批量上传时每个文件都有独立结果：成功提交 / 内容重复跳过 / 类型不支持 / 失败。
 */
public record UploadResultVO(
        String fileName,
        boolean success,
        String message,
        Long docId
) {
}
