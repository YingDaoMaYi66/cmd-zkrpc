package com.zkrpc.channelHandler.handler;

import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class MethodCallHandler extends SimpleChannelInboundHandler<ZkrpcRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ZkrpcRequest zkrpcRequest) throws Exception {

    }
}
