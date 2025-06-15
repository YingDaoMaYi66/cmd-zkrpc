package com.zkrpc.impl;
import com.zkrpc.HelloZkrpc2;
import com.zkrpc.annotation.ZkrpcApi;

@ZkrpcApi
public class HelloZkrpcImpl2 implements HelloZkrpc2 {
    @Override
    public String sayHi(String msg) {
        return "Hi consumer:" + msg;
    }
}
