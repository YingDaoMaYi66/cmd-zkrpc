package com.zkrpc;

import com.zkrpc.discovery.Registry;
import com.zkrpc.exceptions.NetworkException;
import com.zkrpc.proxy.handler.RpcConsumerInvocationHandler;
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
        Class<T>[] classes = new Class[]{interfaceRef};
        InvocationHandler handler = new RpcConsumerInvocationHandler(registry, interfaceRef);
        Object helloProxy = Proxy.newProxyInstance(classLoader, classes, handler);
        return (T) helloProxy;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }
}
