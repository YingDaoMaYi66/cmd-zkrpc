package com.zkrpc;
import com.zkrpc.utils.zookeeper.ZookeeperNode;
import com.zkrpc.utils.zookeeper.ZookeeperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;

import java.util.List;

/**
 * 注册中心的管理页面
 */
@Slf4j
public class Application {

    public static void main(String[] args) {

            //创建一个zookeeper实例
            ZooKeeper zooKeeper = ZookeeperUtil.createZookeeper();
            //定义节点和数据
            String basePath="/zkrpc-metadata";
            String providerPath=basePath+"/providers";
            String consumerPath=basePath+"/consumers";
            ZookeeperNode baseNode = new ZookeeperNode(basePath,null);
            ZookeeperNode providersNode = new ZookeeperNode(providerPath,null);
            ZookeeperNode consumersNode = new ZookeeperNode(consumerPath,null);
            //创建节点
            List.of(baseNode,providersNode,consumersNode).forEach(node -> {
                ZookeeperUtil.createNode(zooKeeper,node,null,CreateMode.PERSISTENT);
        });
            ZookeeperUtil.close(zooKeeper);
    }

}
