package com.zkrpc.discovery.impl;

import com.zkrpc.Constant;
import com.zkrpc.ServiceConfig;
import com.zkrpc.discovery.AbstractRegistry;
import com.zkrpc.exceptions.NetworkException;
import com.zkrpc.utils.NetUtils;
import com.zkrpc.utils.zookeeper.ZookeeperNode;
import com.zkrpc.utils.zookeeper.ZookeeperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;

import java.net.InetSocketAddress;
import java.util.List;

@Slf4j
public class NacosRegistry extends AbstractRegistry {
    //维护一个ZooKeeper实例
    private ZooKeeper zooKeeper;
    //默认构造方法
    public NacosRegistry() {
        this.zooKeeper = new ZookeeperUtil().createZookeeper();
    }
    //构造方法
    public NacosRegistry(String connectString, int timeout) {
        this.zooKeeper = new ZookeeperUtil().createZookeeper(connectString, timeout);
    }

    @Override
    public void register(ServiceConfig<?> service) {
        //服务名称的节点
        String parentNode = Constant.BASE_PROVIDERS_PATH+"/"+service.getInterface().getName();
        //这个节点应该是一个持久节点
        if(!ZookeeperUtil.exists(zooKeeper,parentNode,null)){
            ZookeeperNode zookeeperNode = new ZookeeperNode(parentNode,null);
            ZookeeperUtil.createNode(zooKeeper,zookeeperNode,null, CreateMode.PERSISTENT);
        }
        //创建本机的临时节点，ip:port
        //服务提供方的端口一般自己设定，我们还需要一个获取ip的方法
        //ip我们通常是需要一个局域网ip，不是127.0.0.1，也不是ipv6
        //todo后续处理端口问题
        String node = parentNode + "/"+ NetUtils.getIp()+":"+8088;
        if(!ZookeeperUtil.exists(zooKeeper,node,null)){
            ZookeeperNode zookeeperNode = new ZookeeperNode(node,null);
            ZookeeperUtil.createNode(zooKeeper,zookeeperNode,null, CreateMode.EPHEMERAL);
        }

        if(log.isDebugEnabled()){
            log.debug("服务{},已经被注册",service.getInterface().getName());
        }
    }

    @Override
    public InetSocketAddress lookup(String name) {
        //1、找到服务对应的节点
        String serviceNode = Constant.BASE_PROVIDERS_PATH+"/"+name;
        //2、从zk中获取他的子节点 192.168.12.123:2151
        List<String> children = ZookeeperUtil.getChildren(zooKeeper, serviceNode, null);
        //3、获取所有的可用的服务列表
        List<InetSocketAddress> inetSocketAddresses = children.stream().map(ipString -> {
            String[] ipAndPort = ipString.split(":");
            String ip = ipAndPort[0];
            int port = Integer.parseInt(ipAndPort[1]);
            return new InetSocketAddress(ip, port);
        }).toList();
        if (inetSocketAddresses.isEmpty()) {
            throw new NetworkException();
        }
        return inetSocketAddresses.get(0);
    }
}
