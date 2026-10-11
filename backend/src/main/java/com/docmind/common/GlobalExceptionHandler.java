package com.docmind.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器。
 * 任何 Controller/Service 抛出的异常都会到这里被"接住"，统一转成 Result 返回，
 * 前端永远拿到的是结构化 JSON，而不是一片 500 错误页。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：属于"预期内"的错误（如用户名已存在），只记 info，直接把原因告诉前端 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.info("业务异常: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    /** 参数校验异常：@Valid 校验失败，把第一条校验错误返回给前端 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("参数不合法");
        return Result.fail(400, msg);
    }

    /** 上传文件超过 spring 配置的大小限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return Result.fail(400, "单个文件不能超过 50MB");
    }

    /** 兜底：未预料到的异常。必须记完整堆栈方便排查，但只给前端一句通用提示（不泄露内部细节） */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
