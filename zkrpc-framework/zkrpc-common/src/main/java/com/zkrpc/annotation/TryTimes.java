package com.zkrpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解
 */
@Target(ElementType.METHOD)
//运行时可用指的是注解在程序运行期间依然存在 jvm保留注解信息，这样你可以通过反射机制，在运行时获取和使用这些注解
@Retention(RetentionPolicy.RUNTIME)
public @interface  TryTimes {
    int tryTimes() default 3; // 默认重试次数为3次
    int intervalTime() default 2000; // 默认重试间隔时间为2000毫秒

}
