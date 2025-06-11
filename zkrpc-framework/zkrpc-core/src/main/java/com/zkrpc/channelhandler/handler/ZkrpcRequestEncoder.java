package com.zkrpc.channelhandler.handler;

import com.zkrpc.channelhandler.compress.Compressor;
import com.zkrpc.channelhandler.compress.CompressorFactory;
import com.zkrpc.serialize.Serializer;
import com.zkrpc.serialize.SerializerFactory;
import com.zkrpc.transport.message.MessageFormatConstant;
import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
/*
 * comsumer的自定义协议编码器，将请求编码
 */

/**
 * 自定义协议编码器
 * <p>
 * <pre>
 * 0    1    2    3    4    5    6    7    8    9    10   11   12   13   14  15  16   17   18   19   20   21   22
 * +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
 * |  magic            |ver| head  len|    full_length    | qt|ser|comp  |            RequestId                  |
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
public class ZkrpcRequestEncoder extends MessageToByteEncoder<ZkrpcRequest> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, ZkrpcRequest zkrpcRequest, ByteBuf byteBuf) throws Exception {

        //4个字节的魔数值
        byteBuf.writeBytes(MessageFormatConstant.MAGIC);

        //1个字节的版本号
        byteBuf.writeByte(MessageFormatConstant.VERSION);

        //2个字节的头部的长度
        byteBuf.writeShort(MessageFormatConstant.HEADER_LENGTH);

        //先把这个总长度位置留出来，后面再填充
        byteBuf.writeInt(byteBuf.writerIndex()+MessageFormatConstant.FULL_FIELD_LENGTH);

        //3个类型
        byteBuf.writeByte(zkrpcRequest.getRequestType());
        byteBuf.writeByte(zkrpcRequest.getSerializeType());
        byteBuf.writeByte(zkrpcRequest.getCompressType());

        //8字节的请求id
        byteBuf.writeLong(zkrpcRequest.getRequestId());

        byteBuf.writeLong(zkrpcRequest.getTimeStamp());


        //写入请求体(RequestPayload)
        //1、根据配置的序列化方式进行序列化
        //实现序列化 1、工具类 耦合性很高 如果以后我想替换序列化的方式，很难
        byte[] body = null;
        if (zkrpcRequest.getRequestPayload()!=null) {
            Serializer serializer = SerializerFactory.getSerialzer(zkrpcRequest.getSerializeType()).getSerializer();
            body = serializer.serialize(zkrpcRequest.getRequestPayload());

            Compressor compressor = CompressorFactory.getCompressor(zkrpcRequest.getCompressType()).getCompressor();
            body = compressor.compress(body);
        }
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

        //打印日志
        if(log.isDebugEnabled()){
            log.debug("请求【{}】已经完成报文的编码",zkrpcRequest.getRequestId());
        }
    }


}
