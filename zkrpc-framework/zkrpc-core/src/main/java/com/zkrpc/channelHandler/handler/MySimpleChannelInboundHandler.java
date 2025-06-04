package com.zkrpc.channelHandler.handler;

import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.transport.message.ZkrpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * 这是一个用来测试的一个类
 */
@Slf4j
public class MySimpleChannelInboundHandler extends SimpleChannelInboundHandler<ZkrpcResponse> {
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ZkrpcResponse zkrpcResponse) throws Exception {
        //服务提供方给予的结果
        Object returnValue = zkrpcResponse.getBody();
        //从全局的挂起的请求中寻找与之匹配的待处理的cf
        CompletableFuture<Object> completableFuture = ZkrpcBootstrap.PENDING_REQUEST.get(1L);
        completableFuture.complete(returnValue);
        if(log.isDebugEnabled()){
            log.debug("已经寻找到编号为【{}】的completablefuture，处理响应结果", zkrpcResponse.getRequestId());
        }
    }
}
