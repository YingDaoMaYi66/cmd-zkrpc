package com.zkrpc.discovery;

import com.zkrpc.Constant;
import com.zkrpc.discovery.impl.NacosRegistry;
import com.zkrpc.discovery.impl.ZookeeperRegistry;
import com.zkrpc.exceptions.DiscoveryException;

public class RegistryConfig {
    //定义连接的url zookeeper://localhost:2181 redis://localhost:3306
    private final String connectString;

    public RegistryConfig(String connectString) {
        this.connectString = connectString;
    }

    /**
     * 可以使用简单工厂来完成
     * @return 注册中心
     */
    public Registry getRegistry() {
        //1、获取注册中心的类型
        String registryType = getRegistryType(connectString,true).toLowerCase().trim();
        if (registryType.equals("zookeeper")) {
            String host = getRegistryType(connectString,false);
            return new ZookeeperRegistry(host, Constant.TIME_OUT);
        } else if (registryType.equals("nacos")) {
            String host = getRegistryType(connectString,false);
            return new NacosRegistry(host, Constant.TIME_OUT);
        }
        throw new DiscoveryException("未发现合适的注册中心");
    }

    /**
     * 这个是判断注册中心的类型
     * @param connectString 传入注册中心的url
     * @param ifType 当为true时，返回注册中心的类型，当为false时，返回注册中心的host
     * @return 注册中心的类型或者host 为String类型
     */
    private String getRegistryType(String connectString,boolean ifType) {
        String[] typeAndHost = connectString.split("://");
        if(typeAndHost.length != 2) {
            throw new RuntimeException("给定的注册中心链接url不合法");
        }
        //根据连接的url来判断使用的注册中心
        if(ifType) {
            return typeAndHost[0];
        } else {
            return typeAndHost[1];
        }

    }
}
