package com.zkrpc.loadbalancer;
import java.net.InetSocketAddress;
/**
 * 接口
 */
public interface LoadBalancer {
    //根据服务列表找到一个可以用的服务
    /**
     * 根据服务名获取一个可用的服务
     * @param serviceName 服务名称
     * @return 服务地址
     */
    InetSocketAddress selectServiceAddress(String serviceName);
}
