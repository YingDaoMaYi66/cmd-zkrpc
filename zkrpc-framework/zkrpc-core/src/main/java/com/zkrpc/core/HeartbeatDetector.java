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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 心跳探测的核心目的是什么？探活，感知哪些服务器的链接状态是正常的，哪些是不正常的。
 */
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
                //定义一个重试的次数
                int tryTimes = 3;
                while (tryTimes>0) {
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

                    channel.writeAndFlush(zkrpcRequest).addListener((ChannelFutureListener) promise -> {
                        if (!promise.isSuccess()) {
                            completableFuture.completeExceptionally(promise.cause());
                        }
                    });

                    //
                    Long endTime = 0L;
                    try {
                        //这个是阻塞方法，get方法如果获取不到结果，就会一直阻塞
                        //我们想不一直阻塞可以添加参数
                        completableFuture.get(1, TimeUnit.SECONDS);
                        endTime = System.currentTimeMillis();
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        //一旦发生问题,需要进行重试
                        tryTimes --;
                        log.error("和地址为：【{}】的主机链接发生异常，正在进行第【{}】次重试。。。。。。。",
                                channel.remoteAddress(), 3-tryTimes);
                        //将重试的机会用尽了
                        if (tryTimes == 0){
                            //将这个失效的地址期移除我们的服务列表
                            ZkrpcBootstrap.CHANNEL_CACHE.remove(entry.getKey());
                        }
                        //尝试等待一段时间后重试 这个随机值是为了防止出现重试过载
                        try {
                            Thread.sleep(10*(new Random().nextInt(5)));
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                        continue;
                    }
                    Long time = endTime - start;
                    //使用treemap进行缓存
                    ZkrpcBootstrap.ANSWER_TIME_CHANNEL_CACHE.put(time, channel);
                    log.debug("和【{}】服务器的相应时间是[{}],", entry.getKey(), time);
                    break;
                }

            }

            log.info("----------------------响应时间的treemap--------------------------");
            for (Map.Entry<Long,Channel> entry :ZkrpcBootstrap.ANSWER_TIME_CHANNEL_CACHE.entrySet()){
                if (log.isDebugEnabled()){
                    log.debug("{}---->channelId:{}",entry.getKey(),entry.getValue().id());
                }
            }
        }
    }


}
