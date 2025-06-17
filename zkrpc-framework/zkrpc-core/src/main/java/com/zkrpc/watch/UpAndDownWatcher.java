package com.zkrpc.watch;

import com.zkrpc.NettyBootstrapInitializer;
import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.discovery.Registry;
import com.zkrpc.loadbalancer.LoadBalancer;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

@Slf4j
public class UpAndDownWatcher implements Watcher {
    @Override
    public void process(WatchedEvent event) {
        //当前的阶段是否发生了变化
        if(event.getType() == Event.EventType.NodeChildrenChanged){
            if(log.isDebugEnabled()){
                log.debug("检测服务【{}】到有节点上/下线，将重新拉取服务列表.....",event.getPath());
            }
            //获取地址
            String serviceName = getServiceName(event.getPath());
            //获取注册中心
            Registry registry = ZkrpcBootstrap.getInstance().getConfiguration().getRegistryConfig().getRegistry();
            //zookeeper发现
            List<InetSocketAddress> addresses = registry.lookup(serviceName);
            //处理新增的节点
            for (InetSocketAddress address : addresses) {
                //新增的节点 会在address 不在CHANNEL_CACHE
                //下线的节点 可能会在CHANNEL_CACHE, 不在address
                if (!ZkrpcBootstrap.CHANNEL_CACHE.containsKey(address)) {
                    //根据地址建立连接，缓存链接
                    Channel channel = null;
                    try {
                        channel = NettyBootstrapInitializer.getBootstrap()
                                .connect(address).sync().channel();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    ZkrpcBootstrap.CHANNEL_CACHE.put(address, channel);
                }
            }

            //处理下线的节点 可能会在缓存里面，但一定不在address里面
            for(Map.Entry<InetSocketAddress,Channel> entry : ZkrpcBootstrap.CHANNEL_CACHE.entrySet()){
                if(!addresses.contains(entry.getKey())){
                    ZkrpcBootstrap.CHANNEL_CACHE.remove(entry.getKey());
                }
            }
            // 获得负载均衡器，进行重新的loadBalance
            LoadBalancer loadBalancer = ZkrpcBootstrap.getInstance().getConfiguration().getLoadBalancer();
            loadBalancer.reLoadBalance(serviceName,addresses);
        }
    }

    private String getServiceName(String path) {
        String[] split = path.split("/");
        return split[split.length - 1];
    }
}
