package com.zkrpc;
import com.zkrpc.channelhandler.ConsumerChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * 提供bootstrap单列
 * todo:这里会有什么问题
 */
@Slf4j
public class NettyBootstrapInitializer {

    private static final Bootstrap bootstrap = new Bootstrap();
    static{
        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ConsumerChannelInitializer());
    }

    private NettyBootstrapInitializer() {}

    public static Bootstrap getBootstrap() {
        //建立一个新的channel
        return bootstrap;

    }
}
