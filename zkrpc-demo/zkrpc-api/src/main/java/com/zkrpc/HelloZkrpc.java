package com.zkrpc;

import com.zkrpc.annotation.TryTimes;

public interface HelloZkrpc {
    /**
     * 通用接口，server和client都需要依赖
     * @param msg 自定义字符串，发送的具体的消息
     * @return 返回的字符串
     */
    @TryTimes(tryTimes = 3, intervalTime = 3000)
    String sayHi(String msg);
}
