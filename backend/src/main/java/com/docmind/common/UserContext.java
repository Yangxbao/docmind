package com.docmind.common;

/**
 * 当前登录用户上下文。
 *
 * JwtInterceptor 校验完 token 后，把 userId 放进 ThreadLocal；
 * 后续 Service/Controller 里用 UserContext.get() 就能拿到当前用户，
 * 不用每个方法都传 userId 参数。
 *
 * 注意：拦截器在请求结束时调用 clear() 清理，防止线程池复用导致"用户串号"；
 *       异步线程（文档解析）拿不到 ThreadLocal，所以 userId 是作为参数显式传过去的。
 */
public class UserContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    public static void set(Long userId) {
        HOLDER.set(userId);
    }

    /** 获取当前登录用户id，理论上拦截器保证了这里一定有值 */
    public static Long get() {
        Long userId = HOLDER.get();
        if (userId == null) {
            throw new BizException("未登录或登录已过期");
        }
        return userId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
