package com.zkrpc;

import com.zkrpc.annotation.ZkrpcApi;
import com.zkrpc.channelhandler.handler.MethodCallHandler;
import com.zkrpc.channelhandler.handler.ZkrpcRequestDecoder;
import com.zkrpc.channelhandler.handler.ZkrpcResponseEncoder;
import com.zkrpc.core.HeartbeatDetector;
import com.zkrpc.discovery.Registry;
import com.zkrpc.discovery.RegistryConfig;
import com.zkrpc.loadbalancer.LoadBalancer;
import com.zkrpc.loadbalancer.impl.ConsistentHashBalancer;
import com.zkrpc.loadbalancer.impl.MinimumResponseTimeLoadBalancer;
import com.zkrpc.loadbalancer.impl.RoundRobinLoadBalancer;
import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.ZooKeeper;

import java.io.File;
import java.io.FileFilter;
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
    //使用ThreadLocal创建
    public static final ThreadLocal<ZkrpcRequest> REQUEST_THREAD_LOACL = new ThreadLocal<>();
    public static final int PORT = 8093;
    //YrpcBootstrap是一个单例类，使用饿汉式单例模式，我们希望每个应用都只有一个实例
    private static final ZkrpcBootstrap ZKRPC_BOOTSTRAP = new ZkrpcBootstrap();
    //维护一个已经发布且暴露的服务列表Key是Interface的全限定名 value ->ServiceConfig
    public static final  Map<String,ServiceConfig<?>> SERVICE_LIST = new ConcurrentHashMap<>(16);
    //定义一些相关的一些基础的配置
    private String appName = "default";
    //维护一个注册中心
    private RegistryConfig registryConfig;
    //维护一个协议的配置
    private ProtocolConfig protocolConfig;
    //todo 雪花算法的配置参数是要在配置文件进行读取的
    public static final IdGenerator ID_GENERATOR = new IdGenerator(1L,2L);
    //序列化方式默认配置项
    public static String SERIALIZE_TYPE = "jdk";
    //压缩方式
    public static  String COMPRESS_TYPE = "gzip";
    //维护一个zookeeper实例
    private ZooKeeper  zookeeper;
    //注册中心
    private Registry registry;
    //负载均衡
    public static  LoadBalancer LOAD_BALANCER;
    //维护一个链接的缓存 如果使用这样的类做key一定要看它有没有重写hashcode和equals 和tostring方法
    public final static Map<InetSocketAddress, Channel> CHANNEL_CACHE = new ConcurrentHashMap<>(16);
    //用来缓存全局的心跳机制带来的相应时间
    public final static TreeMap<Long, Channel> ANSWER_TIME_CHANNEL_CACHE = new TreeMap<>();
    //定义全局对外挂起的 completeableFuture
    public final static Map<Long, CompletableFuture<Object>> PENDING_REQUEST = new ConcurrentHashMap<>(128);

    private ZkrpcBootstrap(){

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
        this.appName = appName;
        return this;
    }

    /**
     * 用来配置一个注册中心
     * @param registryConfig 注册中心
     * @return 当前实例
     */
    public ZkrpcBootstrap registry(RegistryConfig registryConfig) {
        //这里维护一个zookeeper实例，但是这样写，会将zookeeper和当前工程耦合
        //尝试使用registryConfig获取一个注册中心，有点工厂设计模式的意思了
        this.registry = registryConfig.getRegistry();
        //todo 负载均衡选择
        ZkrpcBootstrap.LOAD_BALANCER = new RoundRobinLoadBalancer();
        return this;
    }

    /**
     * 配置当前暴露的服务使用的协议
     * @param protocalConfig 协议的封装
     * @return this当前实例
     */
    public ZkrpcBootstrap protocol(ProtocolConfig protocalConfig) {
        this.protocolConfig = protocalConfig;
        if(log.isDebugEnabled()){
            log.debug("当前工程使用了:{}协议进行序列化",protocalConfig.toString());
        }
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
        registry.register(service);
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
            ChannelFuture channelFuture = serverbootstrap.bind(PORT).sync();

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

        reference.setRegistry(registry);
        return this;
    }

    /**
     * 配置序列化的方式
     * @param serializeType 序列化的方式
     */
    public ZkrpcBootstrap serialize(String serializeType) {
        SERIALIZE_TYPE = serializeType;
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
        COMPRESS_TYPE = compressType;
        if (log.isDebugEnabled()) {
            log.debug("我们配置了使用的压缩算法的方式为【{}】.",compressType);
        }
        return this;
    }

    public  Registry getRegistry(){
        return registry;
    }

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
}
