package com.zkrpc.compress;
import com.zkrpc.compress.impl.GzipCompressor;
import com.zkrpc.config.ObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CompressorFactory {

    private final static ConcurrentHashMap<String, ObjectWrapper<Compressor>> COMPRESSOR_CACHE = new ConcurrentHashMap<>(8);
    private final static ConcurrentHashMap<Byte,ObjectWrapper<Compressor>> COMPRESSOR_CACHE_CODE = new ConcurrentHashMap<>(8);


    static{
        ObjectWrapper<Compressor> gzip = new ObjectWrapper((byte)1, "gzip", new GzipCompressor());
        COMPRESSOR_CACHE.put("gzip",gzip);
        COMPRESSOR_CACHE_CODE.put((byte)1,gzip);
    }

    /**
     * 使用工厂方法获取一个CompressorWrapper
     * @param compressorType  压缩类型
     * @return 返回一个压缩器包装类
     */
    public static ObjectWrapper<Compressor> getCompressor(String compressorType) {
        //做空值判断
        ObjectWrapper<Compressor> compressorObjectWrapper = COMPRESSOR_CACHE.get(compressorType);
        if (compressorObjectWrapper == null) {
            log.error("未找到您配置的编号为【{}】压缩算法，默认使用gzip算法",compressorType);
            return COMPRESSOR_CACHE.get("gzip");
        }
        return compressorObjectWrapper;
    }

    public static ObjectWrapper<Compressor> getCompressor(byte serializeCode) {
        ObjectWrapper<Compressor> compressorObjectWrapper = COMPRESSOR_CACHE.get(serializeCode);
        if ((compressorObjectWrapper == null)) {
            log.error("未找到您配置的编号为【{}】压缩算法，默认使用gzip算法",serializeCode);
            return COMPRESSOR_CACHE.get("gzip");
        }
        return compressorObjectWrapper;
    }

    /**
     * 添加一个压缩器
     * @param compressorObjectWrapper 压缩器类型的包装
     */
    public static void addCompressor(ObjectWrapper<Compressor> compressorObjectWrapper) {
        COMPRESSOR_CACHE.put(compressorObjectWrapper.getName(),compressorObjectWrapper);
        COMPRESSOR_CACHE_CODE.put(compressorObjectWrapper.getCode(),compressorObjectWrapper);
    }
}
