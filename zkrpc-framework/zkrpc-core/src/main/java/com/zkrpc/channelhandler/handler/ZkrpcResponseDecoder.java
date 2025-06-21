package com.zkrpc.channelhandler.handler;
import com.zkrpc.compress.Compressor;
import com.zkrpc.compress.CompressorFactory;
import com.zkrpc.serialize.Serializer;
import com.zkrpc.serialize.SerializerFactory;
import com.zkrpc.transport.message.MessageFormatConstant;
import com.zkrpc.transport.message.ZkrpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;
/*
 * consumer的端的解码器，用来解码provider端发送过来的响应
 */
/**
 *
 *
 * <pre>
 *  * 0    1    2    3    4    5    6    7    8    9    10   11   12   13   14  15  16   17   18   19   20   21   22
 *  * +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
 *  * |  magic            |ver| head  len|    full_length    |code| ser|comp|            RequestId                  |
 *  * +--------------------------------------------------------------------------------------------------------------
 *  * |                                                                                                             |
 *  * |                                    BODY                                                                     |
 *  * +-------------------------------------------------------------------------------------------------------------+
 *  * </pre>
 * 基于长度字段的帧解码器
 */
@Slf4j
public class ZkrpcResponseDecoder extends LengthFieldBasedFrameDecoder {
    public ZkrpcResponseDecoder() {
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
        byte responseCode = byteBuf.readByte();
        //6、序列化类型
        byte serializeType = byteBuf.readByte();
        //7、压缩类型
        byte compressType = byteBuf.readByte();
        //8、请求id
        long requestId = byteBuf.readLong();
        //9、使用时间戳
        long timeStamp = byteBuf.readLong();
        //我们需要封装
        ZkrpcResponse zkrpcResponse = new ZkrpcResponse();
        zkrpcResponse.setCode(responseCode);
        zkrpcResponse.setCompressType(compressType);
        zkrpcResponse.setSerializeType(serializeType);
        zkrpcResponse.setRequestId(requestId);
        zkrpcResponse.setTimeStamp(timeStamp);
        // todo 心跳请求没有负载，此处可以判断并直接返回
//        if (requestType == RequestType.HEART_BEAT.getId()){
//            return zkrpcRequest;
//        }

        int bodyLength = fullLength - headerLength;
        byte[] payload = new byte[bodyLength];
        byteBuf.readBytes(payload);
        //有了字节数组后就可以解压缩，反序列化
        if (payload.length > 0) {

            //1、 解压缩
            Compressor compressor = CompressorFactory.getCompressor(compressType).getImpl();
            payload = compressor.decompress(payload);
            //2、 反序列化
            Serializer serializer = SerializerFactory
                .getSerialzer(zkrpcResponse.getSerializeType()).getImpl();
            Object body = serializer.deserialize(payload, Object.class);
            zkrpcResponse.setBody(body);
        }

        if(log.isDebugEnabled()){
            log.debug("响应【{}】已经在调用端端完成解码工作", zkrpcResponse.getRequestId());
        }
        return zkrpcResponse;
    }
}
