package com.zkrpc.core;

import com.zkrpc.NettyBootstrapInitializer;
import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.channelhandler.compress.CompressorFactory;
import com.zkrpc.discovery.Registry;
import com.zkrpc.enumeration.RequestType;
import com.zkrpc.serialize.SerializerFactory;
import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class HeartbeatDetector {

    public static void detectHeartbeat(String ServiceName){

        //1、从注册中心拉取服务列表并建立连接
        Registry registry = ZkrpcBootstrap.getInstance().getRegistry();
        List<InetSocketAddress> addresses = registry.lookup(ServiceName);

        //2、将连接进行缓存
        for (InetSocketAddress address : addresses) {
            try {
                if (!ZkrpcBootstrap.CHANNEL_CACHE.containsKey(address)) {
                    Channel channel = NettyBootstrapInitializer.getBootstrap().connect(address).sync().channel();
                    ZkrpcBootstrap.CHANNEL_CACHE.put(address, channel);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        //3、任务、定期发送消息
        Thread thread = new Thread(() ->
                new Timer().scheduleAtFixedRate(new MyTimerTask(), 0, 2000)
        ,"zkrpc-HeartbeatDetector-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private static class MyTimerTask extends TimerTask {
        @Override
        public void run(){

            //每次进行心跳检测时将相应的map，清空
            ZkrpcBootstrap.ANSWER_TIME_CHANNEL_CACHE.clear();
            //遍历所有的channel
            Map<InetSocketAddress, Channel> cache = ZkrpcBootstrap.CHANNEL_CACHE;
            for (Map.Entry<InetSocketAddress,Channel> entry :cache.entrySet()){
                Channel channel = entry.getValue();

                long start = System.currentTimeMillis();
                // 构建一个心跳请求
                ZkrpcRequest zkrpcRequest = ZkrpcRequest.builder()
                        .requestId(ZkrpcBootstrap.ID_GENERATOR.getId())
                        .compressType(CompressorFactory.getCompressor(ZkrpcBootstrap.COMPRESS_TYPE).getCode())
                        .requestType(RequestType.HEART_BEAT.getId())
                        .serializeType(SerializerFactory.getSerialzer(ZkrpcBootstrap.SERIALIZE_TYPE).getCode())
                        .timeStamp(start)
                        .build();

                CompletableFuture<Object> completableFuture = new CompletableFuture<>();
                ZkrpcBootstrap.PENDING_REQUEST.put(zkrpcRequest.getRequestId(), completableFuture);
                
                channel.writeAndFlush(zkrpcRequest).addListener((ChannelFutureListener) promise->{
                    if (!promise.isSuccess()) {
                        completableFuture.completeExceptionally(promise.cause());
                    }
                });
                
                //
                Long endTime = 0L;
                try {
                    completableFuture.get();
                    endTime = System.currentTimeMillis();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
                Long time = endTime - start;
                //使用treemap进行缓存
                ZkrpcBootstrap.ANSWER_TIME_CHANNEL_CACHE.put(time,channel);
                log.debug("和【{}】服务器的相应时间是[{}],",entry.getKey(),time);
                //拿到最短相应时间的channel
            }
        }
    }


}
