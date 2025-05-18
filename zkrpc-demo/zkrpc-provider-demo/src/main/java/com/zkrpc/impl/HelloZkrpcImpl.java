package com.zkrpc.impl;

import com.zkrpc.HelloZkrpc;

public class HelloZkrpcImpl implements HelloZkrpc {
    @Override
    public String sayHi(String msg) {
        return "Hi consumer:" + msg;
    }
}
