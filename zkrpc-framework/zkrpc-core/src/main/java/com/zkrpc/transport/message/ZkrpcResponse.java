package com.zkrpc.transport.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务提供方恢复的响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZkrpcResponse {
    //请求的id
    private long requestId;
    //请求的类型，压缩的类型，序列化的方式
    private byte compressType;
    private byte serializeType;

    private long timeStamp;
    //响应的状态码
    //1 表示成功，2表示异常
    private byte code;
    //具体的消息体
    private Object body;

}
