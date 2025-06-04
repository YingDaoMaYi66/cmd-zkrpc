package com.zkrpc.channelHandler.handler;

import com.zkrpc.transport.message.MessageFormatConstant;
import com.zkrpc.transport.message.RequestPayload;
import com.zkrpc.transport.message.ZkrpcRequest;
import com.zkrpc.transport.message.ZkrpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * 自定义协议编码器
 * <p>
 * <pre>
 * 0    1    2    3    4    5    6    7    8    9    10   11   12   13   14  15  16   17   18   19   20   21   22
 * +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
 * |  magic            |ver| head  len|    full_length    |code|ser |comp|            RequestId                  |
 * +--------------------------------------------------------------------------------------------------------------
 * |                                                                                                             |
 * |                                    BODY                                                                     |
 * +-------------------------------------------------------------------------------------------------------------+
 * </pre>
 * 4B magic(魔数值)  ---->zkrpc.getBytes
 * 1B version(版本)  ---->1
 * 2B header length 首部的长度
 * 4B full length 报文总长度
 * 1B serialize 报文总长度
 * 1B compress
 * 1B requestType
 * 8B requestId
 * *
 * body
 * *
 * 出站时，第一个经过的处理器
 * <p/>
 */
//MessageToByteEncoder<ZkrpcRequest>是一个Netty的编码器，用于将ZkrpcRequest对象编码为字节流,继承于出站Handler
@Slf4j
public class ZkrpcResponseEncoder extends MessageToByteEncoder<ZkrpcResponse> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, ZkrpcResponse zkrpcResponse, ByteBuf byteBuf) throws Exception {

        //4个字节的魔数值
        byteBuf.writeBytes(MessageFormatConstant.MAGIC);

        //1个字节的版本号
        byteBuf.writeByte(MessageFormatConstant.VERSION);

        //2个字节的头部的长度
        byteBuf.writeShort(MessageFormatConstant.HEADER_LENGTH);

        //先把这个总长度位置留出来，后面再填充
        byteBuf.writeInt(byteBuf.writerIndex()+MessageFormatConstant.FULL_FIELD_LENGTH);

        //3个类型
        byteBuf.writeByte(zkrpcResponse.getCode());
        byteBuf.writeByte(zkrpcResponse.getSerializeType());
        byteBuf.writeByte(zkrpcResponse.getCompressType());

        //8字节的请求id
        byteBuf.writeLong(zkrpcResponse.getRequestId());


        //写入请求体(RequestPayload)
        byte[] body = getBodyBytes(zkrpcResponse.getBody());
        if(body != null){
            byteBuf.writeBytes(body);
        }
        int bodyLength = body == null ? 0 : body.length;


        //重新处理报文的总长度
        //先保存当前的写指针的位置
        int writerIndex = byteBuf.writerIndex();
        //将写指针的位置移动到总长度的位置上
        byteBuf.writerIndex(MessageFormatConstant.HEADER_FIELD_LENGTH
                +MessageFormatConstant.MAGIC.length+MessageFormatConstant.VERSION_LENGTH
        );
        byteBuf.writeInt(MessageFormatConstant.HEADER_LENGTH+bodyLength);

        //将写指针归位
        byteBuf.writerIndex(writerIndex);

        if(log.isDebugEnabled()){
            log.debug("响应【{}】已经在服务端完成编码工作", zkrpcResponse.getRequestId());
        }
    }

    private byte[] getBodyBytes(Object body) {
        //todo 针对不同的消息类型需要做不同的处理，比如说心跳的请求 没有payload
        if (body == null) {
            return null;
        }

        //希望可以通过一些设计模式，面向对象的编程，让我们可以配置修改序列化和压缩的方式
        //对象怎么变成一个字节数组 序列化 压缩
        try{
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(baos);
            outputStream.writeObject(body);
            //压缩
            return baos.toByteArray();
        }catch (IOException e) {
            log.error("序列化时出现异常");
            throw new RuntimeException(e);
        }
    }

}
