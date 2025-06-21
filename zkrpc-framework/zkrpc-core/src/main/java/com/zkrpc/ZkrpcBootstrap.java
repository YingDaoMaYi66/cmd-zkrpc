package com.zkrpc;

import com.zkrpc.annotation.ZkrpcApi;
import com.zkrpc.channelhandler.handler.MethodCallHandler;
import com.zkrpc.channelhandler.handler.ZkrpcRequestDecoder;
import com.zkrpc.channelhandler.handler.ZkrpcResponseEncoder;
import com.zkrpc.config.Configuration;
import com.zkrpc.core.HeartbeatDetector;
import com.zkrpc.discovery.RegistryConfig;
import com.zkrpc.loadbalancer.LoadBalancer;
import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class ZkrpcBootstrap {

    //YrpcBootstrap是一个单例类，使用饿汉式单例模式，我们希望每个应用都只有一个实例
    private static final ZkrpcBootstrap ZKRPC_BOOTSTRAP = new ZkrpcBootstrap();

    //全局的配置中心
    private Configuration configuration;

    //保存request对象，可以到当前线程中随时获取
    public static final ThreadLocal<ZkrpcRequest> REQUEST_THREAD_LOACL = new ThreadLocal<>();

    //维护一个已经发布且暴露的服务列表Key是Interface的全限定名 value ->ServiceConfig
    public static final  Map<String,ServiceConfig<?>> SERVICE_LIST = new ConcurrentHashMap<>(16);

    //负载均衡
    public static  LoadBalancer LOAD_BALANCER;

    //维护一个链接的缓存 如果使用这样的类做key一定要看它有没有重写hashcode和equals 和tostring方法
    public final static Map<InetSocketAddress, Channel> CHANNEL_CACHE = new ConcurrentHashMap<>(16);

    //用来缓存全局的心跳机制带来的相应时间
    public final static TreeMap<Long, Channel> ANSWER_TIME_CHANNEL_CACHE = new TreeMap<>();

    //定义全局对外挂起的 completeableFuture
    public final static Map<Long, CompletableFuture<Object>> PENDING_REQUEST = new ConcurrentHashMap<>(128);

    private ZkrpcBootstrap(){
        //构造启动引导程序，时需要做一些什么初始化的事
        configuration = new Configuration();
    }

    public static ZkrpcBootstrap getInstance() {
        return ZKRPC_BOOTSTRAP;
    }

    /**
     * 用来定义当前应用的名字
     * @param appName 当前应用的名字
     * @return 链式编程
     */
    public ZkrpcBootstrap application(String appName) {
        configuration.setAppName(appName);
        return this;
    }

    /**
     * 用来配置一个注册中心
     * @param registryConfig 注册中心
     * @return 当前实例
     */
    public ZkrpcBootstrap registry(RegistryConfig registryConfig) {
        configuration.setRegistryConfig(registryConfig);
        return this;
    }


    /**
     * 用来配置一个负载均衡策略
     * @param loadBalancer 负载均衡策略
     * @return 当前实例
     */
    public ZkrpcBootstrap loadBalancer(LoadBalancer loadBalancer) {
        configuration.setLoadBalancer(loadBalancer);
        return this;
    }


    /*
     * -----------------------------------服务提供方的相关api-----------------------------------------------------------------------------------------
     */

    /**
     * 发布服务 将接口和匹配的实现注册到服务中心
     * @param service 独立的封装好的需要发布的服务
     * @return this当前实例
     */
    public ZkrpcBootstrap publish(ServiceConfig<?> service) {
        //我们使用了注册中心的概念，使用注册中心的一个实现完成注册
        configuration.getRegistryConfig().getRegistry().register(service);
        //1、当服务调用方，通过接口，方法名，具体的方法参数列表发起调用，提供方怎么知道使用哪一个实现
        //(1)new一个 （2）spring beanFactory.getBean(Class) 3、自己维护一个映射关系
        SERVICE_LIST.put(service.getInterface().getName(), service);
        return this;
    }

    public ZkrpcBootstrap publish(List<ServiceConfig<?>> services) {
        for(ServiceConfig<?> service : services) {
            this.publish(service);
        }
        return this;
    }


    /**
     * 启动netty服务
     */
    public void start() {
        //创建eventLoop,老板只负责处理请求，之后会将请求分发到worker
        EventLoopGroup boss = new NioEventLoopGroup(2);
        EventLoopGroup worker = new NioEventLoopGroup(10);
        try {
            ServerBootstrap serverbootstrap = new ServerBootstrap();
            serverbootstrap = serverbootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            //是核心，我们要添加很多入站出站的handler
                            socketChannel.pipeline()
                                    //日志处理器，打印请求和响应的日志
                                    .addLast(new LoggingHandler())
                                    //自定义的请求编码器
                                    .addLast(new ZkrpcRequestDecoder())
                                    //根据请求进行方法调用
                                    .addLast(new MethodCallHandler())
                                    //自定义的响应编码器
                                    .addLast(new ZkrpcResponseEncoder());
                        }
                    });
            //4、绑定端口
            ChannelFuture channelFuture = serverbootstrap.bind(configuration.getPort()).sync();

            channelFuture.channel().closeFuture().sync();
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            try {
                boss.shutdownGracefully().sync();
                worker.shutdownGracefully().sync();
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }


    /**
     * -----------------------------------服务调用方的相关api-----------------------------------------------------------------------------------------
     */
    public ZkrpcBootstrap reference(ReferenceConfig<?> reference) {
        //开启一个对这个服务的心跳检测
        HeartbeatDetector.detectHeartbeat(reference.getInterface().getName());

        reference.setRegistry(configuration.getRegistryConfig().getRegistry());
        return this;
    }

    /**
     * 配置序列化的方式
     * @param serializeType 序列化的方式
     */
    public ZkrpcBootstrap serialize(String serializeType) {
        configuration.setSerializeType(serializeType);
        if (log.isDebugEnabled()) {
            log.debug("我们配置了使用的序列化的方式为【{}】.",serializeType);
        }
        return this;
    }

    /**
     * 配置解压缩的方式
     * @param compressType 压缩类型
     * @return ZkrpcBootstrap
     */
    public ZkrpcBootstrap compress(String compressType) {
        configuration.setCompressType(compressType);
        if (log.isDebugEnabled()) {
            log.debug("我们配置了使用的压缩算法的方式为【{}】.",compressType);
        }
        return this;
    }

    /**
     * 扫描指定包下的所有类，并将符合条件的类发布为服务
     * @param packageName 需要扫描的包名
     * @return ZkrpcBootstrap
     */
    public ZkrpcBootstrap scan(String packageName) {
        //1、需要通过packageName扫描包下面的所有的类的权限定名称
        List<String> classNames = getALlClassNames(packageName);
        //2、通过反射获取他的接口，构建具体实现
        List<Class<?>> classes = classNames.stream()
                .map(className -> {
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }).filter(clazz -> clazz.getAnnotation(ZkrpcApi.class) != null)
                .collect(Collectors.toList());

        for (Class<?> clazz : classes) {
            //获取他的接口
            Class<?>[] interfaces = clazz.getInterfaces();
            Object instance = null;
            try {
                instance = clazz.getConstructor().newInstance();
            } catch (InstantiationException | NoSuchMethodException | InvocationTargetException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            for (Class<?> anInterface : interfaces) {
                ServiceConfig<Object> serviceConfig = new ServiceConfig<>();
                serviceConfig.setInterface(anInterface);
                serviceConfig.setRef(instance);
                if (log.isDebugEnabled()) {
                    log.debug("----->已经通过包扫描，将服务【{}】发布",anInterface);
                }
                publish(serviceConfig);
            }
            //3、发布


        }

        return this;
    }

    private List<String> getALlClassNames(String packageName) {
        //1、通过packageName获得绝对路径
        String basePath = packageName.replaceAll("\\.", "/");
        URL url = ClassLoader.getSystemClassLoader().getResource(basePath);
        if(url == null){
            throw new RuntimeException("包扫描时,发现路径不存在" + basePath);
        }
        String absolutePath = url.getPath();
        List<String> classNames = new ArrayList<>();
        classNames = recursionFile(absolutePath,classNames,basePath);
        return classNames;
    }

    private List<String> recursionFile(String absolutePath, List<String> classNames,String basePath) {
        //获取文件
        File file = new File(absolutePath);
        if (file.isDirectory()){
            //找到文件夹的所有文件
            File[] children = file.listFiles(pathname -> pathname.isDirectory() || pathname.getPath().contains(".class"));
            if (children == null || children.length == 0) {
                return classNames; // 如果没有子文件，直接返回
            }
            for(File child : children){
                if (child.isDirectory()){
                    recursionFile(child.getAbsolutePath(),classNames,basePath);
                }else{
                    //文件-->类的权限限定名称
                    String className = getClassNameByAbsolutePath(child.getAbsolutePath(),basePath);
                    classNames.add(className);
                }
            }
        }else {
            String className = getClassNameByAbsolutePath(absolutePath,basePath);
            classNames.add(className);
        }
        return classNames;
    }

    private String getClassNameByAbsolutePath(String absolutePath,String basePath) {
        //将获取到的绝对路径转换为类的全限定名称
        String fileName = absolutePath.
                substring(absolutePath.lastIndexOf(basePath.replaceAll("/","\\\\")))
                .replaceAll("\\\\",".");
        fileName = fileName.substring(0, fileName.indexOf(".class"));
        return fileName;
    }

    public static void main(String[] args) {
        List<String> aLlClassNames = ZkrpcBootstrap.getInstance().getALlClassNames("com.zkrpc");
        System.out.println(aLlClassNames);
    }

    public Configuration getConfiguration() {
        return configuration;
    }
}
