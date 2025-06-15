package com.zkrpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义包扫描注解
 */
//在
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ZkrpcApi {

}
