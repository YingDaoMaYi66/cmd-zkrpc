package com.zkrpc.proxy.handler;
import com.zkrpc.NettyBootstrapInitializer;
import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.channelhandler.compress.CompressorFactory;
import com.zkrpc.discovery.Registry;
import com.zkrpc.enumeration.RequestType;
import com.zkrpc.exceptions.DiscoveryException;
import com.zkrpc.exceptions.NetworkException;
import com.zkrpc.serialize.SerializerFactory;
import com.zkrpc.transport.message.RequestPayload;
import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 该类封装了客户端通信的基础步骤，每一个代理对象的远程调用过程都封装在了invoke中
 * 1、发现可用服务
 * 2、建立相关链接
 * 3、发送请求
 * 4、得到结果
 */
@Slf4j
public class RpcConsumerInvocationHandler implements InvocationHandler {
    private final Registry registry;
    private final Class<?> interfaceRef;

    public RpcConsumerInvocationHandler(Registry registry, Class<?> interfaceRef) {
        this.registry = registry;
        this.interfaceRef = interfaceRef;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        /*
         * 1、封装报文，然后将封装好的报文写到channel中
         */
        RequestPayload requestPayload = RequestPayload.builder()
                .interfaceName(interfaceRef.getName())
                .methodName(method.getName())
                .parameterType(method.getParameterTypes())
                .parametersValue(args)
                .returnType(method.getReturnType())
                .build();

        ZkrpcRequest zkrpcRequest = ZkrpcRequest.builder()
                .requestId(ZkrpcBootstrap.getInstance().getConfiguration().getIdGenerator().getId())
                .compressType(CompressorFactory.getCompressor(ZkrpcBootstrap.getInstance().getConfiguration().getCompressType()).getCode())
                .requestType(RequestType.REQUEST.getId())
                .serializeType(SerializerFactory.getSerialzer(ZkrpcBootstrap.getInstance().getConfiguration().getSerializeType()).getCode())
                .timeStamp(new Date().getTime())
                .requestPayload(requestPayload)
                .build();
        //将请求存入本地线程，需要在合适的时候调用remove
        ZkrpcBootstrap.REQUEST_THREAD_LOACL.set(zkrpcRequest);

        //2、发现服务，从注册中心拉取服务列表，并通过客户端负载均衡寻找一个可用的服务
        InetSocketAddress address = ZkrpcBootstrap.getInstance().getConfiguration().getLoadBalancer().selectServiceAddress(interfaceRef.getName());
        if (log.isDebugEnabled()){
            log.debug("服务调用方,返现了服务【{}】的可用主机【{}】",
                    interfaceRef.getName(), address);
        }

        //尝试从全局的缓存中获取一个channel
        Channel channel = getAvaliableChannel(address);
        if(log.isDebugEnabled()){
            log.debug("获取了和【{}】建立的；链接通道，准备发送数据",address);
        }

        //写出报文
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        ZkrpcBootstrap.PENDING_REQUEST.put(zkrpcRequest.getRequestId(), completableFuture);
        //这里直接writeAndFlush写出了一个请求，这个请求的实例就会进入pipeline执行出站的一系列操作
        //我们可以想象的到，第一个出站程序一定是将ZkrpcRequest-->二进制报文
        channel.writeAndFlush(zkrpcRequest).addListener((ChannelFutureListener) promise->{
            if (!promise.isSuccess()) {
                completableFuture.completeExceptionally(promise.cause());
            }
        });
        //清理ThreadLocal
        ZkrpcBootstrap.REQUEST_THREAD_LOACL.remove();

        return completableFuture.get(10, TimeUnit.SECONDS);
    }



    /**
     * 根据地址获取一个可用的通道
     * @param address channel地址
     * @return 返回一个可用通道
     */
    private Channel getAvaliableChannel(InetSocketAddress address) {
        //1、尝试从全局缓存获取一个channel
        Channel channel = ZkrpcBootstrap.CHANNEL_CACHE.get(address);
        if (channel == null) {
            CompletableFuture<Channel> channelFuture = new CompletableFuture<>();
            NettyBootstrapInitializer.getBootstrap().connect(address).addListener(
                    (ChannelFutureListener) promise->{
                        if (promise.isDone()) {
                            if(log.isDebugEnabled()){
                                log.debug("已经和【{}】成功建立了链接",address);
                            }
                            channelFuture.complete(promise.channel());
                        } else if (!promise.isSuccess()) {
                            channelFuture.completeExceptionally(promise.cause());
                        }
                    }
            );
            try {
                channel = channelFuture.get(3, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("获取通道时,发生异常",e);
                throw new DiscoveryException(e);
            }
            //将获取到的channel放入全局缓存中
            ZkrpcBootstrap.CHANNEL_CACHE.put(address, channel);
        }
        if (channel == null){
            log.error("获取或建立与【{}】的通道时发生了异常",address);
            throw new NetworkException("获取通道时发生了异常");
        }
        return channel;
    }

}
