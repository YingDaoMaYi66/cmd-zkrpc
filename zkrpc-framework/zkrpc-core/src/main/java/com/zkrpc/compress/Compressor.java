package com.zkrpc.compress;

/**
 * 压缩
 */
public interface Compressor {
    /**
     * 对字节数组进行压缩
     * @param bytes 压缩后的字节数组
     * @return 压缩后的字节数据
     */
    byte[] compress(byte[] bytes);

    /**
     * 对字节数据进行解压缩
     * @param bytes 待压缩的字节数组
     * @return 压缩后的字节数据
     */
    byte[] decompress(byte[] bytes);
}
