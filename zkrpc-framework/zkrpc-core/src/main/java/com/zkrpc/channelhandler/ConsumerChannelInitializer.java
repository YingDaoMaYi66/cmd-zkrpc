package com.zkrpc.channelhandler;

import com.zkrpc.channelhandler.handler.MySimpleChannelInboundHandler;
import com.zkrpc.channelhandler.handler.ZkrpcRequestEncoder;
import com.zkrpc.channelhandler.handler.ZkrpcResponseDecoder;
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
                //出站的消息编码器
                .addLast(new ZkrpcRequestEncoder())
                //入站的解码器
                .addLast(new ZkrpcResponseDecoder())
                //入站的处理器
                .addLast(new MySimpleChannelInboundHandler());
    }
}
