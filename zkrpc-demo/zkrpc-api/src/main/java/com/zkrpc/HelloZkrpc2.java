package com.zkrpc;

public interface HelloZkrpc2 {
    /**
     * 通用接口，server和client都需要依赖
     * @param msg 自定义字符串，发送的具体的消息
     * @return 返回的字符串
     */
    String sayHi(String msg);
}
