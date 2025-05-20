package com.zkrpc.exceptions;

public class NetworkException extends RuntimeException {
    public NetworkException() {
    }
    public NetworkException(Throwable cause) {
        super(cause);
    }

    public NetworkException(String message) {
        super(message);
    }
}
