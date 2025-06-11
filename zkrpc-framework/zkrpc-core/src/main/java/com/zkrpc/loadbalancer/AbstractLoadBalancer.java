package com.zkrpc.loadbalancer;
import com.zkrpc.ZkrpcBootstrap;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractLoadBalancer implements LoadBalancer{

    /**
     * one service matching one selector
     */
    private Map<String,Selector> cache = new ConcurrentHashMap<String, Selector>(8);

    @Override
    public InetSocketAddress selectServiceAddress(String serviceName) {

        //1、优先从cache获取一个选择器
        Selector selector = cache.get(serviceName);

        //2、如果没有、就需要为这个service创建一个selector
        if (selector == null) {
            //对于一个负载均衡器内部应该维护一个服务列表作为缓存
            List<InetSocketAddress> serviceList = ZkrpcBootstrap.getInstance().getRegistry().lookup(serviceName);
            //提供一些算法负责选取合适的节点
            selector = getSelector(serviceList);
            //将selector放入缓存当中
            cache.put(serviceName, selector);
        }
        //获取可用节点
        return selector.getNext();
    }

    /**
     * 由子类进行搜索
     * @param serviceList 菔务列表
     * @return 负载均衡算法选择器
     */
    protected abstract Selector getSelector(List<InetSocketAddress> serviceList);
}
