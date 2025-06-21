package com.zkrpc.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @param <T>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ObjectWrapper<T> {
    private Byte code;
    private String name;
    private T impl;

}
