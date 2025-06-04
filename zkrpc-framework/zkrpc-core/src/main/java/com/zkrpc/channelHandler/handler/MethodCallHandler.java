package com.zkrpc.channelHandler.handler;

import com.zkrpc.ServiceConfig;
import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.enumeration.RespCode;
import com.zkrpc.transport.message.RequestPayload;
import com.zkrpc.transport.message.ZkrpcRequest;
import com.zkrpc.transport.message.ZkrpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
@Slf4j
public class MethodCallHandler extends SimpleChannelInboundHandler<ZkrpcRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ZkrpcRequest zkrpcRequest) throws Exception {
        //1、获取负载内容
        RequestPayload requestPayload = zkrpcRequest.getRequestPayload();

        //2、根据负载内容进行方法调用
        Object result = callTargetMethod(requestPayload);
        if (log.isDebugEnabled()) {
            log.debug("请求【{}】已经在服务端完成方法调用。", zkrpcRequest.getRequestId());
        }
        //3、封装响应
        ZkrpcResponse zkrpcResponse = new ZkrpcResponse();
        zkrpcResponse.setCode((RespCode.SUCCESS).getCode());
        zkrpcResponse.setRequestId(zkrpcRequest.getRequestId());
        zkrpcResponse.setCompressType(zkrpcRequest.getCompressType());
        zkrpcResponse.setSerializeType(zkrpcRequest.getSerializeType());
        zkrpcResponse.setBody(result);
        //4、写出响应
        channelHandlerContext.channel().writeAndFlush(zkrpcResponse);
    }

    private Object callTargetMethod(RequestPayload requestPayload) {
        String interfaceName = requestPayload.getInterfaceName();
        String methodName = requestPayload.getMethodName();
        Class<?>[] parameterType = requestPayload.getParameterType();
        Object[] parametersValue = requestPayload.getParametersValue();
        //寻找到匹配的暴露出去的具体的实现
        ServiceConfig<?> serviceConfig = ZkrpcBootstrap.SERVICE_LIST.get(interfaceName);
        Object refImpl = serviceConfig.getRef();
        //通过反射进行调用 1、获取方法对象 2、执行invoke方法
        Object resultValue;
        try{
            //获取它的类型
            Class<?> aClass = refImpl.getClass();
            //获取他的方法
            Method method = aClass.getMethod(methodName, parameterType);
            //获取它的方法调用
            resultValue = method.invoke(refImpl, parametersValue);
        }catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            log.error("调用服务【{}】的方法【{}】时发生了异常", interfaceName, methodName, e);
            throw new RuntimeException(e);
            //这里可以封装一个异常响应
        }

        return resultValue;
    }
}
