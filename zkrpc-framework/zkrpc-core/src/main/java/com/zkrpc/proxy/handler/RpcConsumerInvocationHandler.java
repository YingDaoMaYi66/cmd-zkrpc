package com.zkrpc.proxy.handler;
import com.zkrpc.NettyBootstrapInitializer;
import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.annotation.TryTimes;
import com.zkrpc.compress.CompressorFactory;
import com.zkrpc.discovery.Registry;
import com.zkrpc.enumeration.RequestType;
import com.zkrpc.exceptions.DiscoveryException;
import com.zkrpc.exceptions.NetworkException;
import com.zkrpc.protection.CircuitBreaker;
import com.zkrpc.serialize.SerializerFactory;
import com.zkrpc.transport.message.RequestPayload;
import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
    private String group;

    public RpcConsumerInvocationHandler(Registry registry, Class<?> interfaceRef,String group) {
        this.registry = registry;
        this.interfaceRef = interfaceRef;
        this.group = group;
    }

    /**
     *
     * @param proxy the proxy instance that the method was invoked on
     *
     * @param method the {@code Method} instance corresponding to
     * the interface method invoked on the proxy instance.  The declaring
     * class of the {@code Method} object will be the interface that
     * the method was declared in, which may be a superinterface of the
     * proxy interface that the proxy class inherits the method through.
     *
     * @param args an array of objects containing the values of the
     * arguments passed in the method invocation on the proxy instance,
     * or {@code null} if interface method takes no arguments.
     * Arguments of primitive types are wrapped in instances of the
     * appropriate primitive wrapper class, such as
     * {@code java.lang.Integer} or {@code java.lang.Boolean}.
     *
     * @return 返回值
     * @throws Throwable
     */

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //从接口中获取判断接口是否需要重试
        TryTimes annotation = method.getAnnotation(TryTimes.class);
        //默认0代表不重试
        int trytimes = 0; //默认不重试
        int intervaltime = 0; //默认重试间隔时间
        if (annotation != null) {
           trytimes = annotation.tryTimes();
           intervaltime = annotation.intervalTime();

        }
        while(true) {
            //什么情况下需要重试 1、异常 2、响应有问题 code == 500

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
                //创建请求
                ZkrpcRequest zkrpcRequest = ZkrpcRequest.builder()
                        .requestId(ZkrpcBootstrap.getInstance().getConfiguration().getIdGenerator().getId())
                        .compressType(CompressorFactory.getCompressor(ZkrpcBootstrap.getInstance().getConfiguration().getCompressType()).getCode())
                        .requestType(RequestType.REQUEST.getId())
                        .serializeType(SerializerFactory.getSerialzer(ZkrpcBootstrap.getInstance().getConfiguration().getSerializeType()).getCode())
                        .timeStamp(System.currentTimeMillis())
                        .requestPayload(requestPayload)
                        .build();
                //2、将请求存入本地线程，需要在合适的时候调用remove
                ZkrpcBootstrap.REQUEST_THREAD_LOACL.set(zkrpcRequest);

                //3、发现服务，从注册中心拉取服务列表，并通过客户端负载均衡寻找一个可用的服务
                InetSocketAddress address = ZkrpcBootstrap.getInstance()
                        .getConfiguration().getLoadBalancer().selectServiceAddress(interfaceRef.getName(),group);
                if (log.isDebugEnabled()) {
                    log.debug("服务调用方,返现了服务【{}】的可用主机【{}】",
                            interfaceRef.getName(), address);
                }
                //4、获取当前地址所对应的断路器，如果断路器是打开的则不发送请求，抛出异常
            Map<SocketAddress, CircuitBreaker> everyIpCircuitBreaker = ZkrpcBootstrap.getInstance()
                    .getConfiguration().getEveryIpCircuitBreaker();
            CircuitBreaker circuitBreaker = everyIpCircuitBreaker.get(address);
            if (circuitBreaker == null) {
                circuitBreaker = new CircuitBreaker(10,0.5f);
                everyIpCircuitBreaker.put(address, circuitBreaker);
            }
            try {
                //如果断路器是打开的
                if (zkrpcRequest.getRequestType() != RequestType.HEART_BEAT.getId() && circuitBreaker.isBreak()){
                    //定期打开
                    Timer timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            ZkrpcBootstrap.getInstance()
                                    .getConfiguration().getEveryIpCircuitBreaker()
                                    .get(address).reset();
                        }
                    },5000);
                    throw new RuntimeException("当前断路器已经开启，无法发送请求");
                }

                // 5、尝试从全局的缓存中获取一个channel
                Channel channel = getAvaliableChannel(address);
                if (log.isDebugEnabled()) {
                    log.debug("获取了和【{}】建立的；链接通道，准备发送数据", address);
                }

                // 6、写出报文
                CompletableFuture<Object> completableFuture = new CompletableFuture<>();
                ZkrpcBootstrap.PENDING_REQUEST.put(zkrpcRequest.getRequestId(), completableFuture);
                //这里直接writeAndFlush写出了一个请求，这个请求的实例就会进入pipeline执行出站的一系列操作
                //我们可以想象的到，第一个出站程序一定是将ZkrpcRequest-->二进制报文
                channel.writeAndFlush(zkrpcRequest).addListener((ChannelFutureListener) promise -> {
                    if (!promise.isSuccess()) {
                        completableFuture.completeExceptionally(promise.cause());
                    }
                });
                // 7、清理ThreadLocal
                ZkrpcBootstrap.REQUEST_THREAD_LOACL.remove();
                // 8、获得响应结果
                Object result = completableFuture.get(10, TimeUnit.SECONDS);
                //如果成功拿到一个结果，记录成功的请求
                circuitBreaker.recordRequest();
                return result;
            } catch (Exception e) {
                trytimes--;
                //记录错误的次数
                circuitBreaker.recordErrorRequest();
                try {
                    Thread.sleep(intervaltime);
                }catch (InterruptedException ex) {
                    log.error("线程休眠时发生异常",ex);
                }
                if (trytimes <0) {
                    log.error("对方法【{}】进行远程调用时发生异常,重试【{}】，依然不可用",
                            method.getName(),trytimes, e);
                    break;
                }
                log.error("在进行第【{}】次重试时发生异常",3-trytimes,e);
            }
        }
        throw new RuntimeException("执行远程方法"+method.getName()+"调用失败");
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
