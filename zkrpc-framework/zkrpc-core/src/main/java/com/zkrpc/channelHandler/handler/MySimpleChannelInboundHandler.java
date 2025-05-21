package com.zkrpc.channelHandler.handler;

import com.zkrpc.YrpcBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

/**
 * 这是一个用来测试的一个类
 */
public class MySimpleChannelInboundHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf msg) throws Exception {
        String result = msg.toString(Charset.defaultCharset());
        //从全局的挂起的请求中寻找与之匹配的待处理的cf
        CompletableFuture<Object> completableFuture = YrpcBootstrap.PENDING_REQUEST.get(1L);
        completableFuture.complete(result);
    }
}
