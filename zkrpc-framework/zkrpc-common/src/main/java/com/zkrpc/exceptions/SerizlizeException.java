package com.zkrpc.exceptions;

public class SerizlizeException extends RuntimeException {
    public SerizlizeException() {
    }
    public SerizlizeException(Throwable cause) {
        super(cause);
    }
    public SerizlizeException(String message) {
        super(message);
    }
}
