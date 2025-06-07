package com.zkrpc.exceptions;

public class CompressException extends RuntimeException {
    public CompressException() {
    }
    public CompressException(Throwable cause) {
        super(cause);
    }
    public CompressException(String message) {
        super(message);
    }

}
