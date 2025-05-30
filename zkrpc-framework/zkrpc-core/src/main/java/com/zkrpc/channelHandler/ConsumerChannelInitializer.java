package com.zkrpc.channelHandler;

import com.zkrpc.channelHandler.handler.MySimpleChannelInboundHandler;
import com.zkrpc.channelHandler.handler.ZkrpcMessageEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;


public class ConsumerChannelInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        //netty自带的日志处理器
        socketChannel.pipeline()
                //netty自带的日志处理器
                .addLast(new LoggingHandler(LogLevel.DEBUG))
                //消息编码器
                .addLast(new ZkrpcMessageEncoder())
                //入站的处理器
                .addLast(new MySimpleChannelInboundHandler());
    }
}
