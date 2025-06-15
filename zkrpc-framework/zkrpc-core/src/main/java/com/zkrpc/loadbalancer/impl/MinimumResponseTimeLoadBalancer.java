package com.zkrpc.loadbalancer.impl;

import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.exceptions.LoadBalancerException;
import com.zkrpc.loadbalancer.AbstractLoadBalancer;
import com.zkrpc.loadbalancer.Selector;
import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最短响应时间的负载均衡策略
 */
@Slf4j
public class MinimumResponseTimeLoadBalancer extends AbstractLoadBalancer {

    @Override
    protected Selector getSelector(List<InetSocketAddress> serviceList) {
        return new MinimumResponseTimeSelector(serviceList);
    }

    private static class MinimumResponseTimeSelector implements Selector{
        public MinimumResponseTimeSelector(List<InetSocketAddress> serviceList) {

        }

        @Override
        public InetSocketAddress getNext() {
            Map.Entry<Long, Channel> entry = ZkrpcBootstrap.ANSWER_TIME_CHANNEL_CACHE.firstEntry();
            if (entry != null){
                if (log.isDebugEnabled()){
                    log.debug("选取了响应时间为【{}】，ms的服务节点.",entry.getKey());
                }
                return (InetSocketAddress) entry.getValue().remoteAddress();
            }

            //如果在心跳检测线程启动之前就，就需要远程调用，直接从缓存中获取一个可用的就行
            Channel channel =(Channel) ZkrpcBootstrap.CHANNEL_CACHE.values().toArray()[0];

            return (InetSocketAddress)channel.remoteAddress();
        }


    }
}
