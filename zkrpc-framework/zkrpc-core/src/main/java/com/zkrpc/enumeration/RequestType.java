package com.zkrpc.enumeration;

/**
 * 用来标记请求类型
 */
public enum RequestType {
    REQUEST((byte)1,"普通请求"),HEART_BEAT((byte)2,"心跳请求");
    private byte id;
    private String type;

    RequestType(byte id, String type) {
        this.id = id;
        this.type = type;
    }

    public byte getId() {
        return id;
    }

    public void setId(byte id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
