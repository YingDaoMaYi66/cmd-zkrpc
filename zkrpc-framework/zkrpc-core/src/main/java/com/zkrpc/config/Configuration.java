package com.zkrpc.config;
import com.zkrpc.IdGenerator;
import com.zkrpc.ProtocolConfig;
import com.zkrpc.compress.Compressor;
import com.zkrpc.compress.impl.GzipCompressor;
import com.zkrpc.discovery.RegistryConfig;
import com.zkrpc.loadbalancer.LoadBalancer;
import com.zkrpc.loadbalancer.impl.RoundRobinLoadBalancer;
import com.zkrpc.serialize.Serializer;
import com.zkrpc.serialize.impl.JdkSerializer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局配置类，优先代码配置----->xml配置--->默认配置
 */
@Data
@Slf4j
public class Configuration {

    // 配置信息-->端口号
    private int port = 8094;

    // 配置信息-->应用程序的名字
    private String appName = "default";

    // 配置信息-->注册中心
    private RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");

    // 配置信息-->序列化协议
    private String serializeType = "jdk";


    // 配置信息-->压缩使用的协议
    private String compressType = "gzip";


    // 配置信息-->id发射器
    public IdGenerator idGenerator = new IdGenerator(1L, 2L);

    // 配置信息-->负载均衡策略
    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

    // 读xml，dom4j
    public Configuration() {
        //1、成员变量的默认配置项

        //2、spi机制发现相关配置项、
        SpiResolver spiResolver = new SpiResolver();
        spiResolver.loadFromSpi(this);

        //3、 读取xml获得上边的信息
        XmlResolver xmlResolver = new XmlResolver();
        xmlResolver.loadFromXml(this);

    }
    public static  void main(String[] args) {
        Configuration configuration = new Configuration();

    }

}
