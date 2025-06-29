package com.zkrpc.impl;

import com.zkrpc.HelloZkrpc;
import com.zkrpc.annotation.ZkrpcApi;

@ZkrpcApi(group = "primary")
public class HelloZkrpcImpl implements HelloZkrpc {
    @Override
    public String sayHi(String msg) {
        return "Hi consumer:" + msg;
    }
}
