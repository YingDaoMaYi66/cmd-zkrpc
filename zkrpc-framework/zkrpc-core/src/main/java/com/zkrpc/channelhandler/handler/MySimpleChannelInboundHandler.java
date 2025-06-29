package com.zkrpc.channelhandler.handler;

import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.enumeration.RespCode;
import com.zkrpc.exceptions.ResponseException;
import com.zkrpc.loadbalancer.LoadBalancer;
import com.zkrpc.protection.CircuitBreaker;
import com.zkrpc.transport.message.ZkrpcRequest;
import com.zkrpc.transport.message.ZkrpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/*
 * 这个类是用来进行异步操作的
 * 当服务提供方返回结果时，Netty会将结果传递给这个类，这个类会从全局的挂起请求中找到对应的CompletableFuture，并将结果设置到这个CompletableFuture中，
 */
@Slf4j
public class MySimpleChannelInboundHandler extends SimpleChannelInboundHandler<ZkrpcResponse> {
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ZkrpcResponse zkrpcResponse) throws Exception {
        //从全局的挂起的请求中寻找与之匹配的待处理的cf
        CompletableFuture<Object> completableFuture = ZkrpcBootstrap.PENDING_REQUEST.get(zkrpcResponse.getRequestId());

        SocketAddress socketAddress = channelHandlerContext.channel().remoteAddress();
        Map<SocketAddress, CircuitBreaker> everyIpCircuitBreaker = ZkrpcBootstrap
                .getInstance().getConfiguration().getEveryIpCircuitBreaker();
        CircuitBreaker circuitBreaker = everyIpCircuitBreaker.get(socketAddress);
        byte code = zkrpcResponse.getCode();

        if (code == RespCode.FAIL.getCode()){

            circuitBreaker.recordErrorRequest();
            completableFuture.complete(null);
            log.error("当前id为[{}]的请求，返回错误的结果，响应码[{}]", zkrpcResponse.getRequestId(), zkrpcResponse.getCode());
            throw new ResponseException(code,RespCode.FAIL.getDesc());

        }else if (code == RespCode.RATE_LIMIT.getCode()){

            circuitBreaker.recordErrorRequest();
            completableFuture.complete(null);
            log.error("当前id为[{}]的请求，被限流，响应码[{}]", zkrpcResponse.getRequestId(), zkrpcResponse.getCode());
            throw new ResponseException(code,RespCode.RATE_LIMIT.getDesc());


        }else if (code == RespCode.RESOURCE_NOT_FOUND.getCode()){

            circuitBreaker.recordErrorRequest();
            completableFuture.complete(null);
            log.error("当前id为[{}]的请求，未找到目标资源，响应码[{}]", zkrpcResponse.getRequestId(), zkrpcResponse.getCode());
            throw new ResponseException(code,RespCode.RESOURCE_NOT_FOUND.getDesc());
        } else if (code == RespCode.SUCCESS.getCode()) {
            //服务提供方给予的结果
            Object returnValue = zkrpcResponse.getBody();
            completableFuture.complete(returnValue);

            if(log.isDebugEnabled()){
                log.debug("已经寻找到编号为【{}】的completablefuture，处理响应结果", zkrpcResponse.getRequestId());
            }
        }else if (code == RespCode.SUCCESS_HEART_BEAT.getCode()){
            completableFuture.complete(null);
            if(log.isDebugEnabled()){
                log.debug("已经寻找到编号为【{}】的completablefuture，处理处理响应结果", zkrpcResponse.getRequestId());
            }
        }
        else if (code == RespCode.BeCOLSING.getCode()){
            completableFuture.complete(null);
            if(log.isDebugEnabled()){
                log.debug("当前id为[{}]的请求，访问被拒绝，目标服务器正处在关闭中响应码[{}]",
                        zkrpcResponse.getRequestId(), zkrpcResponse.getCode());
            }
            // 修正负载均衡器
            // 先将负载均衡从健康列表中移除
            ZkrpcBootstrap.CHANNEL_CACHE.remove(socketAddress);
            // 找到负载均衡器进行reloadbalance
            LoadBalancer loadBalancer = ZkrpcBootstrap.getInstance()
                    .getConfiguration().getLoadBalancer();
            //重新进行负载均衡
            ZkrpcRequest zkrpcRequest = ZkrpcBootstrap.REQUEST_THREAD_LOACL.get();

            loadBalancer.reLoadBalance(zkrpcRequest.getRequestPayload().getInterfaceName()
                    ,ZkrpcBootstrap.CHANNEL_CACHE.keySet().stream().toList());

            throw new ResponseException(code,RespCode.BeCOLSING.getDesc());
        }

    }
}
