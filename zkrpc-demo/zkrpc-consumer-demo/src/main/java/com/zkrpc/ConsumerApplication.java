package com.zkrpc;


import com.zkrpc.discovery.RegistryConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsumerApplication {
    public static void main(String[] args) {
        //想尽一些办法获取代理对象,使用ReferenceConfig进行封装
        //reference一定用生成代理的方法模版
        ReferenceConfig<HelloZkrpc> reference = new ReferenceConfig<>();
        reference.setInterface(HelloZkrpc.class);


        //代理做了什么？1、链接注册中心 2、拉取服务列表 3、选择一个服务并建立连接 4、发送请求，携带一些信息(接口名，参数列表，方法的名字)，获得结果
        ZkrpcBootstrap.getInstance()
                .application("first-yrpc-consumer")
                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
                .serialize("hessian")
                .reference(reference);

        //获取一个代理对象
        HelloZkrpc helloZkrpc = reference.get();
        String sayHi = helloZkrpc.sayHi("你好zkrpc");
        log.info("sayHi:{}", sayHi);
    }
}
