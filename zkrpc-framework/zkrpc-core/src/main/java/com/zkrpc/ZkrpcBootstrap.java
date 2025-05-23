package com.zkrpc;

import com.zkrpc.discovery.Registry;
import com.zkrpc.discovery.RegistryConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.ZooKeeper;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ZkrpcBootstrap {

    //YrpcBootstrap是一个单例类，使用饿汉式单例模式，我们希望每个应用都只有一个实例
    private static final ZkrpcBootstrap ZKRPC_BOOTSTRAP = new ZkrpcBootstrap();
    //维护一个已经发布且暴露的服务列表Key是Interface的全限定名 value ->ServiceConfig
    private static final  Map<String,ServiceConfig<?>> SERVICE_LIST = new ConcurrentHashMap<>(16);
    //定义一些相关的一些基础的配置
    private String appName = "default";
    //维护一个注册中心
    private RegistryConfig registryConfig;
    //维护一个协议的配置
    private ProtocolConfig protocolConfig;
    private final int port = 8088;
    //维护一个zookeeper实例
    private ZooKeeper  zookeeper;
    //注册中心
    private Registry registry;
    //维护一个链接的缓存 如果使用这样的类做key一定要看它有没有重写hashcode和equals 和tostring方法
    public final static Map<InetSocketAddress, Channel> CHANNEL_CACHE = new ConcurrentHashMap<>(16);
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
        SERVICE_LIST.put(service.getInterface().toString(),service);
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
                            socketChannel.pipeline().addLast(new SimpleChannelInboundHandler<>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object msg) throws Exception {
                                    ByteBuf byteBuf = (ByteBuf) msg;
                                    log.info("服务端接收到的消息是ByteBuf->:{}", byteBuf.toString(Charset.defaultCharset()));
                                    //打印结果之后就可以不管了，直接将数据写返回
                                    channelHandlerContext.channel().writeAndFlush(Unpooled.copiedBuffer("hello world--zkrpc".getBytes()));
                                }
                            });
                        }
                    });
            //4、绑定端口
            ChannelFuture channelFuture = serverbootstrap.bind(port).sync();
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
        //债这个方法中我们是否能拿到相关配置项(包括注册中心)
        //配置Reference，将来调用get方法时，方便生成代理对象
        //1、reference需要一个注册中心
        reference.setRegistry(registry);
        return this;
    }
}
