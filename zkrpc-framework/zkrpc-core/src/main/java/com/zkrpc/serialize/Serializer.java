package com.zkrpc.serialize;

public interface Serializer {
    /**
     * 抽象的用来做序列化的方法
     * @param object 带序列化的对象实例
     * @return 字节数组
     */
    byte[] serialize(Object object);

    /**
     * 反序列化的方法
     * @param bytes 待反序列化的数组
     * @param clazz 目标值的class对象
     * @return 目标实例
     * @param <T> 目标类泛型
     */
    /**这是一个使用泛型的接口方法
    <T>: 这是一个泛型声明，表示该方法是一个泛型方法，T 是一个占位符，代表某种类型。这个类型会在调用方法时由具体的类型参数替换。
    T deserialize(...): 方法的返回值是类型 T，即调用者指定的类型。通过泛型，方法可以返回一个与输入类型匹配的对象，而无需强制类型转换。
    参数 Class<T> clazz: 这是一个 Class 对象，表示目标类型的类信息。它用于在反序列化时帮助确定目标对象的类型。
    作用: 该方法的作用是将字节数组 bytes 反序列化为指定类型 T 的对象。调用者需要提供目标类型的 Class 对象（clazz），以便方法知道如何将字节数组转换为目标类型。
     */
    <T> T deserialize(byte[] bytes,Class<T> clazz);

}
