package com.zkrpc.serialize;
import com.zkrpc.config.ObjectWrapper;
import com.zkrpc.serialize.impl.HessianSerializer;
import com.zkrpc.serialize.impl.JdkSerializer;
import com.zkrpc.serialize.impl.JsonSerializer;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
public class SerializerFactory {

    private final static ConcurrentHashMap<String, ObjectWrapper<Serializer>> SERIALIZER_CACHE = new ConcurrentHashMap<>(8);
    private final static ConcurrentHashMap<Byte,ObjectWrapper<Serializer>> SERIALIZER_CACHE_CODE = new ConcurrentHashMap<>(8);


    static{
        ObjectWrapper<Serializer> jdk = new ObjectWrapper((byte)1, "jdk", new JdkSerializer());
        ObjectWrapper<Serializer> json = new ObjectWrapper((byte) 2, "json", new JsonSerializer());
        ObjectWrapper<Serializer> hessian = new ObjectWrapper((byte)3, "Hessian", new HessianSerializer());
        SERIALIZER_CACHE.put("jdk",jdk);
        SERIALIZER_CACHE.put("json",json);
        SERIALIZER_CACHE.put("hessian",hessian);

        SERIALIZER_CACHE_CODE.put((byte)1,jdk);
        SERIALIZER_CACHE_CODE.put((byte)2,json);
        SERIALIZER_CACHE_CODE.put((byte)3,hessian);
    }

    /**
     * 使用工厂方法获取一个SerializerWrapper
     * @param serializeType 序列化的类型
     * @return 一个包装类
     */
    public static ObjectWrapper<Serializer> getSerialzer(String serializeType) {
        ObjectWrapper<Serializer> serializerWrapper = SERIALIZER_CACHE.get(serializeType);
        if (serializerWrapper == null) {
            log.error("未找到您配置【{}】的压缩策略.默认选用jdk的序列化方式",serializeType);
            return SERIALIZER_CACHE.get("jdk");
        }
        return SERIALIZER_CACHE.get(serializeType);
    }

    public static ObjectWrapper<Serializer> getSerialzer(Byte serializeCode) {
        ObjectWrapper<Serializer> serializerWrapper = SERIALIZER_CACHE_CODE.get(serializeCode);
        if (serializerWrapper == null) {
                log.error("未找到您配置【{}】的压缩策略.默认选用jdk的序列化方式",serializeCode);
                return SERIALIZER_CACHE.get("jdk");
        }
        ObjectWrapper<Serializer> serializerObjectWrapper = SERIALIZER_CACHE_CODE.get(serializeCode);
        return SERIALIZER_CACHE_CODE.get(serializeCode);
        
    }

    /**
     * 新增一个序列化器
     * @param serializerObjectWrapper 序列化器的包装类
     */
    public static void addSerializer(ObjectWrapper<Serializer> serializerObjectWrapper) {
        SERIALIZER_CACHE.put(serializerObjectWrapper.getName(), serializerObjectWrapper);
        SERIALIZER_CACHE_CODE.put(serializerObjectWrapper.getCode(),serializerObjectWrapper);
    }

}
