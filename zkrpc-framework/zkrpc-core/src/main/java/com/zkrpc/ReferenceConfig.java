package com.zkrpc;

import com.zkrpc.discovery.Registry;
import com.zkrpc.proxy.handler.RpcConsumerInvocationHandler;
import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;


@Slf4j
public class ReferenceConfig<T> {
    private Class<T> interfaceRef;
    private Registry registry;


    public Class<T> getInterface() {
        return interfaceRef;
    }

    //设置需要代理的接口
    public void setInterface(Class<T> interfaceRef) {
        this.interfaceRef = interfaceRef;
    }




    /**
     * 代理设计模式,生成一个API接口的代理对象
     * @return 代理对象
     */
    public T  get() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
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
