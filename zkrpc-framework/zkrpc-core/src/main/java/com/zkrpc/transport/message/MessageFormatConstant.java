package com.zkrpc.transport.message;

public class MessageFormatConstant {
    public final static byte[] MAGIC = "zkrpc".getBytes();
    public final static byte VERSION = 1;
    public final static short HEADER_LENGTH = (byte)(MAGIC.length+1+2+4+1+1+1+8);

}
