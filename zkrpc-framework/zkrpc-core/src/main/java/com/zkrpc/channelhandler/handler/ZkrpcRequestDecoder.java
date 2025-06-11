package com.zkrpc.channelhandler.handler;

import com.zkrpc.channelhandler.compress.Compressor;
import com.zkrpc.channelhandler.compress.CompressorFactory;
import com.zkrpc.enumeration.RequestType;
import com.zkrpc.serialize.Serializer;
import com.zkrpc.serialize.SerializerFactory;
import com.zkrpc.transport.message.MessageFormatConstant;
import com.zkrpc.transport.message.RequestPayload;
import com.zkrpc.transport.message.ZkrpcRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
/*
 * provider的端的解码器，用来解码客户端发送过来的请求
 */

/**
 *
 *
 * <pre>
 *  * 0    1    2    3    4    5    6    7    8    9    10   11   12   13   14  15  16   17   18   19   20   21   22
 *  * +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
 *  * |  magic            |ver| head  len|    full_length    | qt | ser|comp|            RequestId                  |
 *  * +--------------------------------------------------------------------------------------------------------------
 *  * |                                                                                                             |
 *  * |                                    BODY                                                                     |
 *  * +-------------------------------------------------------------------------------------------------------------+
 *  * </pre>
 * 基于长度字段的帧解码器
 */
@Slf4j
public class ZkrpcRequestDecoder extends LengthFieldBasedFrameDecoder {
    public ZkrpcRequestDecoder() {
        super(
                //找到当前报文的总长度，截取报文，截取出来的报文我们可以去进行解析

                //最大帧的长度，超过这个maxFrameLength会直接抛弃
                MessageFormatConstant.MAX_FRAME_LENGTH,
                //长度字段的偏移量
                MessageFormatConstant.MAGIC.length + MessageFormatConstant.VERSION_LENGTH+MessageFormatConstant.HEADER_FIELD_LENGTH,
                //长度字段的长度
                MessageFormatConstant.FULL_FIELD_LENGTH,
                //负载的适配长度
                -(MessageFormatConstant.MAGIC.length + MessageFormatConstant.VERSION_LENGTH + MessageFormatConstant.HEADER_FIELD_LENGTH+MessageFormatConstant.FULL_FIELD_LENGTH),
                0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        //todo：目的：本机速度太快吗，无法负载均衡 正式完成后要将这个延时去除
        Thread.sleep(new Random().nextInt(50));

        Object decode = super.decode(ctx, in);
        if (decode instanceof ByteBuf byteBuf) {
            return decodeFrame(byteBuf);
        }
        return null;
    }
    private Object decodeFrame(ByteBuf byteBuf) {
        //1、解析魔术值
        byte[] magic = new byte[MessageFormatConstant.MAGIC.length];
        byteBuf.readBytes(magic);
        //检测魔术是否匹配
        for (int i = 0; i < magic.length; i++) {
            if (magic[i] != MessageFormatConstant.MAGIC[i]) {
                //如果不匹配，直接抛出异常
                throw new RuntimeException("获得的请求不合法");
            }
        }
        //2、解析版本号
        byte version = byteBuf.readByte();
        if (version > MessageFormatConstant.VERSION_LENGTH) {
            throw new RuntimeException("获得的请求版本不被支持");
        }
        //3、解析头部长度
        short headerLength = byteBuf.readShort();
        //4、解析总长度
        int fullLength = byteBuf.readInt();
        //5、请求类型
        byte requestType = byteBuf.readByte();
        //6、序列化类型
        byte serializeType = byteBuf.readByte();
        //7、压缩类型
        byte compressType = byteBuf.readByte();
        //8、请求id
        long requestId = byteBuf.readLong();
        //9、时间戳
        long timeStamp = byteBuf.readLong();
        //我们需要封装
        ZkrpcRequest zkrpcRequest = new ZkrpcRequest();
        zkrpcRequest.setRequestType(requestType);
        zkrpcRequest.setCompressType(compressType);
        zkrpcRequest.setSerializeType(serializeType);
        zkrpcRequest.setRequestId(requestId);
        zkrpcRequest.setTimeStamp(timeStamp);
        //心跳请求没有负载，此处可以判断并直接返回
        if (requestType == RequestType.HEART_BEAT.getId()){
            return zkrpcRequest;
        }

        int payloadLength = fullLength - headerLength;
        byte[] payload = new byte[payloadLength];

        int readableBytes = byteBuf.readableBytes();
        byteBuf.readBytes(payload);
        log.debug("请求的负载长度为【{}】，实际读取的字节数为【{}】",payloadLength,readableBytes);
        //有了字节数组后就可以解压缩，反序列化

        //解压缩
        if (payload !=null && payload.length != 0) {
            Compressor compressor = CompressorFactory.getCompressor(compressType).getCompressor();
            payload = compressor.decompress(payload);


            //反序列化
            // 1-->反序列化器
            Serializer serialzer = SerializerFactory.getSerialzer(serializeType).getSerializer();
            RequestPayload requestPayload = serialzer.deserialize(payload, RequestPayload.class);
            zkrpcRequest.setRequestPayload(requestPayload);
        }
        if(log.isDebugEnabled()){
            log.debug("请求【{}】已经在服务端完成解码工作",zkrpcRequest.getRequestId());
        }
        return zkrpcRequest;
    }
}
