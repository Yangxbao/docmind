package com.docmind.common;

/**
 * 统一接口返回体。
 * 所有接口都返回 {code, msg, data} 三段式结构，前端只需按 code 判断成功/失败。
 */
public class Result<T> {

    public static final int SUCCESS = 200;

    private int code;    // 200 = 成功，其他都是失败
    private String msg;  // 给用户看的提示信息
    private T data;      // 业务数据，失败时为 null

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS, "ok", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS, "ok", data);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(500, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public int getCode() { return code; }

    public String getMsg() { return msg; }

    public T getData() { return data; }
}
