package com.zkrpc.serialize.impl;

import com.zkrpc.exceptions.SerizlizeException;
import com.zkrpc.serialize.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Slf4j
public class JdkSerializer implements Serializer {
    @Override
    public byte[] serialize(Object object) {
        if(object == null){
            return null;
        }
        try(
            //将流的定义写在这里会自动关闭，不需要在写finally
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(baos)
           ){
            outputStream.writeObject(object);
            byte[] byteArray = baos.toByteArray();
            if (log.isDebugEnabled()) {
                log.debug("对象【{}】已经完成了序列化操作，序列化后的字节数位【{}】",object,byteArray.length);
            }
            return byteArray;
        }catch (IOException e){
            log.error("序列化对象【{}】时发生异常.",object);
            throw new SerizlizeException(e);
        }

    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if(bytes == null || clazz == null){
            return null;
        }
        try(
                //将流的定义写在这里会自动关闭，不需要在写finally
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ObjectInputStream objectInputStream = new ObjectInputStream(bais)
        ){
                if (log.isDebugEnabled()) {
                    log.debug("对象【{}】已经完成了反序列化操作",clazz);
                }
            return (T)objectInputStream.readObject();
        }catch (IOException | ClassNotFoundException e){
            log.error("反序列化对象【{}】时发生异常.",clazz);
            throw new SerizlizeException(e);
        }
    }
}
