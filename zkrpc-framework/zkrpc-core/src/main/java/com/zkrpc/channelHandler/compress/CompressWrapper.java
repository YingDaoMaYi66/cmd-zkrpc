package com.zkrpc.channelHandler.compress;

import com.zkrpc.serialize.Serializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class CompressWrapper {
    private byte code;
    private String type;
    private Compressor compressor;
}
