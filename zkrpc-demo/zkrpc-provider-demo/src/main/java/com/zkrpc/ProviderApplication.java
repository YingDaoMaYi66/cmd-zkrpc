package com.zkrpc;

import com.zkrpc.discovery.RegistryConfig;
import com.zkrpc.impl.HelloZkrpcImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProviderApplication {
    public static void main(String[] args) {
        log.debug("ProviderApplication start");

        //服务提供方需要注册服务，启动服务
        // 1、封装要发布的服务
        ServiceConfig<HelloZkrpc> service = new ServiceConfig<>();
        service.setInterface(HelloZkrpc.class);
        service.setRef(new HelloZkrpcImpl());
        // 2、定义注册中心
        // 3、启动引导程序，启动服务提供方
        //   (1) 配置 -- 应用的名称 -- 注册中心
        //   (2) 发布服务
        ZkrpcBootstrap.getInstance()
                .application("first-yrpc-provider")
                //配置注册中心
                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
                //设置序列化机制
                .protocol(new ProtocolConfig("jdk"))
                //发布服务
                .publish(service)
                //启动服务
                .start();
    }
}
