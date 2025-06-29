package com.zkrpc;

public class ServiceConfig<T> {
    // 服务提供方的接口
    private Class<?> interfaceProvider;
    // 服务提供方的实现类
    private Object ref;
    private String group = "default"; // 服务分组，默认为"default"

    public Class<?> getInterface() {
        return interfaceProvider;
    }

    public void setInterface(Class<?> interfaceProvider) {
        this.interfaceProvider = interfaceProvider;
    }

    public Object getRef() {
        return ref;
    }

    public void setRef(Object ref) {
        this.ref = ref;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getGroup() {
        return group;
    }
}
