package com.zkrpc;

import com.zkrpc.discovery.Registry;
import com.zkrpc.exceptions.NetworkException;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ReferenceConfig<T> {
    private Class<T> interfaceRef;
    private Registry registry;


    public Class<T> getInterface() {
        return interfaceRef;
    }
    public void setInterface(Class<T> interfaceRef) {
        this.interfaceRef = interfaceRef;
    }




    /**
     * 代理设计模式,生成一个API接口的代理对象
     * @return 代理对象
     */
    public T  get() {
        //此处一定是使用动态代理完成了一些操作
        //获取当前线程的上下文类加载器，因为动态代理的类需要被加载到jvm当中，类加载器负责这个任务
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //定义代理接口，指定代理类需要实现的接口数组
        //重要限制，动态代理只能为接口创建代理，不能为类创建代理
        Class[] classes = new Class[]{interfaceRef};
        //使用动态代理生成对象
        Object helloProxy = Proxy.newProxyInstance(classLoader, classes, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                //服务发现！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
                //我们调用saHi方法，事实上会走进这个代码段中
                //我们已经知道method args
                log.info("method-->{}", method.getName());
                log.info("args-->{}", args);
                //1、发现服务，从注册中心，寻找一个可用的服务
                // 传入服务的名字,返回一个ip+端口 InetSocketAddress里面封装了ip和端口

                InetSocketAddress address = registry.lookup(interfaceRef.getName());
                if (log.isDebugEnabled()){
                    log.debug("服务调用方,返现了服务【{}】的可用主机【{}】",
                            interfaceRef.getName(), address);
                }


                //2尝试从全局的缓存中获取一个channel！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
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
                    // 在 Netty 中，connect() 方法返回一个 ChannelFuture，用于跟踪连接操作的状态。
                    // 在 addListener 方法中，promise 是回调函数的参数，Netty 会将 connect()
                    // 返回的 ChannelFuture 作为参数传递给回调函数。因此，promise 代表了 connect() 方法的异步操作结果。
                    NettyBootstrapInitializer.getBootstrap().connect(address).addListener(
                            (ChannelFutureListener)promise->{
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
                    //阻塞获取channel
                    channel = channelFuture.get(3, TimeUnit.SECONDS);
                    //缓存channel
                    YrpcBootstrap.CHANNEL_CACHE.put(address, channel);
                }
                if (channel == null){
                    log.error("获取或建立与【{}】的通道时发生了异常",address);
                    throw new NetworkException("获取通道时发生了异常");
                }

                /*
                 * 封装报文，然后将封装好的报文写到channel中
                 */

                CompletableFuture<Object> completableFuture = new CompletableFuture<>();
                //当前的promise将来返回的结果是什么？是writeAndFlush的返回结果
                //一旦数据被写出去，这个promise也就结束了
                //但是我们想要是什么？服务端给我们的返回值，所以这里处理completableFuture是有问题的
                //isDone:操作是否结束
                //isSuccess:操作是否成功(包含了操作是否结束
                // todo 需要将completableFuture 暴露出去

                channel.writeAndFlush(Unpooled.copiedBuffer("Hello".getBytes())).addListener((ChannelFutureListener) promise->{
//                    if (promise.isDone()){
//                        completableFuture.complete(promise.getNow());
//                    }
                 if (promise.isSuccess()) {
                        completableFuture.completeExceptionally(promise.cause());
                    }
                });
                //需要学习channelFuture的使用

//                Object o = completableFuture.get(3, TimeUnit.SECONDS);

                return null;
            }
        });

        return (T) helloProxy;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }
}
