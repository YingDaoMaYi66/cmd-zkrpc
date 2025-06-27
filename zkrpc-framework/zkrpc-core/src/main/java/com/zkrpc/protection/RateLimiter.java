package com.zkrpc.protection;

public interface RateLimiter {
    /**
     * 是否允许新的请求进入
     * @return true 表示允许请求，false表示不允许请求
     */
    boolean allowRequest();
}
