package com.zkrpc.core;

import com.zkrpc.loadbalancer.LoadBalancer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public class ShutDownHolder {

    // 用来标记请求的次数
    public static AtomicBoolean BAFFLE = new AtomicBoolean(false);

    //用于请求的计数器
    public static LongAdder REQUEST_COUNTER = new LongAdder();
}
