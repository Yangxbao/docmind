package com.docmind.common;

/**
 * 业务异常。
 * 业务代码里遇到"请求不合法/数据不存在/外部依赖不可用"等情况直接 throw，
 * 由 GlobalExceptionHandler 统一捕获并转成友好的 Result 返回，避免到处 try-catch。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
