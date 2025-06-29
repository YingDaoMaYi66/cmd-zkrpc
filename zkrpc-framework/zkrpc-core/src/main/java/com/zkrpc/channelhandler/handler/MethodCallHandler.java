package com.zkrpc.channelhandler.handler;
import com.zkrpc.ServiceConfig;
import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.core.ShutDownHolder;
import com.zkrpc.enumeration.RequestType;
import com.zkrpc.enumeration.RespCode;
import com.zkrpc.protection.RateLimiter;
import com.zkrpc.protection.TokenBuketRateLimiter;
import com.zkrpc.transport.message.RequestPayload;
import com.zkrpc.transport.message.ZkrpcRequest;
import com.zkrpc.transport.message.ZkrpcResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.util.Map;

/*
 * 这个类是用来处理服务端接收到的请求，并调用相关接口,封装响应结果
 */
@Slf4j
public class MethodCallHandler extends SimpleChannelInboundHandler<ZkrpcRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ZkrpcRequest zkrpcRequest) throws Exception {

        // 1、先封装部分响应
        ZkrpcResponse zkrpcResponse = new ZkrpcResponse();
        zkrpcResponse.setRequestId(zkrpcRequest.getRequestId());
        zkrpcResponse.setCompressType(zkrpcRequest.getCompressType());
        zkrpcResponse.setSerializeType(zkrpcRequest.getSerializeType());

        // 2、获得通道
        Channel channel = channelHandlerContext.channel();

        // 3、查看关闭的挡板是否是打开的，如果挡板已经打开，返回一个错误的响应
        if (ShutDownHolder.BAFFLE.get()){
            zkrpcResponse.setCode(RespCode.BeCOLSING.getCode());
            channel.writeAndFlush(zkrpcResponse);
            return;
        }

        // 4、 计数器加一
        ShutDownHolder.REQUEST_COUNTER.increment();




        //2、完成限流相关的操作
        SocketAddress socketAddress = channel.remoteAddress();
        Map<SocketAddress, RateLimiter> everyIpRateLimiter =
                ZkrpcBootstrap.getInstance().getConfiguration().getEveryIpRateLimiter();
        RateLimiter rateLimiter = everyIpRateLimiter.get(socketAddress);
        if (rateLimiter == null) {
            rateLimiter = new TokenBuketRateLimiter(20,20);
            everyIpRateLimiter.put(socketAddress, rateLimiter);
        }
        boolean allowRequest = rateLimiter.allowRequest();
        // 5、处理请求的逻辑
        //限流
        if (!allowRequest) {
            //需要封装响应并且返回了
            zkrpcResponse.setCode(RespCode.RATE_LIMIT.getCode());
            // 处理心跳
        }else if (zkrpcRequest.getRequestType() == RequestType.HEART_BEAT.getId()){
            //需要封装响应并且返回
            zkrpcResponse.setCode(RespCode.SUCCESS_HEART_BEAT.getCode());
        }else{
            /**---------具体的调用过程------------*/
            //（1）、获取负载内容
            RequestPayload requestPayload = zkrpcRequest.getRequestPayload();

            //（2）、根据负载内容进行方法调用
            try{
                Object result = callTargetMethod(requestPayload);
                if (log.isDebugEnabled()) {
                    log.debug("请求【{}】已经在服务端完成方法调用。", zkrpcRequest.getRequestId());
                }
                //（3）、封装响应  我们是否需要考虑另外一个问题，响应码，响应类型
                zkrpcResponse.setCode((RespCode.SUCCESS).getCode());
                zkrpcResponse.setBody(result);
            }catch (Exception e){
                log.error("编号为[{}]的请求在调用过程中发生异常", zkrpcRequest.getRequestId(), e);
                zkrpcResponse.setCode(RespCode.FAIL.getCode());
            }
        }
        //6、写出响应
        channel.writeAndFlush(zkrpcResponse);

        //7、计数器减一
        ShutDownHolder.REQUEST_COUNTER.decrement();
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
