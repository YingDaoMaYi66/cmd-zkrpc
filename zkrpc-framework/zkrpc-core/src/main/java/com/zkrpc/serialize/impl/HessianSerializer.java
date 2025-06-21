package com.zkrpc.serialize.impl;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.zkrpc.exceptions.SerizlizeException;
import com.zkrpc.serialize.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Slf4j
public class HessianSerializer implements Serializer {
    @Override
    public byte[] serialize(Object object) {
        if(object == null){
            return null;
        }
        try(
            ByteArrayOutputStream baos = new ByteArrayOutputStream()
            )
        {
            Hessian2Output hessian2Output = new Hessian2Output(baos);
            hessian2Output.writeObject(object);
            hessian2Output.flush();
            byte[] byteArray = baos.toByteArray();
            if (log.isDebugEnabled()) {
                log.debug("对象【{}】已经完成了序列化操作，序列化后的字节数位【{}】",object,byteArray.length);
            }
            return byteArray;
        }catch (IOException e){
            log.error("对象【{}】使用hessian序列化时发生异常.",object);
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
        ){
            Hessian2Input hessian2Input = new Hessian2Input(bais);
            T t =(T) hessian2Input.readObject();
            if (log.isDebugEnabled()) {
                log.debug("对象【{}】已经完成了反序列化操作",clazz);
            }
            return t;
        }catch (IOException  e){
            log.error("使用hessian进行反序列化对象【{}】时发生异常.",clazz);
            throw new SerizlizeException(e);
        }
    }
}
