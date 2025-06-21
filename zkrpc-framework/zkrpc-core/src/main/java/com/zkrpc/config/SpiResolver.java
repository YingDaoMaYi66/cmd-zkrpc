package com.zkrpc.config;

import com.zkrpc.compress.Compressor;
import com.zkrpc.compress.CompressorFactory;
import com.zkrpc.loadbalancer.LoadBalancer;
import com.zkrpc.serialize.Serializer;
import com.zkrpc.serialize.SerializerFactory;
import com.zkrpc.spi.SpiHandler;

import java.util.List;

/**
 * spi解析器通过这个解析器对configu
 */
public class SpiResolver {
    /**
     * 通过spi的方式加载配置项
     * @param configuration 配置上下文
     */
    public void loadFromSpi(Configuration configuration) {
        //我们的spi的文件中配置了很多实现（）
        List<ObjectWrapper<LoadBalancer>> loadBalancerWrappers = SpiHandler.getList(LoadBalancer.class);
        //将负载均衡放入
        if (loadBalancerWrappers != null && loadBalancerWrappers.size() > 0) {
            configuration.setLoadBalancer(loadBalancerWrappers.get(0).getImpl());
        }

        //将压缩器放入工厂
        List<ObjectWrapper<Compressor>> objectWrappers = SpiHandler.getList(Compressor.class);
        if (objectWrappers != null) {
            //他的语法是对objectWrapper每一个元素调用CompressorFactory的addCompressor方法
           objectWrappers.forEach(CompressorFactory::addCompressor);
        }

        //将序列化器放入工厂
        List<ObjectWrapper<Serializer>> serializerobjectWrappers = SpiHandler.getList(Serializer.class);
        if (serializerobjectWrappers != null) {
            serializerobjectWrappers.forEach(SerializerFactory::addSerializer);
        }

    }
}
