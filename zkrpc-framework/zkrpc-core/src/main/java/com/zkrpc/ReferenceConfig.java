package com.zkrpc;

import com.zkrpc.discovery.Registry;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;

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
    public T get() {
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
                //2、使用netty连接服务，发送调用的服务的名字+方法名字+参数列表，得到结果

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
