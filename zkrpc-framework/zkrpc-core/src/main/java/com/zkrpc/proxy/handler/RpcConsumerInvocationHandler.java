package com.zkrpc.proxy.handler;

import com.zkrpc.NettyBootstrapInitializer;
import com.zkrpc.YrpcBootstrap;
import com.zkrpc.discovery.Registry;
import com.zkrpc.exceptions.DiscoveryException;
import com.zkrpc.exceptions.NetworkException;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
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
    //此处需要一个注册中心，和一个接口
    private final Registry registry;
    private final Class<?> interfaceRef;

    public RpcConsumerInvocationHandler(Registry registry, Class<?> interfaceRef) {
        this.registry = registry;
        this.interfaceRef = interfaceRef;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //1、发现服务，从注册中心，寻找一个可用的服务
        // 传入服务的名字,返回一个ip+端口 InetSocketAddress里面封装了ip和端口
        InetSocketAddress address = registry.lookup(interfaceRef.getName());
        if (log.isDebugEnabled()){
            log.debug("服务调用方,返现了服务【{}】的可用主机【{}】",
                    interfaceRef.getName(), address);
        }

        //2尝试从全局的缓存中获取一个channel！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
        Channel channel = getAvaliableChannel(address);
        if(log.isDebugEnabled()){
            log.debug("获取了和【{}】建立的；链接通道，准备发送数据",address);
        }


        /*
         * 3、封装报文，然后将封装好的报文写到channel中
         */
        //4、写出报文
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        YrpcBootstrap.PENDING_REQUEST.put(1L, completableFuture);

        channel.writeAndFlush(Unpooled.copiedBuffer("Hello".getBytes())).addListener((ChannelFutureListener) promise->{
            if (promise.isSuccess()) {
                completableFuture.completeExceptionally(promise.cause());
            }
        });

        return completableFuture.get(10, TimeUnit.SECONDS);
    }

    /**
     * 根据地址获取一个可用的通道
     * @param address channel地址
     * @return 返回一个可用通道
     */
    private Channel getAvaliableChannel(InetSocketAddress address) {
        //1、尝试从全局缓存获取一个channel
        Channel channel = YrpcBootstrap.CHANNEL_CACHE.get(address);
        if (channel == null) {
            //await方法会阻塞，会等待拦截成功再返回，netty还提供了异步处理的逻辑
            //sync和await都是阻塞当前线程，获取返回值，(链接的过程是异步的，发生发送数据的过程是异步的)
            //如果发生了异常，sync会主动在主线程抛出异常.await不会，异常在子线程中处理需要使用future的addListener方法去处理
//                    channel = NettyBootstrapInitializer.getBootstrap()
//                            .connect(address).await().channel();
            CompletableFuture<Channel> channelFuture = new CompletableFuture<>();

            //addListener是netty中用于异步操作的回调机制，它允许你在异步操作完成后执行特定的逻辑，而无需阻塞当前线程
            //在这个代码中addListener被用于监听connect方法的结果，具体作用如下
            //1、异步监听：addListener 会在连接操作完成（成功或失败）时被触发。
            //2、回调处理：通过传入的 ChannelFutureListener，可以在连接成功时获取 Channel，或者在失败时处理异常。
            //3、非阻塞：避免使用 sync 或 await 阻塞线程，提升性能。


            //connect方法是异步的，它会立即返回一个 ChannelFuture 对象，而不是阻塞等待连接完成。
            //addListener 也是异步的，它的回调会在连接完成后执行
            //但代码又用get方法阻塞了当前线程，等待连接完成。

            //promise 是 ChannelFuture 的一个实例，表示 connect() 方法返回的异步操作结果。
            //说人话！！！！connect就是链式编程的终点,返回的不再是一个bootstrap，而是一个channelfuture！！！！！！！！！！
            //
            // 在 Netty 中，connect() 方法返回一个 ChannelFuture，用于跟踪连接操作的状态。
            // 在 addListener 方法中，promise 是回调函数的参数，Netty 会将 connect()
            // 返回的 ChannelFuture 作为参数传递给回调函数。因此，promise 代表了 connect() 方法的异步操作结果。
            NettyBootstrapInitializer.getBootstrap().connect(address).addListener(
                    (ChannelFutureListener) promise->{
                        if (promise.isDone()) {
                            if(log.isDebugEnabled()){
                                log.debug("已经和【{}】成功建立了链接",address);
                            }
                            channelFuture.complete(promise.channel());
                        } else if (!promise.isSuccess()) {
                            channelFuture.completeExceptionally(promise.cause());
                            //这行代码的作用是将异步操作的结果传递给 CompletableFuture 对象,
                            // promise.cause() 是获取连接失败的异常信息,进行异常收集，然后抛出在调用get的主线程
                        }
                    });
            try {
                channel = channelFuture.get(3, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("获取通道时,发生异常",e);
                throw new DiscoveryException(e);
            }
            //将获取到的channel放入全局缓存中
            YrpcBootstrap.CHANNEL_CACHE.put(address, channel);
        }
        if (channel == null){
            log.error("获取或建立与【{}】的通道时发生了异常",address);
            throw new NetworkException("获取通道时发生了异常");
        }
        return channel;
    }

}
