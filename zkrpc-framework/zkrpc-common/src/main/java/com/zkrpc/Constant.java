package com.zkrpc;

public class Constant {
    //zookeeper的默认链接地址
    public static final String DEFAULT_zk_CONNECT = "127.0.0.1:2181";
    //zookeeper默认的连接超时时间
    public static final int TIME_OUT = 1000;
    //服务提供方和调用方在代码当中的基础路径
    public  static final String BASE_PROVIDERS_PATH = "/zkrpc-metadata/providers";
    public  static final String BASE_CONSUMERS_PATH = "/zkrpc-metadate/consumers";

}
