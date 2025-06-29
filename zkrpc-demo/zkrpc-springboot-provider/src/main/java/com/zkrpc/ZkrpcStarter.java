package com.zkrpc;

import com.zkrpc.discovery.RegistryConfig;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ZkrpcStarter implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(5000);
        log.info("zkrpc start.........");
        ZkrpcBootstrap.getInstance()
                .application("first-yrpc-provider")
                //配置注册中心
                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
                //设置序列化机制
                .serialize("jdk")
                //发布服务
//                .publish(service)
                //扫包批量发布
                .scan("com.zkrpc.impl")
                //启动服务
                .start();
    }
}
