package com.zkrpc.loadbalancer;
import java.net.InetSocketAddress;
/**
 * 服务选择器:从服务列表中使用负载均衡算法选择一个服务
 */
public interface Selector {
    /**
     * 根据服务列表执行一种算法获取一个服务列表
     * @return 具体的服务节点
     */
    InetSocketAddress getNext();

    //todo 服务上下线需要重新去做负载均衡
     void reBalance();
}
