package com.zkrpc.transport.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 它用来描述，请求调用方所请求的接口方法的描述
 * helloZkrpc.sayHi("你好")
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestPayload implements Serializable {

    //1、接口的名字--com.zkrpc.HelloZkrpc
    private String interfaceName;

    //2、调用方法的名字--sayHi
    private String methodName;

    //3、参数列表，参数分为参数类型和举例的参数
    //参数类型去确定重载方法，具体参数用来执行方法调用
    private Class<?>[] parameterType;
    private Object[] parametersValue;

    //4、返回值的封装
    private Class<?> returnType;

}
